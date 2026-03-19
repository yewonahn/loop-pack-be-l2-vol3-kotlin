package com.loopers.domain.payment

interface PaymentHistoryRepository {
    fun save(paymentHistory: PaymentHistory): PaymentHistory
    fun findAllByPaymentId(paymentId: Long): List<PaymentHistory>
}
