package com.loopers.domain.payment

enum class PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
    REQUEST_FAILED,
    ;

    fun isTerminal(): Boolean = this == APPROVED || this == FAILED
}
