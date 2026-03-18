package com.loopers.application.payment

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentRequest
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.PaymentException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RequestPaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val pgPaymentClient: PgPaymentClient,
    @Value("\${pg.simulator.callback-url}") private val callbackUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(command: PaymentCommand.Request): PaymentInfo {
        // TX-1: Order 존재 확인 + Payment 저장 (REQUESTED)
        val payment = savePayment(command)

        // No TX: PG API 호출 (외부 시스템)
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
            // TX-2: 성공 → transactionId 저장
            return updateTransactionId(payment.id, pgResponse.transactionId)
        } catch (e: Exception) {
            log.warn("PG 결제 요청 실패 [pgOrderId={}]: {}", payment.pgOrderId, e.message)
            // TX-2: 실패 → REQUEST_FAILED
            return markRequestFailed(payment.id, e.message)
        }
    }

    @Transactional
    fun savePayment(command: PaymentCommand.Request): Payment {
        val order = orderRepository.findByIdOrNull(command.orderId)
            ?: throw PaymentException.orderNotFound()

        val payment = Payment.create(
            orderId = order.id,
            userId = command.userId,
            amount = Money(order.totalAmount.amount),
            cardType = command.cardType,
            cardNo = command.cardNo,
        )
        return paymentRepository.save(payment)
    }

    @Transactional
    fun updateTransactionId(paymentId: Long, transactionId: String): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()
        payment.updateTransactionId(transactionId)
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun markRequestFailed(paymentId: Long, reason: String?): PaymentInfo {
        val payment = paymentRepository.findByIdOrNull(paymentId)
            ?: throw PaymentException.notFound()
        payment.markRequestFailed(reason)
        return PaymentInfo.from(payment)
    }
}
