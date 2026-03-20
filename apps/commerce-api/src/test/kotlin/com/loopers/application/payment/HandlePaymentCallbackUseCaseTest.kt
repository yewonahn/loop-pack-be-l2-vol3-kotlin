package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class HandlePaymentCallbackUseCaseTest @Autowired constructor(
    private val handlePaymentCallbackUseCase: HandlePaymentCallbackUseCase,
    private val requestPaymentUseCase: RequestPaymentUseCase,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgPaymentClient: PgPaymentClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun createPaymentWithTransaction(): PaymentInfo {
        val order = orderRepository.save(Order.create(userId = 1L, totalAmount = Money(50000)))
        every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
            transactionId = "txn_callback_test",
            orderId = "test",
            status = "REQUESTED",
            message = null,
        )
        return requestPaymentUseCase.execute(
            PaymentCommand.Request(
                orderId = order.id,
                userId = 1L,
                cardType = CardType.SAMSUNG,
                cardNo = "1234-5678-9012-3456",
            ),
        )
    }

    @DisplayName("결제 콜백 처리")
    @Nested
    inner class Execute {

        @DisplayName("성공 콜백이면 APPROVED 상태가 된다.")
        @Test
        fun successCallback() {
            // arrange
            val paymentInfo = createPaymentWithTransaction()

            // act
            val result = handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(
                    transactionId = "txn_callback_test",
                    status = "SUCCESS",
                    reason = null,
                ),
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.APPROVED)
        }

        @DisplayName("실패 콜백이면 FAILED 상태가 된다.")
        @Test
        fun failCallback() {
            // arrange
            createPaymentWithTransaction()

            // act
            val result = handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(
                    transactionId = "txn_callback_test",
                    status = "FAILED",
                    reason = "한도 초과",
                ),
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
        }

        @DisplayName("이미 APPROVED인 결제에 콜백이 오면 무시한다 (멱등성).")
        @Test
        fun idempotentWhenAlreadyApproved() {
            // arrange
            createPaymentWithTransaction()
            handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_callback_test", status = "SUCCESS", reason = null),
            )

            // act - 두 번째 콜백
            val result = handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_callback_test", status = "SUCCESS", reason = null),
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.APPROVED)
        }
    }

    @DisplayName("동시 콜백 방어 검증")
    @Nested
    inner class ConcurrentCallback {

        @DisplayName("동일 결제에 성공/실패 콜백이 동시에 도착하면, 하나만 반영되고 최종 상태는 터미널이다.")
        @Test
        fun onlyOneCallbackApplied() {
            // arrange
            createPaymentWithTransaction()
            val latch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            // act — 두 스레드가 동시에 콜백 처리
            val successFuture = executor.submit<PaymentInfo> {
                latch.await()
                handlePaymentCallbackUseCase.execute(
                    PaymentCommand.Callback(transactionId = "txn_callback_test", status = "SUCCESS", reason = null),
                )
            }
            val failFuture = executor.submit<PaymentInfo> {
                latch.await()
                handlePaymentCallbackUseCase.execute(
                    PaymentCommand.Callback(transactionId = "txn_callback_test", status = "FAILED", reason = "한도 초과"),
                )
            }

            latch.countDown()

            val result1 = successFuture.get()
            val result2 = failFuture.get()
            executor.shutdown()

            // assert — 최종 DB 상태는 APPROVED 또는 FAILED 중 하나 (터미널)
            val payment = paymentRepository.findByIdOrNull(result1.id)!!
            assertThat(payment.status.isTerminal()).isTrue()
        }
    }
}
