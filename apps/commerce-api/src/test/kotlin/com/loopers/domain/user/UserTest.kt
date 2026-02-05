package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserTest {

    @DisplayName("유저 생성")
    @Nested
    inner class Create {

        @DisplayName("로그인ID, 비밀번호, 이름, 생년월일, 이메일이 주어지면 성공한다.")
        @Test
        fun success() {
            // arrange
            val loginId = "testuser"
            val encodedPassword = "Test123!"
            val name = "홍길동"
            val birthDate = LocalDate.of(1990, 1, 1)
            val email = "test@example.com"

            // act
            val user = User.create(
                loginId = loginId,
                encodedPassword = encodedPassword,
                name = name,
                birthDate = birthDate,
                email = email,
            )

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(loginId) },
                { assertThat(user.password.value).isEqualTo(encodedPassword) },
                { assertThat(user.name).isEqualTo(name) },
                { assertThat(user.birthDate).isEqualTo(birthDate) },
                { assertThat(user.email).isEqualTo(email) },
            )
        }
    }

    @DisplayName("유저 생성 실패: 로그인ID")
    @Nested
    inner class FailByLoginId {

        @DisplayName("로그인ID가 4자 미만이면 실패한다.")
        @Test
        fun failWhenLoginIdTooShort() {
            // arrange
            val loginId = "abc"

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = loginId,
                    encodedPassword = "Test123!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_LOGIN_ID_LENGTH)
        }

        @DisplayName("로그인ID가 20자 초과이면 실패한다.")
        @Test
        fun failWhenLoginIdTooLong() {
            // arrange
            val loginId = "a".repeat(21)

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = loginId,
                    encodedPassword = "Test123!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_LOGIN_ID_LENGTH)
        }

        @DisplayName("로그인ID에 특수문자가 포함되면 실패한다.")
        @Test
        fun failWhenLoginIdContainsSpecialCharacter() {
            // arrange
            val loginId = "test_user!"

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = loginId,
                    encodedPassword = "Test123!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_LOGIN_ID_FORMAT)
        }
    }

    @DisplayName("유저 생성 실패: 이메일")
    @Nested
    inner class FailByEmail {

        @DisplayName("이메일 형식이 올바르지 않으면 실패한다.")
        @Test
        fun failWhenEmailFormatIsInvalid() {
            // arrange
            val email = "invalid-email"

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    encodedPassword = "Test123!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = email,
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_EMAIL_FORMAT)
        }
    }

    @DisplayName("유저 생성 실패: 이름")
    @Nested
    inner class FailByName {

        @DisplayName("이름이 빈값이면 실패한다.")
        @Test
        fun failWhenNameIsEmpty() {
            // arrange
            val name = ""

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    encodedPassword = "Test123!",
                    name = name,
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_NAME_FORMAT)
        }

        @DisplayName("이름이 2자 미만이면 실패한다.")
        @Test
        fun failWhenNameIsTooShort() {
            // arrange
            val name = "홍"

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    encodedPassword = "Test123!",
                    name = name,
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_NAME_FORMAT)
        }

        @DisplayName("이름이 20자 초과이면 실패한다.")
        @Test
        fun failWhenNameIsTooLong() {
            // arrange
            val name = "가".repeat(21)

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    encodedPassword = "Test123!",
                    name = name,
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_NAME_FORMAT)
        }
    }

    @DisplayName("유저 생성 실패: 생년월일")
    @Nested
    inner class FailByBirthDate {

        @DisplayName("생년월일이 미래 날짜이면 실패한다.")
        @Test
        fun failWhenBirthDateIsFuture() {
            // arrange
            val futureBirthDate = LocalDate.now().plusDays(1)

            // act & assert
            val exception = assertThrows<CoreException> {
                User.create(
                    loginId = "testuser",
                    encodedPassword = "Test123!",
                    name = "홍길동",
                    birthDate = futureBirthDate,
                    email = "test@example.com",
                )
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_BIRTH_DATE)
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    inner class ChangePassword {

        @DisplayName("새 비밀번호가 주어지면 비밀번호가 변경된다.")
        @Test
        fun success() {
            // arrange
            val user = User.create(
                loginId = "testuser",
                encodedPassword = "OldEncodedPassword",
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )
            val newEncodedPassword = "NewEncodedPassword"

            // act
            user.changePassword(newEncodedPassword)

            // assert
            assertThat(user.password.value).isEqualTo(newEncodedPassword)
        }
    }
}
