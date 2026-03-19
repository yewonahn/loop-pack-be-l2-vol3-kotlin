package com.loopers.domain.payment

import java.time.ZonedDateTime

interface PaymentRepository {
    fun findByIdOrNull(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
    fun findByTransactionId(transactionId: String): Payment?
    fun findByPgOrderId(pgOrderId: String): Payment?
    fun findAllByStatus(status: PaymentStatus): List<Payment>
    fun findAllRecoveryTargets(createdBefore: ZonedDateTime): List<Payment>
    fun save(payment: Payment): Payment
}
