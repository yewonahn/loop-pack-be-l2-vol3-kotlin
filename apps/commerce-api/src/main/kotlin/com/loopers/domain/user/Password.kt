package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Embeddable
class Password protected constructor(
    val value: String = "",
) {
    companion object {
        private const val MIN_LENGTH = 8
        private const val MAX_LENGTH = 16
        private val ALLOWED_PATTERN = Regex("^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$")
        private val BIRTH_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        /**
         * 비밀번호 규칙을 검증합니다.
         * - 8~16자
         * - 영문 대소문자, 숫자, 특수문자만 허용
         * - 생년월일 포함 불가
         *
         * 저장용 Password 객체를 생성하려면 fromEncoded()를 사용하세요.
         */
        fun validate(rawPassword: String, birthDate: LocalDate) {
            validateLength(rawPassword)
            validateFormat(rawPassword)
            validateNotContainsBirthDate(rawPassword, birthDate)
        }

        /**
         * 암호화된 비밀번호로 Password를 생성합니다.
         * 반드시 validate()로 검증 후 사용하세요.
         */
        fun fromEncoded(encodedPassword: String): Password {
            return Password(encodedPassword)
        }

        private fun validateLength(rawPassword: String) {
            if (rawPassword.length < MIN_LENGTH || rawPassword.length > MAX_LENGTH) {
                throw CoreException(UserErrorCode.INVALID_PASSWORD_LENGTH)
            }
        }

        private fun validateFormat(rawPassword: String) {
            if (!rawPassword.matches(ALLOWED_PATTERN)) {
                throw CoreException(UserErrorCode.INVALID_PASSWORD_FORMAT)
            }
        }

        private fun validateNotContainsBirthDate(rawPassword: String, birthDate: LocalDate) {
            val birthDateString = birthDate.format(BIRTH_DATE_FORMAT)
            if (rawPassword.contains(birthDateString)) {
                throw CoreException(UserErrorCode.PASSWORD_CONTAINS_BIRTH_DATE)
            }
        }
    }
}
