package com.loopers.application.payment.port

interface PgPaymentClient {
    fun requestPayment(request: PgPaymentRequest): PgPaymentResponse
    fun getPaymentByTransactionId(transactionId: String, userId: Long): PgPaymentStatusResponse
    fun getPaymentsByOrderId(pgOrderId: String, userId: Long): List<PgPaymentStatusResponse>
}
