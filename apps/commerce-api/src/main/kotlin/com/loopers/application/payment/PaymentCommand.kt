package com.loopers.application.payment

import com.loopers.domain.payment.CardType

class PaymentCommand {
    data class Request(
        val orderId: Long,
        val userId: Long,
        val cardType: CardType,
        val cardNo: String,
    )

    data class Callback(
        val transactionId: String,
        val status: String,
        val reason: String?,
    )
}
