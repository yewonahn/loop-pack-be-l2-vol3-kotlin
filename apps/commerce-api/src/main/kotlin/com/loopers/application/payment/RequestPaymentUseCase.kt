package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RequestPaymentUseCase(
    private val paymentTransactionManager: PaymentTransactionManager,
    private val pgPaymentClient: PgPaymentClient,
    @Value("\${pg.simulator.callback-url}") private val callbackUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: PaymentCommand.Request): PaymentInfo {
        val payment = paymentTransactionManager.savePayment(command)

        try {
            val pgResponse = pgPaymentClient.requestPayment(
                PgPaymentRequest(
                    orderId = payment.pgOrderId,
                    cardType = command.cardType.name,
                    cardNo = command.cardNo,
                    amount = payment.amount.amount.toString(),
                    callbackUrl = callbackUrl,
                ),
            )
            return paymentTransactionManager.updateTransactionId(payment.id, pgResponse.transactionId)
        } catch (e: Exception) {
            log.warn("PG 결제 요청 실패 [pgOrderId={}]: {}", payment.pgOrderId, e.message)
            return paymentTransactionManager.markRequestFailed(payment.id, e.message)
        }
    }
}
