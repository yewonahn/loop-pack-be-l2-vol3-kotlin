package com.loopers.application.payment

import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.PaymentException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetPaymentUseCase(
    private val paymentRepository: PaymentRepository,
) {
    @Transactional(readOnly = true)
    fun execute(paymentId: Long): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()
        return PaymentInfo.from(payment)
    }
}
