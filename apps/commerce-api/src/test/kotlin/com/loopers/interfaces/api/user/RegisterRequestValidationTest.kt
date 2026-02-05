package com.loopers.interfaces.api.user

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RegisterRequestValidationTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    private fun createValidRequest(
        loginId: String = "testuser",
        password: String = "Test1234!",
        name: String = "홍길동",
        birthDate: String = "2000-09-30",
        email: String = "test@example.com",
    ) = UserV1Dto.RegisterRequest(
        loginId = loginId,
        password = password,
        name = name,
        birthDate = birthDate,
        email = email,
    )

    @DisplayName("loginId 검증")
    @Nested
    inner class LoginIdValidation {

        @DisplayName("loginId가 비어있으면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun failWhenLoginIdIsBlank(loginId: String) {
            // arrange
            val request = createValidRequest(loginId = loginId)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.map { it.propertyPath.toString() }).contains("loginId")
        }

        @DisplayName("loginId가 4자 미만이면 검증에 실패한다.")
        @Test
        fun failWhenLoginIdTooShort() {
            // arrange
            val request = createValidRequest(loginId = "abc")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "loginId" }.message)
                .isEqualTo("로그인 ID는 4자 이상 20자 이하여야 합니다.")
        }

        @DisplayName("loginId가 20자 초과이면 검증에 실패한다.")
        @Test
        fun failWhenLoginIdTooLong() {
            // arrange
            val request = createValidRequest(loginId = "a".repeat(21))

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "loginId" }.message)
                .isEqualTo("로그인 ID는 4자 이상 20자 이하여야 합니다.")
        }

        @DisplayName("loginId가 4~20자이면 검증을 통과한다.")
        @Test
        fun successWhenLoginIdValid() {
            // arrange
            val request = createValidRequest(loginId = "validuser")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations.filter { it.propertyPath.toString() == "loginId" }).isEmpty()
        }
    }

    @DisplayName("password 검증")
    @Nested
    inner class PasswordValidation {

        @DisplayName("password가 비어있으면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun failWhenPasswordIsBlank(password: String) {
            // arrange
            val request = createValidRequest(password = password)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.map { it.propertyPath.toString() }).contains("password")
        }

        @DisplayName("password가 8자 미만이면 검증에 실패한다.")
        @Test
        fun failWhenPasswordTooShort() {
            // arrange
            val request = createValidRequest(password = "Short1!")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "password" }.message)
                .isEqualTo("비밀번호는 8자 이상 16자 이하여야 합니다.")
        }

        @DisplayName("password가 16자 초과이면 검증에 실패한다.")
        @Test
        fun failWhenPasswordTooLong() {
            // arrange
            val request = createValidRequest(password = "VeryLongPass123!!")  // 17자

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "password" }.message)
                .isEqualTo("비밀번호는 8자 이상 16자 이하여야 합니다.")
        }

        @DisplayName("password가 8~16자이면 검증을 통과한다.")
        @Test
        fun successWhenPasswordValid() {
            // arrange
            val request = createValidRequest(password = "ValidPass1!")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations.filter { it.propertyPath.toString() == "password" }).isEmpty()
        }
    }

    @DisplayName("name 검증")
    @Nested
    inner class NameValidation {

        @DisplayName("name이 비어있으면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun failWhenNameIsBlank(name: String) {
            // arrange
            val request = createValidRequest(name = name)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.map { it.propertyPath.toString() }).contains("name")
        }

        @DisplayName("name이 2자 미만이면 검증에 실패한다.")
        @Test
        fun failWhenNameTooShort() {
            // arrange
            val request = createValidRequest(name = "홍")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "name" }.message)
                .isEqualTo("이름은 2자 이상 20자 이하여야 합니다.")
        }

        @DisplayName("name이 20자 초과이면 검증에 실패한다.")
        @Test
        fun failWhenNameTooLong() {
            // arrange
            val request = createValidRequest(name = "가".repeat(21))

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "name" }.message)
                .isEqualTo("이름은 2자 이상 20자 이하여야 합니다.")
        }

        @DisplayName("name이 2~20자이면 검증을 통과한다.")
        @Test
        fun successWhenNameValid() {
            // arrange
            val request = createValidRequest(name = "홍길동")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations.filter { it.propertyPath.toString() == "name" }).isEmpty()
        }
    }

    @DisplayName("birthDate 검증")
    @Nested
    inner class BirthDateValidation {

        @DisplayName("birthDate가 비어있으면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun failWhenBirthDateIsBlank(birthDate: String) {
            // arrange
            val request = createValidRequest(birthDate = birthDate)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.map { it.propertyPath.toString() }).contains("birthDate")
        }

        @DisplayName("birthDate가 yyyy-MM-dd 형식이면 검증을 통과한다.")
        @Test
        fun successWhenBirthDateValid() {
            // arrange
            val request = createValidRequest(birthDate = "2000-09-30")

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations.filter { it.propertyPath.toString() == "birthDate" }).isEmpty()
        }

        @DisplayName("birthDate가 잘못된 형식이면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["20000930", "2000/09/30", "30-09-2000", "2000.09.30"])
        fun failWhenBirthDateFormatInvalid(invalidDate: String) {
            // arrange
            val request = createValidRequest(birthDate = invalidDate)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "birthDate" }.message)
                .isEqualTo("날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식으로 입력해주세요.")
        }

        @DisplayName("birthDate가 존재하지 않는 날짜이면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["2000-13-01", "2000-02-31", "2000-00-01", "2000-09-99"])
        fun failWhenBirthDateInvalid(invalidDate: String) {
            // arrange
            val request = createValidRequest(birthDate = invalidDate)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "birthDate" }.message)
                .isEqualTo("날짜 형식이 올바르지 않습니다. yyyy-MM-dd 형식으로 입력해주세요.")
        }
    }

    @DisplayName("email 검증")
    @Nested
    inner class EmailValidation {

        @DisplayName("email이 비어있으면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun failWhenEmailIsBlank(email: String) {
            // arrange
            val request = createValidRequest(email = email)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.map { it.propertyPath.toString() }).contains("email")
        }

        @DisplayName("email이 올바른 형식이면 검증을 통과한다.")
        @ParameterizedTest
        @ValueSource(strings = ["test@example.com", "user.name@domain.co.kr", "user+tag@example.org"])
        fun successWhenEmailValid(validEmail: String) {
            // arrange
            val request = createValidRequest(email = validEmail)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations.filter { it.propertyPath.toString() == "email" }).isEmpty()
        }

        @DisplayName("email이 잘못된 형식이면 검증에 실패한다.")
        @ParameterizedTest
        @ValueSource(strings = ["notanemail", "@nodomain.com", "spaces in@email.com", "double@@at.com"])
        fun failWhenEmailFormatInvalid(invalidEmail: String) {
            // arrange
            val request = createValidRequest(email = invalidEmail)

            // act
            val violations = validator.validate(request)

            // assert
            assertThat(violations).isNotEmpty
            assertThat(violations.first { it.propertyPath.toString() == "email" }.message)
                .isEqualTo("이메일 형식이 올바르지 않습니다.")
        }
    }

    @DisplayName("모든 필드가 유효하면 검증을 통과한다.")
    @Test
    fun successWhenAllFieldsValid() {
        // arrange
        val request = createValidRequest()

        // act
        val violations = validator.validate(request)

        // assert
        assertThat(violations).isEmpty()
    }
}
