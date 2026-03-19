package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: Long): Payment?
    fun findByTransactionId(transactionId: String): Payment?
    fun findByPgOrderId(pgOrderId: String): Payment?
    fun findAllByStatus(status: PaymentStatus): List<Payment>

    @Query(
        "SELECT p FROM Payment p WHERE p.status IN ('REQUESTED', 'REQUEST_FAILED') AND p.createdAt < :createdBefore",
    )
    fun findAllRecoveryTargets(@Param("createdBefore") createdBefore: ZonedDateTime): List<Payment>
}
