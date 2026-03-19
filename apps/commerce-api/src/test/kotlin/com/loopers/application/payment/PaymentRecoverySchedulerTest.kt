package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class PaymentRecoverySchedulerTest {

    private val paymentRepository: PaymentRepository = mockk()
    private val recoverPaymentUseCase: RecoverPaymentUseCase = mockk()
    private val scheduler = PaymentRecoveryScheduler(paymentRepository, recoverPaymentUseCase)

    private fun createPayment(
        id: Long,
        createdAt: ZonedDateTime,
        status: PaymentStatus = PaymentStatus.REQUESTED,
    ): Payment {
        val payment = Payment.create(
            orderId = id,
            userId = 1L,
            amount = Money(10000),
            cardType = CardType.SAMSUNG,
            cardNo = "1234",
        )
        // 테스트용으로 createdAt과 id를 리플렉션으로 설정
        val baseClass = payment.javaClass.superclass
        val idField = baseClass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(payment, id)

        val createdAtField = baseClass.getDeclaredField("createdAt")
        createdAtField.isAccessible = true
        createdAtField.set(payment, createdAt)

        if (status == PaymentStatus.REQUEST_FAILED) {
            payment.markRequestFailed("PG 요청 실패")
        }

        return payment
    }

    @DisplayName("결제 자동 복구 스케줄러")
    @Nested
    inner class RecoverPendingPayments {

        @DisplayName("복구 대상이 없으면 UseCase를 호출하지 않는다.")
        @Test
        fun noTargets() {
            // arrange
            every { paymentRepository.findAllRecoveryTargets(any()) } returns emptyList()

            // act
            scheduler.recoverPendingPayments()

            // assert
            verify(exactly = 0) { recoverPaymentUseCase.execute(any()) }
        }

        @DisplayName("5분~1시간 범위의 복구 대상에 대해 RecoverPaymentUseCase를 호출한다.")
        @Test
        fun recoverTargets() {
            // arrange
            val payment1 = createPayment(id = 1L, createdAt = ZonedDateTime.now().minusMinutes(10))
            val payment2 = createPayment(id = 2L, createdAt = ZonedDateTime.now().minusMinutes(30))
            every { paymentRepository.findAllRecoveryTargets(any()) } returns listOf(payment1, payment2)
            every { recoverPaymentUseCase.execute(any()) } returns mockk()

            // act
            scheduler.recoverPendingPayments()

            // assert
            verify(exactly = 1) { recoverPaymentUseCase.execute(1L) }
            verify(exactly = 1) { recoverPaymentUseCase.execute(2L) }
        }

        @DisplayName("1시간 초과 건은 UseCase를 호출하지 않는다.")
        @Test
        fun skipAbandoned() {
            // arrange
            val abandonedPayment = createPayment(id = 1L, createdAt = ZonedDateTime.now().minusHours(2))
            every { paymentRepository.findAllRecoveryTargets(any()) } returns listOf(abandonedPayment)

            // act
            scheduler.recoverPendingPayments()

            // assert
            verify(exactly = 0) { recoverPaymentUseCase.execute(any()) }
        }

        @DisplayName("REQUEST_FAILED 상태도 복구 대상이다.")
        @Test
        fun recoverRequestFailed() {
            // arrange
            val payment = createPayment(
                id = 1L,
                createdAt = ZonedDateTime.now().minusMinutes(10),
                status = PaymentStatus.REQUEST_FAILED,
            )
            every { paymentRepository.findAllRecoveryTargets(any()) } returns listOf(payment)
            every { recoverPaymentUseCase.execute(any()) } returns mockk()

            // act
            scheduler.recoverPendingPayments()

            // assert
            verify(exactly = 1) { recoverPaymentUseCase.execute(1L) }
        }

        @DisplayName("한 건 복구 실패해도 나머지 건은 계속 처리한다.")
        @Test
        fun continueOnFailure() {
            // arrange
            val payment1 = createPayment(id = 1L, createdAt = ZonedDateTime.now().minusMinutes(10))
            val payment2 = createPayment(id = 2L, createdAt = ZonedDateTime.now().minusMinutes(20))
            val payment3 = createPayment(id = 3L, createdAt = ZonedDateTime.now().minusMinutes(30))
            every { paymentRepository.findAllRecoveryTargets(any()) } returns listOf(payment1, payment2, payment3)
            every { recoverPaymentUseCase.execute(1L) } throws RuntimeException("PG 연결 실패")
            every { recoverPaymentUseCase.execute(2L) } returns mockk()
            every { recoverPaymentUseCase.execute(3L) } returns mockk()

            // act
            scheduler.recoverPendingPayments()

            // assert
            verify(exactly = 1) { recoverPaymentUseCase.execute(1L) }
            verify(exactly = 1) { recoverPaymentUseCase.execute(2L) }
            verify(exactly = 1) { recoverPaymentUseCase.execute(3L) }
        }
    }
}
