package com.loopers.domain.user

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("회원가입")
    @Nested
    inner class Register {

        @DisplayName("정상적인 정보가 주어지면, 회원가입에 성공한다.")
        @Test
        fun success() {
            // arrange
            val loginId = "testuser"
            val rawPassword = "Test123!"
            val name = "홍길동"
            val birthDate = LocalDate.of(1990, 1, 1)
            val email = "test@example.com"

            // act
            val user = userService.register(
                loginId = loginId,
                rawPassword = rawPassword,
                name = name,
                birthDate = birthDate,
                email = email,
            )

            // assert
            assertAll(
                { assertThat(user.id).isGreaterThan(0) },
                { assertThat(user.loginId).isEqualTo(loginId) },
                { assertThat(user.name).isEqualTo(name) },
                { assertThat(user.birthDate).isEqualTo(birthDate) },
                { assertThat(user.email).isEqualTo(email) },
                // 비밀번호는 암호화되어 저장되어야 함
                { assertThat(passwordEncoder.matches(rawPassword, user.password.value)).isTrue() },
            )
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, DUPLICATE_LOGIN_ID 예외가 발생한다.")
        @Test
        fun failWhenLoginIdAlreadyExists() {
            // arrange
            val loginId = "existinguser"
            userService.register(
                loginId = loginId,
                rawPassword = "Test123!",
                name = "기존회원",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "existing@example.com",
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                userService.register(
                    loginId = loginId,
                    rawPassword = "Test456!",
                    name = "신규회원",
                    birthDate = LocalDate.of(1995, 5, 5),
                    email = "new@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.DUPLICATE_LOGIN_ID)
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    inner class ChangePassword {

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 규칙을 만족하면 성공한다.")
        @Test
        fun success() {
            // arrange
            val currentPassword = "OldPass123!"
            val newPassword = "NewPass456!"
            val user = userService.register(
                loginId = "testuser",
                rawPassword = currentPassword,
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            // act
            userService.changePassword(user.id!!, currentPassword, newPassword)

            // assert
            val updatedUser = userJpaRepository.findById(user.id).get()
            assertThat(passwordEncoder.matches(newPassword, updatedUser.password.value)).isTrue()
        }

        @DisplayName("현재 비밀번호가 틀리면 INVALID_CURRENT_PASSWORD 예외가 발생한다.")
        @Test
        fun failWhenCurrentPasswordIsWrong() {
            // arrange
            val user = userService.register(
                loginId = "testuser",
                rawPassword = "Correct123!",
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id!!, "WrongPass123!", "NewPass456!")
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_CURRENT_PASSWORD)
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 SAME_PASSWORD 예외가 발생한다.")
        @Test
        fun failWhenNewPasswordIsSameAsCurrent() {
            // arrange
            val currentPassword = "SamePass123!"
            val user = userService.register(
                loginId = "testuser",
                rawPassword = currentPassword,
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id!!, currentPassword, currentPassword)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.SAME_PASSWORD)
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면 예외가 발생한다.")
        @Test
        fun failWhenNewPasswordViolatesRule() {
            // arrange
            val currentPassword = "OldPass123!"
            val user = userService.register(
                loginId = "testuser",
                rawPassword = currentPassword,
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            // act & assert (8자 미만)
            val exception = assertThrows<CoreException> {
                userService.changePassword(user.id!!, currentPassword, "Short1!")
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_PASSWORD_LENGTH)
        }
    }
}
