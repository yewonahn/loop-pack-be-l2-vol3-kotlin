package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.PaymentException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RecoverPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val pgPaymentClient: PgPaymentClient,
    private val paymentTransactionManager: PaymentTransactionManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(paymentId: Long): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()

        if (payment.status != PaymentStatus.REQUESTED && payment.status != PaymentStatus.REQUEST_FAILED) {
            return PaymentInfo.from(payment)
        }

        return try {
            val transactionId = payment.transactionId
            if (transactionId != null) {
                val pgStatus = pgPaymentClient.getPaymentByTransactionId(transactionId, payment.userId)
                paymentTransactionManager.applyPgResult(paymentId, pgStatus.status, pgStatus.reason)
            } else {
                recoverByPgOrderId(paymentId, payment.pgOrderId, payment.userId)
            }
        } catch (e: Exception) {
            log.warn("PG 상태 확인 실패 [paymentId={}]: {}", paymentId, e.message)
            PaymentInfo.from(payment)
        }
    }

    private fun recoverByPgOrderId(paymentId: Long, pgOrderId: String, userId: Long): PaymentInfo {
        val pgStatuses = pgPaymentClient.getPaymentsByOrderId(pgOrderId, userId)

        if (pgStatuses.isEmpty()) {
            log.info("PG에 결제 내역 없음 [paymentId={}, pgOrderId={}]", paymentId, pgOrderId)
            val payment = paymentRepository.findByIdOrNull(paymentId)
                ?: throw PaymentException.notFound()
            return PaymentInfo.from(payment)
        }

        val latest = pgStatuses.last()
        return paymentTransactionManager.applyPgResult(
            paymentId = paymentId,
            pgStatus = latest.status,
            reason = latest.reason,
            transactionId = latest.transactionId,
        )
    }
}
