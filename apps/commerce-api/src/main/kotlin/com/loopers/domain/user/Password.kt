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

        fun of(rawPassword: String, birthDate: LocalDate): Password {
            validateLength(rawPassword)
            validateFormat(rawPassword)
            validateNotContainsBirthDate(rawPassword, birthDate)
            return Password(rawPassword)
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
