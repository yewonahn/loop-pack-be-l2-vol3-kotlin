package com.loopers.application.payment.port

data class PgPaymentStatusResponse(
    val transactionId: String,
    val orderId: String,
    val status: String,
    val amount: String?,
    val reason: String?,
)
