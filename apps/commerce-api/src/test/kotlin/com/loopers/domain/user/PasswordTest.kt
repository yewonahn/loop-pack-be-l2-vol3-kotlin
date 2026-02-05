package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.UserErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class PasswordTest {

    private val birthDate = LocalDate.of(1990, 1, 1)

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    inner class Validate {

        @DisplayName("8자 이상 16자 이하의 영문, 숫자, 특수문자 조합이면 성공한다.")
        @Test
        fun success() {
            // arrange
            val rawPassword = "Test123!"

            // act & assert
            assertDoesNotThrow {
                Password.validate(rawPassword, birthDate)
            }
        }

        @DisplayName("8자 최소 길이도 성공한다.")
        @Test
        fun successWithMinLength() {
            // arrange
            val rawPassword = "Test123!" // 8자

            // act & assert
            assertDoesNotThrow {
                Password.validate(rawPassword, birthDate)
            }
        }

        @DisplayName("16자 최대 길이도 성공한다.")
        @Test
        fun successWithMaxLength() {
            // arrange
            val rawPassword = "TestTest1234!@#$" // 16자

            // act & assert
            assertDoesNotThrow {
                Password.validate(rawPassword, birthDate)
            }
        }
    }

    @DisplayName("비밀번호 검증 실패 - 길이")
    @Nested
    inner class FailByLength {

        @DisplayName("7자(8자 미만)이면 실패한다.")
        @Test
        fun failWhenLessThan8() {
            // arrange
            val rawPassword = "Test12!" // 7자

            // act & assert
            val exception = assertThrows<CoreException> {
                Password.validate(rawPassword, birthDate)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_PASSWORD_LENGTH)
        }

        @DisplayName("17자(16자 초과)이면 실패한다.")
        @Test
        fun failWhenMoreThan16() {
            // arrange
            val rawPassword = "TestTest1234!@#$%" // 17자

            // act & assert
            val exception = assertThrows<CoreException> {
                Password.validate(rawPassword, birthDate)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_PASSWORD_LENGTH)
        }

        @DisplayName("빈 문자열이면 실패한다.")
        @Test
        fun failWhenEmpty() {
            // arrange
            val rawPassword = ""

            // act & assert
            val exception = assertThrows<CoreException> {
                Password.validate(rawPassword, birthDate)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_PASSWORD_LENGTH)
        }
    }

    @DisplayName("비밀번호 검증 실패 - 허용되지 않는 문자")
    @Nested
    inner class FailByInvalidCharacter {

        @DisplayName("한글이 포함되면 실패한다.")
        @Test
        fun failWhenContainsKorean() {
            // arrange
            val rawPassword = "Test123!가"

            // act & assert
            val exception = assertThrows<CoreException> {
                Password.validate(rawPassword, birthDate)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT)
        }

        @DisplayName("공백이 포함되면 실패한다.")
        @Test
        fun failWhenContainsSpace() {
            // arrange
            val rawPassword = "Test 123!"

            // act & assert
            val exception = assertThrows<CoreException> {
                Password.validate(rawPassword, birthDate)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_PASSWORD_FORMAT)
        }
    }

    @DisplayName("비밀번호 검증 실패 - 생년월일 포함")
    @Nested
    inner class FailByBirthDate {

        @DisplayName("생년월일(YYYYMMDD)이 포함되면 실패한다.")
        @Test
        fun failWhenContainsBirthDate() {
            // arrange
            val rawPassword = "Pass19900101!" // 생년월일 1990-01-01 포함

            // act & assert
            val exception = assertThrows<CoreException> {
                Password.validate(rawPassword, birthDate)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.PASSWORD_CONTAINS_BIRTH_DATE)
        }
    }

    @DisplayName("암호화된 비밀번호로 Password를 생성할 때,")
    @Nested
    inner class FromEncoded {

        @DisplayName("암호화된 값으로 Password 객체가 생성된다.")
        @Test
        fun success() {
            // arrange
            val encodedPassword = "\$2a\$10\$abcdefghijklmnopqrstuv"

            // act
            val password = Password.fromEncoded(encodedPassword)

            // assert
            assertThat(password.value).isEqualTo(encodedPassword)
        }
    }
}
