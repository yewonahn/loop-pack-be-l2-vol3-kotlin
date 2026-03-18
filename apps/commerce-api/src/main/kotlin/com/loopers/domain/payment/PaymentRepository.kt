package com.loopers.domain.payment

interface PaymentRepository {
    fun findByIdOrNull(id: Long): Payment?
    fun findByTransactionId(transactionId: String): Payment?
    fun findByPgOrderId(pgOrderId: String): Payment?
    fun findAllByStatus(status: PaymentStatus): List<Payment>
    fun save(payment: Payment): Payment
}
