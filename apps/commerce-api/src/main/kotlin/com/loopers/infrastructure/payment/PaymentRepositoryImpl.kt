package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

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

    override fun findAllRecoveryTargets(createdBefore: ZonedDateTime): List<Payment> {
        return paymentJpaRepository.findAllRecoveryTargets(createdBefore)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }

    override fun approveIfNotTerminal(paymentId: Long): Int {
        return paymentJpaRepository.approveIfNotTerminal(paymentId)
    }

    override fun failIfNotTerminal(paymentId: Long, failReason: String): Int {
        return paymentJpaRepository.failIfNotTerminal(paymentId, failReason)
    }
}
