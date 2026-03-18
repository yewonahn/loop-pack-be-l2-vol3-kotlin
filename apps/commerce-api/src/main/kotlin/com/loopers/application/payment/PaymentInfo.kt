package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import java.time.ZonedDateTime

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val pgOrderId: String,
    val cardType: CardType,
    val cardNo: String,
    val status: PaymentStatus,
    val transactionId: String?,
    val failReason: String?,
    val paidAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun from(payment: Payment): PaymentInfo {
            return PaymentInfo(
                id = payment.id,
                orderId = payment.orderId,
                userId = payment.userId,
                amount = payment.amount.amount,
                pgOrderId = payment.pgOrderId,
                cardType = payment.cardType,
                cardNo = payment.cardNo,
                status = payment.status,
                transactionId = payment.transactionId,
                failReason = payment.failReason,
                paidAt = payment.paidAt,
                createdAt = payment.createdAt,
            )
        }
    }
}
