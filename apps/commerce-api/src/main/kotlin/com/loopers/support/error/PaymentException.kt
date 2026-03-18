package com.loopers.support.error

class PaymentException private constructor(
    errorCode: PaymentErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : CoreException(errorCode, message, cause) {

    companion object {
        fun notFound() = PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND)

        fun invalidStatus() = PaymentException(PaymentErrorCode.INVALID_PAYMENT_STATUS)

        fun invalidAmount() = PaymentException(PaymentErrorCode.INVALID_AMOUNT)

        fun orderNotFound() = PaymentException(PaymentErrorCode.ORDER_NOT_FOUND)

        fun pgRequestFailed(cause: Throwable? = null) =
            PaymentException(PaymentErrorCode.PG_REQUEST_FAILED, cause = cause)

        fun pgSystemError() = PaymentException(PaymentErrorCode.PG_SYSTEM_ERROR)
    }
}
