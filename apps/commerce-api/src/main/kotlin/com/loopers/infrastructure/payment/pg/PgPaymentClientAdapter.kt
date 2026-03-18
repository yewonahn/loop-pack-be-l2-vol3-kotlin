package com.loopers.infrastructure.payment.pg

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentRequest
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.application.payment.port.PgPaymentStatusResponse
import com.loopers.support.error.PaymentException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PgPaymentClientAdapter(
    private val pgFeignClient: PgFeignClient,
) : PgPaymentClient {

    private val log = LoggerFactory.getLogger(javaClass)

    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "requestPaymentFallback")
    override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
        return pgFeignClient.requestPayment(
            userId = request.orderId.hashCode().toLong(),
            request = request,
        )
    }

    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "getPaymentByTransactionIdFallback")
    override fun getPaymentByTransactionId(transactionId: String, userId: Long): PgPaymentStatusResponse {
        return pgFeignClient.getPaymentByTransactionId(transactionId, userId)
    }

    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "getPaymentsByOrderIdFallback")
    override fun getPaymentsByOrderId(pgOrderId: String, userId: Long): List<PgPaymentStatusResponse> {
        return pgFeignClient.getPaymentsByOrderId(pgOrderId, userId)
    }

    private fun requestPaymentFallback(request: PgPaymentRequest, e: CallNotPermittedException): PgPaymentResponse {
        log.warn("CircuitBreaker OPEN — PG 결제 요청 차단 [orderId={}]", request.orderId)
        throw PaymentException.pgSystemError()
    }

    private fun requestPaymentFallback(request: PgPaymentRequest, e: Exception): PgPaymentResponse {
        log.warn("PG 결제 요청 실패 (fallback) [orderId={}]: {}", request.orderId, e.message)
        throw PaymentException.pgRequestFailed(e)
    }

    private fun getPaymentByTransactionIdFallback(
        transactionId: String,
        userId: Long,
        e: CallNotPermittedException,
    ): PgPaymentStatusResponse {
        log.warn("CircuitBreaker OPEN — PG 상태 조회 차단 [transactionId={}]", transactionId)
        throw PaymentException.pgSystemError()
    }

    private fun getPaymentByTransactionIdFallback(
        transactionId: String,
        userId: Long,
        e: Exception,
    ): PgPaymentStatusResponse {
        log.warn("PG 상태 조회 실패 (fallback) [transactionId={}]: {}", transactionId, e.message)
        throw PaymentException.pgRequestFailed(e)
    }

    private fun getPaymentsByOrderIdFallback(
        pgOrderId: String,
        userId: Long,
        e: CallNotPermittedException,
    ): List<PgPaymentStatusResponse> {
        log.warn("CircuitBreaker OPEN — PG 주문별 조회 차단 [pgOrderId={}]", pgOrderId)
        throw PaymentException.pgSystemError()
    }

    private fun getPaymentsByOrderIdFallback(
        pgOrderId: String,
        userId: Long,
        e: Exception,
    ): List<PgPaymentStatusResponse> {
        log.warn("PG 주문별 조회 실패 (fallback) [pgOrderId={}]: {}", pgOrderId, e.message)
        throw PaymentException.pgRequestFailed(e)
    }
}
