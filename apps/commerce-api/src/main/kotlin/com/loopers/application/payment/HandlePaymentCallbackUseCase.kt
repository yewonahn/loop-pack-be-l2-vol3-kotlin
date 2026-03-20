package com.loopers.application.payment

import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.PaymentException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class HandlePaymentCallbackUseCase(
    private val paymentRepository: PaymentRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PG_STATUS_SUCCESS = "SUCCESS"
    }

    @Transactional
    fun execute(command: PaymentCommand.Callback): PaymentInfo {
        val payment = paymentRepository.findByTransactionId(command.transactionId)
            ?: throw PaymentException.notFound()

        if (payment.status.isTerminal()) {
            log.info(
                "이미 처리된 결제 콜백 무시 [transactionId={}, status={}]",
                command.transactionId, payment.status,
            )
            return PaymentInfo.from(payment)
        }

        val updatedCount = if (command.status == PG_STATUS_SUCCESS) {
            paymentRepository.approveIfNotTerminal(payment.id)
        } else {
            paymentRepository.failIfNotTerminal(payment.id, command.reason ?: "PG 결제 실패")
        }

        if (updatedCount == 0) {
            log.info(
                "동시 콜백 처리로 이미 반영됨 [transactionId={}, paymentId={}]",
                command.transactionId, payment.id,
            )
        }

        val updated = paymentRepository.findByIdOrNull(payment.id)
            ?: throw PaymentException.notFound()
        return PaymentInfo.from(updated)
    }
}
