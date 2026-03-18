package com.loopers.interfaces.api.payment

import com.loopers.application.payment.GetPaymentUseCase
import com.loopers.application.payment.HandlePaymentCallbackUseCase
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.RecoverPaymentUseCase
import com.loopers.application.payment.RequestPaymentUseCase
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.CurrentUserId
import com.loopers.support.constant.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class PaymentV1Controller(
    private val requestPaymentUseCase: RequestPaymentUseCase,
    private val handlePaymentCallbackUseCase: HandlePaymentCallbackUseCase,
    private val getPaymentUseCase: GetPaymentUseCase,
    private val recoverPaymentUseCase: RecoverPaymentUseCase,
) {

    @PostMapping(ApiPaths.Payments.BASE)
    @ResponseStatus(HttpStatus.CREATED)
    fun requestPayment(
        @CurrentUserId userId: Long,
        @RequestBody request: PaymentRequest,
    ): ApiResponse<PaymentResponse> {
        val result = requestPaymentUseCase.execute(
            PaymentCommand.Request(
                orderId = request.orderId,
                userId = userId,
                cardType = request.cardType,
                cardNo = request.cardNo,
            ),
        )
        return ApiResponse.success(PaymentResponse.from(result))
    }

    @PostMapping(ApiPaths.Payments.CALLBACK)
    fun handleCallback(
        @RequestBody request: PaymentCallbackRequest,
    ): ApiResponse<PaymentResponse> {
        val result = handlePaymentCallbackUseCase.execute(
            PaymentCommand.Callback(
                transactionId = request.transactionId,
                status = request.status,
                reason = request.reason,
            ),
        )
        return ApiResponse.success(PaymentResponse.from(result))
    }

    @GetMapping(ApiPaths.Payments.BY_ID)
    fun getPayment(
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentResponse> {
        val result = getPaymentUseCase.execute(paymentId)
        return ApiResponse.success(PaymentResponse.from(result))
    }

    @PostMapping(ApiPaths.Payments.RECOVER)
    fun recoverPayment(
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentResponse> {
        val result = recoverPaymentUseCase.execute(paymentId)
        return ApiResponse.success(PaymentResponse.from(result))
    }
}
