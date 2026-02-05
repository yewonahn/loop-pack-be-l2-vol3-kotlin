package com.loopers.application.user

import com.loopers.domain.user.Password
import com.loopers.domain.user.User
import com.loopers.domain.user.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class UserFacadeTest {

    @Mock
    private lateinit var userService: UserService

    @InjectMocks
    private lateinit var userFacade: UserFacade

    @DisplayName("회원가입")
    @Nested
    inner class Register {

        @DisplayName("회원가입 성공 시, 이름이 마스킹된 UserInfo를 반환한다.")
        @Test
        fun success() {
            // arrange
            val loginId = "testuser"
            val rawPassword = "Test123!"
            val name = "홍길동"
            val birthDate = LocalDate.of(1990, 1, 1)
            val email = "test@example.com"

            val savedUser = createUser(
                loginId = loginId,
                name = name,
                birthDate = birthDate,
                email = email,
            )
            whenever(userService.register(any(), any(), any(), any(), any())).thenReturn(savedUser)

            // act
            val result = userFacade.register(
                loginId = loginId,
                rawPassword = rawPassword,
                name = name,
                birthDate = birthDate,
                email = email,
            )

            // assert
            assertAll(
                { assertThat(result.loginId).isEqualTo(loginId) },
                { assertThat(result.name).isEqualTo("홍길*") },
                { assertThat(result.birthDate).isEqualTo(birthDate) },
                { assertThat(result.email).isEqualTo(email) },
            )
        }

        @DisplayName("이름이 1글자면 전체가 마스킹된다.")
        @Test
        fun maskSingleCharacterName() {
            // arrange
            val savedUser = createUser(name = "김")
            whenever(userService.register(any(), any(), any(), any(), any())).thenReturn(savedUser)

            // act
            val result = userFacade.register(
                loginId = "testuser",
                rawPassword = "Test123!",
                name = "김",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            // assert
            assertThat(result.name).isEqualTo("*")
        }

        @DisplayName("이름이 2글자면 마지막 글자만 마스킹된다.")
        @Test
        fun maskTwoCharacterName() {
            // arrange
            val savedUser = createUser(name = "이름")
            whenever(userService.register(any(), any(), any(), any(), any())).thenReturn(savedUser)

            // act
            val result = userFacade.register(
                loginId = "testuser",
                rawPassword = "Test123!",
                name = "이름",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "test@example.com",
            )

            // assert
            assertThat(result.name).isEqualTo("이*")
        }
    }

    private fun createUser(
        loginId: String = "testuser",
        name: String = "홍길동",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "test@example.com",
    ): User {
        val constructor = User::class.java.getDeclaredConstructor(
            String::class.java,
            Password::class.java,
            String::class.java,
            LocalDate::class.java,
            String::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            loginId,
            Password.fromEncoded("encodedPassword"),
            name,
            birthDate,
            email,
        )
    }
}
