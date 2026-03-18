package com.loopers.application.payment.port

data class PgPaymentResponse(
    val transactionId: String,
    val orderId: String,
    val status: String,
    val message: String?,
)
