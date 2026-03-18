package com.loopers.infrastructure.payment.pg

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentRequest
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.application.payment.port.PgPaymentStatusResponse
import org.springframework.stereotype.Component

@Component
class PgPaymentClientAdapter(
    private val pgFeignClient: PgFeignClient,
) : PgPaymentClient {

    override fun requestPayment(request: PgPaymentRequest): PgPaymentResponse {
        return pgFeignClient.requestPayment(
            userId = request.orderId.hashCode().toLong(),
            request = request,
        )
    }

    override fun getPaymentByTransactionId(transactionId: String, userId: Long): PgPaymentStatusResponse {
        return pgFeignClient.getPaymentByTransactionId(transactionId, userId)
    }

    override fun getPaymentsByOrderId(pgOrderId: String, userId: Long): List<PgPaymentStatusResponse> {
        return pgFeignClient.getPaymentsByOrderId(pgOrderId, userId)
    }
}
