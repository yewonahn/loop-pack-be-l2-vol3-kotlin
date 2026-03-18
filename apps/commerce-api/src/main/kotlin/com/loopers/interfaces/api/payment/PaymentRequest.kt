package com.loopers.interfaces.api.payment

import com.loopers.domain.payment.CardType

data class PaymentRequest(
    val orderId: Long,
    val cardType: CardType,
    val cardNo: String,
)
