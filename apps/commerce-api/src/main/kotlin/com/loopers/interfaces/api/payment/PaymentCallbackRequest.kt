package com.loopers.interfaces.api.payment

data class PaymentCallbackRequest(
    val transactionId: String,
    val status: String,
    val reason: String?,
)
