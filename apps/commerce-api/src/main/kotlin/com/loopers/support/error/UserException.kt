package com.loopers.support.error

class UserException private constructor(
    errorCode: UserErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : CoreException(errorCode, message, cause) {

    companion object {
        fun invalidCredentials() = UserException(UserErrorCode.AUTHENTICATION_FAILED)

        fun invalidLoginIdFormat() = UserException(UserErrorCode.INVALID_LOGIN_ID_FORMAT)

        fun invalidLoginIdLength() = UserException(UserErrorCode.INVALID_LOGIN_ID_LENGTH)

        fun invalidEmailFormat() = UserException(UserErrorCode.INVALID_EMAIL_FORMAT)

        fun invalidNameFormat() = UserException(UserErrorCode.INVALID_NAME_FORMAT)

        fun invalidBirthDate() = UserException(UserErrorCode.INVALID_BIRTH_DATE)

        fun invalidPasswordLength() = UserException(UserErrorCode.INVALID_PASSWORD_LENGTH)

        fun invalidPasswordFormat() = UserException(UserErrorCode.INVALID_PASSWORD_FORMAT)

        fun passwordContainsBirthDate() = UserException(UserErrorCode.PASSWORD_CONTAINS_BIRTH_DATE)

        fun duplicateLoginId(loginId: String) = UserException(
            UserErrorCode.DUPLICATE_LOGIN_ID,
            "이미 사용 중인 로그인 ID입니다: $loginId",
        )

        fun invalidCurrentPassword() = UserException(UserErrorCode.INVALID_CURRENT_PASSWORD)

        fun samePassword() = UserException(UserErrorCode.SAME_PASSWORD)
    }
}
