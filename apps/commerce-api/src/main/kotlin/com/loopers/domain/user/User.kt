package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "users")
class User private constructor(
    loginId: String,
    password: Password,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {

    @Column(nullable = false, unique = true)
    var loginId: String = loginId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "password", nullable = false))
    var password: Password = password
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(nullable = false)
    var email: String = email
        protected set

    companion object {
        private val LOGIN_ID_PATTERN = Regex("^[a-zA-Z0-9]+$")
        private val EMAIL_PATTERN = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        private const val NAME_MIN_LENGTH = 2
        private const val NAME_MAX_LENGTH = 20

        /**
         * User를 생성합니다.
         * @param encodedPassword 이미 암호화된 비밀번호 (Service에서 검증 + 암호화 후 전달)
         */
        fun create(
            loginId: String,
            encodedPassword: String,
            name: String,
            birthDate: LocalDate,
            email: String,
        ): User {
            validateLoginId(loginId)
            validateEmail(email)
            validateName(name)
            validateBirthDate(birthDate)
            return User(
                loginId = loginId,
                password = Password.fromEncoded(encodedPassword),
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }

        private fun validateLoginId(loginId: String) {
            if (!loginId.matches(LOGIN_ID_PATTERN)) {
                throw CoreException(UserErrorCode.INVALID_LOGIN_ID_FORMAT)
            }
        }

        private fun validateEmail(email: String) {
            if (!email.matches(EMAIL_PATTERN)) {
                throw CoreException(UserErrorCode.INVALID_EMAIL_FORMAT)
            }
        }

        private fun validateName(name: String) {
            if (name.length < NAME_MIN_LENGTH || name.length > NAME_MAX_LENGTH) {
                throw CoreException(UserErrorCode.INVALID_NAME_FORMAT)
            }
        }

        private fun validateBirthDate(birthDate: LocalDate) {
            if (birthDate.isAfter(LocalDate.now())) {
                throw CoreException(UserErrorCode.INVALID_BIRTH_DATE)
            }
        }
    }
}
