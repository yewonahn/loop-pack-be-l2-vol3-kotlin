package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class PaymentErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_001", "결제 정보를 찾을 수 없습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAYMENT_002", "결제 상태를 변경할 수 없습니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "PAYMENT_003", "결제 금액은 0 이상이어야 합니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_004", "주문 정보를 찾을 수 없습니다."),
    PG_REQUEST_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_005", "PG 결제 요청에 실패했습니다."),
    PG_SYSTEM_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_006", "결제 시스템 점검 중입니다. 잠시 후 다시 시도해주세요."),
    ALREADY_PAID(HttpStatus.CONFLICT, "PAYMENT_007", "이미 결제 완료된 주문입니다."),
    ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT_008", "이미 진행 중인 결제가 있습니다."),
}
