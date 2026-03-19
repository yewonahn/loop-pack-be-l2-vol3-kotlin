package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun findByIdOrNull(id: Long): Payment? {
        return paymentJpaRepository.findById(id).orElse(null)
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)
    }

    override fun findByTransactionId(transactionId: String): Payment? {
        return paymentJpaRepository.findByTransactionId(transactionId)
    }

    override fun findByPgOrderId(pgOrderId: String): Payment? {
        return paymentJpaRepository.findByPgOrderId(pgOrderId)
    }

    override fun findAllByStatus(status: PaymentStatus): List<Payment> {
        return paymentJpaRepository.findAllByStatus(status)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
}
