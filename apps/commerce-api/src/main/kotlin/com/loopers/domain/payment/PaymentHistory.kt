package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "payment_histories")
class PaymentHistory private constructor(
    paymentId: Long,
    status: PaymentStatus,
    transactionId: String?,
    failReason: String?,
) : BaseEntity() {

    @Column(name = "payment_id", nullable = false)
    val paymentId: Long = paymentId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PaymentStatus = status

    @Column(name = "transaction_id", length = 50)
    val transactionId: String? = transactionId

    @Column(name = "fail_reason", length = 200)
    val failReason: String? = failReason

    companion object {
        fun from(payment: Payment): PaymentHistory {
            return PaymentHistory(
                paymentId = payment.id,
                status = payment.status,
                transactionId = payment.transactionId,
                failReason = payment.failReason,
            )
        }
    }
}
