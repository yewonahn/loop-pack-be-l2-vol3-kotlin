package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.application.payment.port.PgPaymentStatusResponse
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.PaymentException
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class PaymentFailureScenarioTest @Autowired constructor(
    private val requestPaymentUseCase: RequestPaymentUseCase,
    private val handlePaymentCallbackUseCase: HandlePaymentCallbackUseCase,
    private val recoverPaymentUseCase: RecoverPaymentUseCase,
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

    private fun createOrder(userId: Long = 1L, amount: Long = 50000): Order {
        return orderRepository.save(Order.create(userId = userId, totalAmount = Money(amount)))
    }

    private fun requestPayment(order: Order, transactionId: String = "txn_test"): PaymentInfo {
        every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
            transactionId = transactionId,
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

    @DisplayName("콜백 중복 수신 시나리오")
    @Nested
    inner class DuplicateCallback {

        @DisplayName("성공 콜백 후 실패 콜백이 오면, 첫 번째 결과(APPROVED)만 반영된다.")
        @Test
        fun successThenFailCallback() {
            // arrange
            val order = createOrder()
            requestPayment(order, "txn_dup_test")

            // act - 첫 번째: 성공 콜백
            handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_dup_test", status = "SUCCESS", reason = null),
            )
            // act - 두 번째: 실패 콜백 (PG 중복 전송 시뮬레이션)
            val result = handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_dup_test", status = "FAILED", reason = "한도 초과"),
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.APPROVED)
        }

        @DisplayName("실패 콜백 후 성공 콜백이 오면, 첫 번째 결과(FAILED)만 반영된다.")
        @Test
        fun failThenSuccessCallback() {
            // arrange
            val order = createOrder()
            requestPayment(order, "txn_dup_test2")

            // act
            handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_dup_test2", status = "FAILED", reason = "잘못된 카드"),
            )
            val result = handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_dup_test2", status = "SUCCESS", reason = null),
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.FAILED)
        }
    }

    @DisplayName("PG 장애 후 복구 시나리오")
    @Nested
    inner class FailureThenRecover {

        @DisplayName("PG 타임아웃으로 REQUEST_FAILED된 결제는 transactionId가 없어 recover 불가, 상태가 유지된다.")
        @Test
        fun cannotRecoverWithoutTransactionId() {
            // arrange - PG 요청 실패 (타임아웃)
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws RuntimeException("Read timed out")
            val paymentInfo = requestPaymentUseCase.execute(
                PaymentCommand.Request(
                    orderId = order.id,
                    userId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                ),
            )
            assertThat(paymentInfo.status).isEqualTo(PaymentStatus.REQUEST_FAILED)

            // act - recover 시도
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert - transactionId가 없으므로 PG 조회 불가, 상태 유지
            assertThat(result.status).isEqualTo(PaymentStatus.REQUEST_FAILED)
        }

        @DisplayName("PG 요청 성공 후 콜백 미수신 상태에서 recover로 APPROVED 상태로 복구한다.")
        @Test
        fun recoverWhenCallbackMissed() {
            // arrange - PG 요청 성공했지만 콜백 미수신 (REQUESTED + transactionId 있음)
            val order = createOrder()
            val paymentInfo = requestPayment(order, "txn_no_callback")
            assertThat(paymentInfo.status).isEqualTo(PaymentStatus.REQUESTED)

            // act - recover: PG 상태 확인 API로 결과 조회
            every { pgPaymentClient.getPaymentByTransactionId("txn_no_callback", any()) } returns
                PgPaymentStatusResponse(
                    transactionId = "txn_no_callback",
                    orderId = paymentInfo.pgOrderId,
                    status = "SUCCESS",
                    amount = "50000",
                    reason = null,
                )
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.APPROVED)
        }
    }

    @DisplayName("CB Open / PG 실패 시나리오")
    @Nested
    inner class PgFailure {

        @DisplayName("PG 시스템 에러(CB Open 등)로 결제 실패 시 REQUEST_FAILED 상태가 된다.")
        @Test
        fun requestFailedOnPgSystemError() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws PaymentException.pgSystemError()

            // act
            val result = requestPaymentUseCase.execute(
                PaymentCommand.Request(
                    orderId = order.id,
                    userId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                ),
            )

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.REQUEST_FAILED)
        }

        @DisplayName("PG 요청 실패(일반 예외) 시 REQUEST_FAILED 상태와 실패 사유가 기록된다.")
        @Test
        fun requestFailedWithReason() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws RuntimeException("Connection refused")

            // act
            val result = requestPaymentUseCase.execute(
                PaymentCommand.Request(
                    orderId = order.id,
                    userId = 1L,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                ),
            )

            // assert
            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.REQUEST_FAILED) },
                { assertThat(result.failReason).isNotNull() },
            )
        }
    }
}
