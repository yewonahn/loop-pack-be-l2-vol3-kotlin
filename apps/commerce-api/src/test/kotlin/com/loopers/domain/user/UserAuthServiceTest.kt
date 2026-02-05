package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

class UserAuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var userAuthService: UserAuthService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        passwordEncoder = mock()
        userAuthService = UserAuthService(userRepository, passwordEncoder)
    }

    @DisplayName("사용자 인증")
    @Nested
    inner class Authenticate {

        @DisplayName("올바른 로그인ID와 비밀번호가 주어지면 사용자를 반환한다.")
        @Test
        fun success() {
            // arrange
            val loginId = "testuser"
            val rawPassword = "Test123!"
            val encodedPassword = "encodedPassword"
            val user = User.create(
                loginId = loginId,
                encodedPassword = encodedPassword,
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            whenever(userRepository.findByLoginId(loginId)).thenReturn(user)
            whenever(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true)

            // act
            val result = userAuthService.authenticate(loginId, rawPassword)

            // assert
            assertThat(result.loginId).isEqualTo(loginId)
        }

        @DisplayName("존재하지 않는 로그인ID이면 AUTHENTICATION_FAILED 예외가 발생한다.")
        @Test
        fun failWhenUserNotFound() {
            // arrange
            val loginId = "nonexistent"
            val rawPassword = "Test123!"

            whenever(userRepository.findByLoginId(loginId)).thenReturn(null)

            // act & assert
            val exception = assertThrows<CoreException> {
                userAuthService.authenticate(loginId, rawPassword)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED)
        }

        @DisplayName("비밀번호가 일치하지 않으면 AUTHENTICATION_FAILED 예외가 발생한다.")
        @Test
        fun failWhenPasswordNotMatched() {
            // arrange
            val loginId = "testuser"
            val rawPassword = "WrongPassword!"
            val encodedPassword = "encodedPassword"
            val user = User.create(
                loginId = loginId,
                encodedPassword = encodedPassword,
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            whenever(userRepository.findByLoginId(loginId)).thenReturn(user)
            whenever(passwordEncoder.matches(any(), any())).thenReturn(false)

            // act & assert
            val exception = assertThrows<CoreException> {
                userAuthService.authenticate(loginId, rawPassword)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED)
        }
    }
}
