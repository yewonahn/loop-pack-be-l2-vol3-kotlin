package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "orders")
class Order private constructor(
    userId: Long,
    totalAmount: Money,
    status: OrderStatus,
    orderedAt: ZonedDateTime,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = totalAmount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = status
        protected set

    @Column(name = "ordered_at", nullable = false)
    val orderedAt: ZonedDateTime = orderedAt

    companion object {
        fun create(
            userId: Long,
            totalAmount: Money,
        ): Order {
            return Order(
                userId = userId,
                totalAmount = totalAmount,
                status = OrderStatus.ORDERED,
                orderedAt = ZonedDateTime.now(),
            )
        }
    }
}
