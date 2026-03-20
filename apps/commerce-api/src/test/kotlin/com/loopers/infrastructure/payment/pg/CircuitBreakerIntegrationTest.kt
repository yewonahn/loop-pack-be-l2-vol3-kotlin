package com.loopers.infrastructure.payment.pg

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentRequest
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.support.error.PaymentErrorCode
import com.loopers.support.error.PaymentException
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.ninjasquad.springmockk.MockkBean
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.net.ConnectException

@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class CircuitBreakerIntegrationTest @Autowired constructor(
    private val pgPaymentClient: PgPaymentClient,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    @MockkBean
    private lateinit var pgFeignClient: PgFeignClient

    private lateinit var requestCb: CircuitBreaker

    @BeforeEach
    fun setUp() {
        requestCb = circuitBreakerRegistry.circuitBreaker("pgPaymentRequest")
        requestCb.reset()
    }

    private fun dummyRequest() = PgPaymentRequest(
        orderId = "test-order-123",
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9012-3456",
        amount = "50000",
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )

    private fun successResponse(txnId: String = "txn_success") = PgPaymentResponse(
        transactionId = txnId,
        orderId = "test-order-123",
        status = "REQUESTED",
        message = null,
    )

    @DisplayName("CircuitBreaker 상태 전이 검증")
    @Nested
    inner class StateTransition {

        @DisplayName("minimumCalls(5) 충족 + 실패율 100% → slidingWindow(10) 채우기 전에 CB가 OPEN된다.")
        @Test
        fun opensBeforeWindowIsFull() {
            every { pgFeignClient.requestPayment(any(), any()) } throws RuntimeException("PG error")

            repeat(5) {
                runCatching { pgPaymentClient.requestPayment(dummyRequest()) }
            }

            assertThat(requestCb.state).isEqualTo(CircuitBreaker.State.OPEN)
        }

        @DisplayName("5건 중 3건 실패(60%) → 실패율 > 50%이므로 CB가 OPEN된다.")
        @Test
        fun opensWhenFailureRateExceedsThreshold() {
            var callCount = 0
            every { pgFeignClient.requestPayment(any(), any()) } answers {
                callCount++
                if (callCount <= 3) throw RuntimeException("PG error")
                else successResponse("txn_$callCount")
            }

            repeat(5) {
                runCatching { pgPaymentClient.requestPayment(dummyRequest()) }
            }

            assertThat(requestCb.state).isEqualTo(CircuitBreaker.State.OPEN)
        }

        @DisplayName("5건 중 2건 실패(40%) → 실패율 < 50%이므로 CB는 CLOSED를 유지한다.")
        @Test
        fun staysClosedWhenFailureRateBelowThreshold() {
            var callCount = 0
            every { pgFeignClient.requestPayment(any(), any()) } answers {
                callCount++
                if (callCount <= 2) throw RuntimeException("PG error")
                else successResponse("txn_$callCount")
            }

            repeat(5) {
                runCatching { pgPaymentClient.requestPayment(dummyRequest()) }
            }

            assertThat(requestCb.state).isEqualTo(CircuitBreaker.State.CLOSED)
        }

        @DisplayName("CB OPEN 상태에서는 PG 호출 없이 즉시 PG_SYSTEM_ERROR로 차단된다.")
        @Test
        fun blocksCallsWhenOpen() {
            requestCb.transitionToOpenState()

            val exception = assertThrows<PaymentException> {
                pgPaymentClient.requestPayment(dummyRequest())
            }
            assertAll(
                { assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.PG_SYSTEM_ERROR) },
                { verify(exactly = 0) { pgFeignClient.requestPayment(any(), any()) } },
            )
        }

        @DisplayName("CB HALF_OPEN에서 허용된 호출(3건)이 모두 성공하면 CLOSED로 전환된다.")
        @Test
        fun closesAfterSuccessfulHalfOpenCalls() {
            requestCb.transitionToOpenState()
            requestCb.transitionToHalfOpenState()
            every { pgFeignClient.requestPayment(any(), any()) } returns successResponse()

            repeat(3) {
                pgPaymentClient.requestPayment(dummyRequest())
            }

            assertThat(requestCb.state).isEqualTo(CircuitBreaker.State.CLOSED)
        }

        @DisplayName("CB HALF_OPEN에서 허용된 호출(3건)이 모두 실패하면 다시 OPEN으로 전환된다.")
        @Test
        fun reopensAfterFailedHalfOpenCalls() {
            requestCb.transitionToOpenState()
            requestCb.transitionToHalfOpenState()
            every { pgFeignClient.requestPayment(any(), any()) } throws RuntimeException("Still failing")

            repeat(3) {
                runCatching { pgPaymentClient.requestPayment(dummyRequest()) }
            }

            assertThat(requestCb.state).isEqualTo(CircuitBreaker.State.OPEN)
        }
    }

    @DisplayName("Retry + CircuitBreaker 연동 검증")
    @Nested
    inner class RetryCircuitBreakerIntegration {

        @DisplayName("Retry 3회 소진 후 CB에는 실패 1건만 기록된다. (aspect order 검증)")
        @Test
        fun retryExhaustedCountsAsOneFailure() {
            every { pgFeignClient.requestPayment(any(), any()) } throws ConnectException("Connection refused")

            runCatching { pgPaymentClient.requestPayment(dummyRequest()) }

            assertAll(
                { verify(exactly = 3) { pgFeignClient.requestPayment(any(), any()) } },
                { assertThat(requestCb.metrics.numberOfFailedCalls).isEqualTo(1) },
                { assertThat(requestCb.state).isEqualTo(CircuitBreaker.State.CLOSED) },
            )
        }
    }
}
