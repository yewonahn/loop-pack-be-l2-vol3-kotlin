package com.loopers.infrastructure.payment.pg

import feign.Request
import org.springframework.context.annotation.Bean
import java.util.concurrent.TimeUnit

class PgFeignConfig {

    @Bean
    fun requestOptions(): Request.Options {
        return Request.Options(
            1L, TimeUnit.SECONDS,   // connectTimeout: 네트워크 연결은 1초 안에
            5L, TimeUnit.SECONDS,   // readTimeout: PG 처리 지연 100~500ms + 여유
            true,                   // followRedirects
        )
    }
}
