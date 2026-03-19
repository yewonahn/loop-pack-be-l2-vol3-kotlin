package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentHistory
import com.loopers.domain.payment.PaymentHistoryRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentHistoryRepositoryImpl(
    private val paymentHistoryJpaRepository: PaymentHistoryJpaRepository,
) : PaymentHistoryRepository {

    override fun save(paymentHistory: PaymentHistory): PaymentHistory {
        return paymentHistoryJpaRepository.save(paymentHistory)
    }

    override fun findAllByPaymentId(paymentId: Long): List<PaymentHistory> {
        return paymentHistoryJpaRepository.findAllByPaymentId(paymentId)
    }
}
