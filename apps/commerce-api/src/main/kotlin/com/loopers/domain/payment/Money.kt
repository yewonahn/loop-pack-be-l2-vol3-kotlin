package com.loopers.domain.payment

import com.loopers.support.error.PaymentException
import jakarta.persistence.Embeddable

@Embeddable
data class Money(
    val amount: Long = 0,
) {
    init {
        if (amount < 0) {
            throw PaymentException.invalidAmount()
        }
    }
}
