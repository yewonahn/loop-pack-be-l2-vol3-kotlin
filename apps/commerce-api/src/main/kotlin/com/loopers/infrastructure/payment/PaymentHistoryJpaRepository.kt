package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentHistory
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentHistoryJpaRepository : JpaRepository<PaymentHistory, Long> {
    fun findAllByPaymentId(paymentId: Long): List<PaymentHistory>
}
