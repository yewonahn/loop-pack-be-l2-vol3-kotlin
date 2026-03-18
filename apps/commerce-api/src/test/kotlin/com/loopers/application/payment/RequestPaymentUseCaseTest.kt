package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.PaymentErrorCode
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
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class RequestPaymentUseCaseTest @Autowired constructor(
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

    private fun createOrder(userId: Long = 1L, amount: Long = 50000): Order {
        return orderRepository.save(
            Order.create(userId = userId, totalAmount = Money(amount)),
        )
    }

    @DisplayName("결제 요청")
    @Nested
    inner class Execute {

        @DisplayName("PG 요청이 성공하면 Payment가 저장되고 transactionId가 반영된다.")
        @Test
        fun success() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
                transactionId = "txn_123456",
                orderId = "test-order",
                status = "REQUESTED",
                message = null,
            )

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
                { assertThat(result.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(result.transactionId).isEqualTo("txn_123456") },
                { assertThat(result.amount).isEqualTo(50000) },
            )
        }

        @DisplayName("PG 요청이 실패(타임아웃)하면 Payment가 REQUEST_FAILED 상태가 된다.")
        @Test
        fun failWhenPgTimeout() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws RuntimeException("Read timed out")

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

        @DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        fun failWhenOrderNotFound() {
            // act & assert
            val exception = assertThrows<PaymentException> {
                requestPaymentUseCase.execute(
                    PaymentCommand.Request(
                        orderId = 999L,
                        userId = 1L,
                        cardType = CardType.SAMSUNG,
                        cardNo = "1234-5678-9012-3456",
                    ),
                )
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND)
        }
    }
}
