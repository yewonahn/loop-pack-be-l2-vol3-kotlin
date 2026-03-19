package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.application.payment.port.PgPaymentStatusResponse
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.PaymentHistoryRepository
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
class PaymentFailureScenarioTest @Autowired constructor(
    private val requestPaymentUseCase: RequestPaymentUseCase,
    private val handlePaymentCallbackUseCase: HandlePaymentCallbackUseCase,
    private val recoverPaymentUseCase: RecoverPaymentUseCase,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentHistoryRepository: PaymentHistoryRepository,
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

    private fun requestPaymentCommand(orderId: Long) = PaymentCommand.Request(
        orderId = orderId,
        userId = 1L,
        cardType = CardType.SAMSUNG,
        cardNo = "1234-5678-9012-3456",
    )

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

    @DisplayName("결제 재시도 시나리오")
    @Nested
    inner class RetryPayment {

        @DisplayName("FAILED 상태의 결제를 재시도하면 기존 Payment가 REQUESTED로 리셋되고, 이력이 남는다.")
        @Test
        fun retryAfterFailed() {
            // arrange - 1차 시도: 결제 실패
            val order = createOrder()
            val firstPayment = requestPayment(order, "txn_first")
            handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_first", status = "FAILED", reason = "한도 초과"),
            )

            // act - 2차 시도: 재시도
            every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
                transactionId = "txn_second",
                orderId = "test",
                status = "REQUESTED",
                message = null,
            )
            val retryResult = requestPaymentUseCase.execute(requestPaymentCommand(order.id))

            // assert
            assertAll(
                { assertThat(retryResult.id).isEqualTo(firstPayment.id) },
                { assertThat(retryResult.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(retryResult.pgOrderId).isEqualTo(firstPayment.pgOrderId) },
                { assertThat(retryResult.transactionId).isEqualTo("txn_second") },
            )

            // assert - 이력 확인
            val histories = paymentHistoryRepository.findAllByPaymentId(firstPayment.id)
            assertAll(
                { assertThat(histories).hasSize(1) },
                { assertThat(histories[0].status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(histories[0].failReason).isEqualTo("한도 초과") },
            )
        }

        @DisplayName("REQUEST_FAILED 상태의 결제를 재시도하면 이력이 남고 새로 PG 요청한다.")
        @Test
        fun retryAfterRequestFailed() {
            // arrange - 1차 시도: PG 타임아웃
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws RuntimeException("Read timed out")
            val firstResult = requestPaymentUseCase.execute(requestPaymentCommand(order.id))
            assertThat(firstResult.status).isEqualTo(PaymentStatus.REQUEST_FAILED)

            // act - 2차 시도: 재시도 성공
            every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
                transactionId = "txn_retry",
                orderId = "test",
                status = "REQUESTED",
                message = null,
            )
            val retryResult = requestPaymentUseCase.execute(requestPaymentCommand(order.id))

            // assert
            assertAll(
                { assertThat(retryResult.id).isEqualTo(firstResult.id) },
                { assertThat(retryResult.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(retryResult.pgOrderId).isEqualTo(firstResult.pgOrderId) },
            )
        }

        @DisplayName("APPROVED 상태의 주문에 재시도하면 ALREADY_PAID 예외가 발생한다.")
        @Test
        fun cannotRetryApprovedPayment() {
            // arrange
            val order = createOrder()
            requestPayment(order, "txn_approved")
            handlePaymentCallbackUseCase.execute(
                PaymentCommand.Callback(transactionId = "txn_approved", status = "SUCCESS", reason = null),
            )

            // act & assert
            val exception = assertThrows<PaymentException> {
                requestPaymentUseCase.execute(requestPaymentCommand(order.id))
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.ALREADY_PAID)
        }

        @DisplayName("REQUESTED 상태의 주문에 중복 요청하면 ALREADY_IN_PROGRESS 예외가 발생한다.")
        @Test
        fun cannotDuplicateRequestedPayment() {
            // arrange
            val order = createOrder()
            requestPayment(order, "txn_in_progress")

            // act & assert
            val exception = assertThrows<PaymentException> {
                requestPaymentUseCase.execute(requestPaymentCommand(order.id))
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.ALREADY_IN_PROGRESS)
        }
    }

    @DisplayName("PG 장애 후 복구 시나리오")
    @Nested
    inner class FailureThenRecover {

        @DisplayName("transactionId가 없는 결제를 pgOrderId로 PG 조회하여 APPROVED로 복구한다.")
        @Test
        fun recoverByPgOrderIdWhenTransactionIdMissing() {
            // arrange - PG 요청 실패 (타임아웃) → 하지만 PG는 실제 처리함
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws RuntimeException("Read timed out")
            val paymentInfo = requestPaymentUseCase.execute(requestPaymentCommand(order.id))
            assertThat(paymentInfo.status).isEqualTo(PaymentStatus.REQUEST_FAILED)

            // act - pgOrderId로 PG 조회
            every { pgPaymentClient.getPaymentsByOrderId(paymentInfo.pgOrderId, any()) } returns listOf(
                PgPaymentStatusResponse(
                    transactionId = "txn_recovered",
                    orderId = paymentInfo.pgOrderId,
                    status = "SUCCESS",
                    amount = "50000",
                    reason = null,
                ),
            )
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert
            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.APPROVED) },
                { assertThat(result.transactionId).isEqualTo("txn_recovered") },
            )
        }

        @DisplayName("pgOrderId로 PG 조회했지만 결제 내역이 없으면 상태가 유지된다.")
        @Test
        fun recoverByPgOrderIdButNoResult() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } throws RuntimeException("Read timed out")
            val paymentInfo = requestPaymentUseCase.execute(requestPaymentCommand(order.id))

            // act - PG에 내역 없음
            every { pgPaymentClient.getPaymentsByOrderId(paymentInfo.pgOrderId, any()) } returns emptyList()
            val result = recoverPaymentUseCase.execute(paymentInfo.id)

            // assert
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
            val result = requestPaymentUseCase.execute(requestPaymentCommand(order.id))

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
            val result = requestPaymentUseCase.execute(requestPaymentCommand(order.id))

            // assert
            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.REQUEST_FAILED) },
                { assertThat(result.failReason).isNotNull() },
            )
        }
    }
}
