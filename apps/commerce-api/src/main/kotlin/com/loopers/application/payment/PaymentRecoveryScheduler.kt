package com.loopers.application.payment

import com.loopers.domain.payment.PaymentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class PaymentRecoveryScheduler(
    private val paymentRepository: PaymentRepository,
    private val recoverPaymentUseCase: RecoverPaymentUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000)
    fun recoverPendingPayments() {
        val now = ZonedDateTime.now()
        val threshold = now.minusMinutes(5)
        val abandonedThreshold = now.minusHours(1)

        val targets = paymentRepository.findAllRecoveryTargets(createdBefore = threshold)

        val (recoverable, abandoned) = targets.partition { it.createdAt.isAfter(abandonedThreshold) }

        abandoned.forEach { payment ->
            log.warn(
                "1시간 초과 미완료 결제 감지 [paymentId={}, status={}, createdAt={}] - 수동 확인 필요",
                payment.id,
                payment.status,
                payment.createdAt,
            )
        }

        if (recoverable.isEmpty()) return

        log.info("결제 자동 복구 시작 [대상={}건]", recoverable.size)

        var successCount = 0
        var failCount = 0

        recoverable.forEach { payment ->
            try {
                recoverPaymentUseCase.execute(payment.id)
                successCount++
            } catch (e: Exception) {
                failCount++
                log.error("결제 자동 복구 실패 [paymentId={}]: {}", payment.id, e.message)
            }
        }

        log.info("결제 자동 복구 완료 [성공={}건, 실패={}건]", successCount, failCount)
    }
}
