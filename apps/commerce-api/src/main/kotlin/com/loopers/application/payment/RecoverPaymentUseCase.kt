package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.PaymentException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RecoverPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val pgPaymentClient: PgPaymentClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PG_STATUS_SUCCESS = "SUCCESS"
        private const val PG_STATUS_PENDING = "PENDING"
        private const val PG_STATUS_REQUESTED = "REQUESTED"
    }

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
            applyPgResult(paymentId, pgStatus.status, pgStatus.reason)
        } catch (e: Exception) {
            log.warn("PG 상태 확인 실패 [paymentId={}, transactionId={}]: {}", paymentId, transactionId, e.message)
            PaymentInfo.from(payment)
        }
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
            PG_STATUS_PENDING, PG_STATUS_REQUESTED -> {
                log.info("PG에서 아직 처리 중 [paymentId={}, pgStatus={}]", paymentId, pgStatus)
            }
            else -> payment.fail(reason ?: "PG 결제 실패")
        }

        return PaymentInfo.from(payment)
    }
}
