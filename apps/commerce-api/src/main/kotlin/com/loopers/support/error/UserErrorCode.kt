package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class UserErrorCode(
    override val status: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    INVALID_LOGIN_ID_FORMAT(HttpStatus.BAD_REQUEST, "USER_001", "로그인 ID는 영문과 숫자만 사용 가능합니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "USER_002", "이메일 형식이 올바르지 않습니다."),
    INVALID_NAME_FORMAT(HttpStatus.BAD_REQUEST, "USER_003", "이름은 2~20자여야 합니다."),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "USER_004", "생년월일이 올바르지 않습니다."),
    INVALID_PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "USER_005", "비밀번호는 8~16자여야 합니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "USER_006", "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다."),
    PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "USER_007", "비밀번호에 생년월일을 포함할 수 없습니다."),
}
