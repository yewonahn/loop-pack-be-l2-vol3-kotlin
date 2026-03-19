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

        val transactionId = payment.transactionId
        if (transactionId == null) {
            log.info("transactionId가 없는 결제는 PG 조회 불가 [paymentId={}]", paymentId)
            return PaymentInfo.from(payment)
        }

        return try {
            val pgStatus = pgPaymentClient.getPaymentByTransactionId(transactionId, payment.userId)
            paymentTransactionManager.applyPgResult(paymentId, pgStatus.status, pgStatus.reason)
        } catch (e: Exception) {
            log.warn("PG 상태 확인 실패 [paymentId={}, transactionId={}]: {}", paymentId, transactionId, e.message)
            PaymentInfo.from(payment)
        }
    }
}
