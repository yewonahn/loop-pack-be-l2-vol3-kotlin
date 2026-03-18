package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

data class PaymentResponse(
    val id: Long,
    val orderId: Long,
    val amount: Long,
    val pgOrderId: String,
    val cardType: CardType,
    val status: PaymentStatus,
    val transactionId: String?,
    val failReason: String?,
    val paidAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun from(info: PaymentInfo): PaymentResponse {
            return PaymentResponse(
                id = info.id,
                orderId = info.orderId,
                amount = info.amount,
                pgOrderId = info.pgOrderId,
                cardType = info.cardType,
                status = info.status,
                transactionId = info.transactionId,
                failReason = info.failReason,
                paidAt = info.paidAt,
                createdAt = info.createdAt,
            )
        }
    }
}
