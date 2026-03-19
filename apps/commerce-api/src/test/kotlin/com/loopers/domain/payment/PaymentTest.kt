package com.loopers.domain.payment

import com.loopers.support.error.PaymentErrorCode
import com.loopers.support.error.PaymentException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentTest {

    private fun createPayment(
        orderId: Long = 1L,
        userId: Long = 1L,
        amount: Money = Money(10000),
        cardType: CardType = CardType.SAMSUNG,
        cardNo: String = "1234567890123456",
    ): Payment {
        return Payment.create(
            orderId = orderId,
            userId = userId,
            amount = amount,
            cardType = cardType,
            cardNo = cardNo,
        )
    }

    @DisplayName("결제 생성")
    @Nested
    inner class Create {

        @DisplayName("주문ID, 유저ID, 금액, 카드정보가 주어지면 REQUESTED 상태로 생성된다.")
        @Test
        fun success() {
            // act
            val payment = createPayment()

            // assert
            assertAll(
                { assertThat(payment.orderId).isEqualTo(1L) },
                { assertThat(payment.userId).isEqualTo(1L) },
                { assertThat(payment.amount).isEqualTo(Money(10000)) },
                { assertThat(payment.cardType).isEqualTo(CardType.SAMSUNG) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(payment.transactionId).isNull() },
                { assertThat(payment.failReason).isNull() },
                { assertThat(payment.paidAt).isNull() },
            )
        }

        @DisplayName("pgOrderId가 yyyyMMdd:8자리 형식으로 자동 생성된다.")
        @Test
        fun pgOrderIdGenerated() {
            // act
            val payment = createPayment()

            // assert
            assertThat(payment.pgOrderId).matches("\\d{8}:[a-f0-9]{8}")
        }

        @DisplayName("금액이 음수이면 INVALID_AMOUNT 예외가 발생한다.")
        @Test
        fun failWhenAmountIsNegative() {
            // act & assert
            val exception = assertThrows<PaymentException> {
                createPayment(amount = Money(-1))
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_AMOUNT)
        }
    }

    @DisplayName("transactionId 업데이트")
    @Nested
    inner class UpdateTransactionId {

        @DisplayName("REQUESTED 상태에서 transactionId를 업데이트한다.")
        @Test
        fun success() {
            // arrange
            val payment = createPayment()

            // act
            payment.updateTransactionId("txn_123456")

            // assert
            assertThat(payment.transactionId).isEqualTo("txn_123456")
        }

        @DisplayName("APPROVED 상태에서 업데이트하면 INVALID_PAYMENT_STATUS 예외가 발생한다.")
        @Test
        fun failWhenAlreadyApproved() {
            // arrange
            val payment = createPayment()
            payment.approve()

            // act & assert
            val exception = assertThrows<PaymentException> {
                payment.updateTransactionId("txn_new")
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @DisplayName("결제 승인")
    @Nested
    inner class Approve {

        @DisplayName("REQUESTED 상태에서 APPROVED로 전환된다.")
        @Test
        fun success() {
            // arrange
            val payment = createPayment()

            // act
            payment.approve()

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
                { assertThat(payment.paidAt).isNotNull() },
            )
        }

        @DisplayName("FAILED 상태에서 승인하면 INVALID_PAYMENT_STATUS 예외가 발생한다.")
        @Test
        fun failWhenAlreadyFailed() {
            // arrange
            val payment = createPayment()
            payment.fail("한도 초과")

            // act & assert
            val exception = assertThrows<PaymentException> {
                payment.approve()
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @DisplayName("결제 실패")
    @Nested
    inner class Fail {

        @DisplayName("REQUESTED 상태에서 FAILED로 전환된다.")
        @Test
        fun success() {
            // arrange
            val payment = createPayment()

            // act
            payment.fail("한도 초과")

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(payment.failReason).isEqualTo("한도 초과") },
            )
        }

        @DisplayName("APPROVED 상태에서 실패 처리하면 INVALID_PAYMENT_STATUS 예외가 발생한다.")
        @Test
        fun failWhenAlreadyApproved() {
            // arrange
            val payment = createPayment()
            payment.approve()

            // act & assert
            val exception = assertThrows<PaymentException> {
                payment.fail("한도 초과")
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @DisplayName("PG 요청 실패")
    @Nested
    inner class MarkRequestFailed {

        @DisplayName("REQUESTED 상태에서 REQUEST_FAILED로 전환된다.")
        @Test
        fun success() {
            // arrange
            val payment = createPayment()

            // act
            payment.markRequestFailed("타임아웃")

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.REQUEST_FAILED) },
                { assertThat(payment.failReason).isEqualTo("타임아웃") },
            )
        }

        @DisplayName("APPROVED 상태에서 요청 실패 처리하면 INVALID_PAYMENT_STATUS 예외가 발생한다.")
        @Test
        fun failWhenAlreadyApproved() {
            // arrange
            val payment = createPayment()
            payment.approve()

            // act & assert
            val exception = assertThrows<PaymentException> {
                payment.markRequestFailed("타임아웃")
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @DisplayName("재시도 리셋")
    @Nested
    inner class ResetForRetry {

        @DisplayName("FAILED 상태에서 REQUESTED로 리셋된다.")
        @Test
        fun resetFromFailed() {
            // arrange
            val payment = createPayment()
            payment.updateTransactionId("txn_old")
            payment.fail("한도 초과")

            // act
            payment.resetForRetry()

            // assert
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(payment.transactionId).isNull() },
                { assertThat(payment.failReason).isNull() },
            )
        }

        @DisplayName("REQUEST_FAILED 상태에서 REQUESTED로 리셋된다.")
        @Test
        fun resetFromRequestFailed() {
            // arrange
            val payment = createPayment()
            payment.markRequestFailed("타임아웃")

            // act
            payment.resetForRetry()

            // assert
            assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
        }

        @DisplayName("REQUESTED 상태에서 리셋하면 INVALID_PAYMENT_STATUS 예외가 발생한다.")
        @Test
        fun failWhenRequested() {
            // arrange
            val payment = createPayment()

            // act & assert
            val exception = assertThrows<PaymentException> {
                payment.resetForRetry()
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }

        @DisplayName("APPROVED 상태에서 리셋하면 INVALID_PAYMENT_STATUS 예외가 발생한다.")
        @Test
        fun failWhenApproved() {
            // arrange
            val payment = createPayment()
            payment.approve()

            // act & assert
            val exception = assertThrows<PaymentException> {
                payment.resetForRetry()
            }
            assertThat(exception.errorCode).isEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS)
        }
    }

    @DisplayName("REQUEST_FAILED 복구")
    @Nested
    inner class RecoveryFromRequestFailed {

        @DisplayName("REQUEST_FAILED 상태에서 approve가 가능하다.")
        @Test
        fun approveFromRequestFailed() {
            // arrange
            val payment = createPayment()
            payment.markRequestFailed("타임아웃")

            // act
            payment.approve()

            // assert
            assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
        }

        @DisplayName("REQUEST_FAILED 상태에서 fail이 가능하다.")
        @Test
        fun failFromRequestFailed() {
            // arrange
            val payment = createPayment()
            payment.markRequestFailed("타임아웃")

            // act
            payment.fail("한도 초과")

            // assert
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @DisplayName("REQUEST_FAILED 상태에서 transactionId 업데이트가 가능하다.")
        @Test
        fun updateTransactionIdFromRequestFailed() {
            // arrange
            val payment = createPayment()
            payment.markRequestFailed("타임아웃")

            // act
            payment.updateTransactionId("txn_recovered")

            // assert
            assertThat(payment.transactionId).isEqualTo("txn_recovered")
        }
    }
}
