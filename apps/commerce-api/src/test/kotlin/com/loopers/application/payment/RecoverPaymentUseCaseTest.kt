package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.application.payment.port.PgPaymentStatusResponse
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
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

@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class RecoverPaymentUseCaseTest @Autowired constructor(
    private val recoverPaymentUseCase: RecoverPaymentUseCase,
    private val requestPaymentUseCase: RequestPaymentUseCase,
    private val orderRepository: OrderRepository,
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
            transactionId = "txn_recover_test",
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

    @DisplayName("결제 복구")
    @Nested
    inner class Execute {

        @DisplayName("PG에서 성공 확인되면 APPROVED 상태가 된다.")
        @Test
        fun recoverToApproved() {
            // arrange
            val paymentInfo = createPaymentWithTransaction()
            every { pgPaymentClient.getPaymentByTransactionId(any(), any()) } returns PgPaymentStatusResponse(
                transactionId = "txn_recover_test",
                orderId = paymentInfo.pgOrderId,
                status = "SUCCESS",
                amount = "50000",
                reason = null,
            )

            // act
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.APPROVED)
        }

        @DisplayName("PG에서 아직 처리 중이면 REQUESTED 상태를 유지한다.")
        @Test
        fun keepRequestedWhenPending() {
            // arrange
            val paymentInfo = createPaymentWithTransaction()
            every { pgPaymentClient.getPaymentByTransactionId(any(), any()) } returns PgPaymentStatusResponse(
                transactionId = "txn_recover_test",
                orderId = paymentInfo.pgOrderId,
                status = "PENDING",
                amount = "50000",
                reason = null,
            )

            // act
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.REQUESTED)
        }

        @DisplayName("PG 상태 확인 실패 시 현재 상태를 유지한다.")
        @Test
        fun keepStatusWhenPgFails() {
            // arrange
            val paymentInfo = createPaymentWithTransaction()
            every { pgPaymentClient.getPaymentByTransactionId(any(), any()) } throws RuntimeException("PG 연결 실패")

            // act
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert
            assertThat(result.status).isEqualTo(PaymentStatus.REQUESTED)
        }
    }
}
