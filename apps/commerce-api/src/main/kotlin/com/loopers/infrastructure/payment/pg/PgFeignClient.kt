package com.loopers.infrastructure.payment.pg

import com.loopers.application.payment.port.PgPaymentRequest
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.application.payment.port.PgPaymentStatusResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "pg-simulator",
    url = "\${pg.simulator.url}",
    configuration = [PgFeignConfig::class],
)
interface PgFeignClient {

    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody request: PgPaymentRequest,
    ): PgPaymentResponse

    @GetMapping("/api/v1/payments/{transactionId}")
    fun getPaymentByTransactionId(
        @PathVariable transactionId: String,
        @RequestHeader("X-USER-ID") userId: Long,
    ): PgPaymentStatusResponse

    @GetMapping("/api/v1/payments")
    fun getPaymentsByOrderId(
        @RequestParam orderId: String,
        @RequestHeader("X-USER-ID") userId: Long,
    ): List<PgPaymentStatusResponse>
}
