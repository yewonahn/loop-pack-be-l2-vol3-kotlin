package com.loopers.application.payment

import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.PaymentException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentTransactionManager(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun savePayment(command: PaymentCommand.Request): Payment {
        val order = orderRepository.findByIdOrNull(command.orderId)
            ?: throw PaymentException.orderNotFound()

        val payment = Payment.create(
            orderId = order.id,
            userId = command.userId,
            amount = Money(order.totalAmount.amount),
            cardType = command.cardType,
            cardNo = command.cardNo,
        )
        return paymentRepository.save(payment)
    }

    @Transactional
    fun updateTransactionId(paymentId: Long, transactionId: String): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()
        payment.updateTransactionId(transactionId)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun markRequestFailed(paymentId: Long, reason: String?): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()
        payment.markRequestFailed(reason)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun applyPgResult(paymentId: Long, pgStatus: String, reason: String?): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()

        if (payment.status.isTerminal()) {
            return PaymentInfo.from(payment)
        }

        when (pgStatus) {
            PG_STATUS_SUCCESS -> payment.approve()
            PG_STATUS_PENDING, PG_STATUS_REQUESTED -> { }
            else -> payment.fail(reason ?: "PG 결제 실패")
        }

        return PaymentInfo.from(payment)
    }

    companion object {
        private const val PG_STATUS_SUCCESS = "SUCCESS"
        private const val PG_STATUS_PENDING = "PENDING"
        private const val PG_STATUS_REQUESTED = "REQUESTED"
    }
}
