package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.support.error.PaymentException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(name = "payments")
class Payment private constructor(
    orderId: Long,
    userId: Long,
    amount: Money,
    pgOrderId: String,
    cardType: CardType,
    cardNo: String,
    status: PaymentStatus,
) : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    val orderId: Long = orderId

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "amount", nullable = false))
    val amount: Money = amount

    @Column(name = "pg_order_id", nullable = false, unique = true, length = 20)
    val pgOrderId: String = pgOrderId

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    val cardType: CardType = cardType

    @Column(name = "card_no", nullable = false, length = 20)
    val cardNo: String = cardNo

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = status
        protected set

    @Column(name = "transaction_id", length = 50)
    var transactionId: String? = null
        protected set

    @Column(name = "fail_reason", length = 200)
    var failReason: String? = null
        protected set

    @Column(name = "paid_at")
    var paidAt: ZonedDateTime? = null
        protected set

    fun updateTransactionId(transactionId: String) {
        validateRequested()
        this.transactionId = transactionId
    }

    fun approve(paidAt: ZonedDateTime = ZonedDateTime.now()) {
        validateRequested()
        this.status = PaymentStatus.APPROVED
        this.paidAt = paidAt
    }

    fun fail(reason: String) {
        validateRequested()
        this.status = PaymentStatus.FAILED
        this.failReason = reason
    }

    fun markRequestFailed(reason: String? = null) {
        validateRequested()
        this.status = PaymentStatus.REQUEST_FAILED
        this.failReason = reason
    }

    private fun validateRequested() {
        if (status != PaymentStatus.REQUESTED) {
            throw PaymentException.invalidStatus()
        }
    }

    companion object {
        fun create(
            orderId: Long,
            userId: Long,
            amount: Money,
            cardType: CardType,
            cardNo: String,
        ): Payment {
            val pgOrderId = generatePgOrderId()
            return Payment(
                orderId = orderId,
                userId = userId,
                amount = amount,
                pgOrderId = pgOrderId,
                cardType = cardType,
                cardNo = cardNo,
                status = PaymentStatus.REQUESTED,
            )
        }

        private fun generatePgOrderId(): String {
            val datePart = LocalDate.now().toString().replace("-", "")
            val uuidPart = UUID.randomUUID().toString().replace("-", "").take(8)
            return "$datePart:$uuidPart"
        }
    }
}
