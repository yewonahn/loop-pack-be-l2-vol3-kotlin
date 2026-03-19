package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: Long): Payment?
    fun findByTransactionId(transactionId: String): Payment?
    fun findByPgOrderId(pgOrderId: String): Payment?
    fun findAllByStatus(status: PaymentStatus): List<Payment>
}
