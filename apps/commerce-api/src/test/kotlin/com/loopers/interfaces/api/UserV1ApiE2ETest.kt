package com.loopers.interfaces.api

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.support.constant.ApiPaths
import com.loopers.support.error.CommonErrorCode
import com.loopers.support.error.UserErrorCode
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/users - 회원가입")
    @Nested
    inner class Register {

        @DisplayName("정상적인 정보로 회원가입하면, 201 CREATED와 회원 정보를 반환한다.")
        @Test
        fun success() {
            // arrange
            val request = UserV1Dto.RegisterRequest(
                loginId = "testuser",
                password = "Test123!",
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "test@example.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                ApiPaths.Users.REGISTER,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.loginId).isEqualTo("testuser") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.email).isEqualTo("test@example.com") },
            )

            // DB 저장 확인
            val savedUser = userJpaRepository.findByLoginId("testuser")
            assertThat(savedUser).isNotNull
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, 409 CONFLICT와 USER_008 에러를 반환한다.")
        @Test
        fun failWhenDuplicateLoginId() {
            // arrange - 먼저 회원가입
            val firstRequest = UserV1Dto.RegisterRequest(
                loginId = "existinguser",
                password = "Test123!",
                name = "기존회원",
                birthDate = "1990-01-01",
                email = "existing@example.com",
            )
            testRestTemplate.postForEntity(ApiPaths.Users.REGISTER, firstRequest, Any::class.java)

            // 같은 로그인 ID로 다시 가입 시도
            val duplicateRequest = UserV1Dto.RegisterRequest(
                loginId = "existinguser",
                password = "Test456!",
                name = "신규회원",
                birthDate = "1995-05-05",
                email = "new@example.com",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                ApiPaths.Users.REGISTER,
                HttpMethod.POST,
                HttpEntity(duplicateRequest),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.DUPLICATE_LOGIN_ID.code) },
            )
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 BAD_REQUEST와 COMMON_002 에러를 반환한다.")
        @Test
        fun failWhenPasswordTooShort() {
            // arrange
            val request = UserV1Dto.RegisterRequest(
                loginId = "testuser",
                password = "Test12!",
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "test@example.com",
            )

            // act
            val response = testRestTemplate.postForEntity(
                ApiPaths.Users.REGISTER,
                request,
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE.code) },
                { assertThat(response.body?.meta?.fieldErrors).containsKey("password") },
            )
        }

        @DisplayName("생년월일 형식이 잘못되면, 400 BAD_REQUEST와 COMMON_002 에러를 반환한다.")
        @Test
        fun failWhenBirthDateFormatInvalid() {
            // arrange
            val request = UserV1Dto.RegisterRequest(
                loginId = "testuser",
                password = "Test123!",
                name = "홍길동",
                birthDate = "20000930",
                email = "test@example.com",
            )

            // act
            val response = testRestTemplate.postForEntity(
                ApiPaths.Users.REGISTER,
                request,
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE.code) },
                { assertThat(response.body?.meta?.fieldErrors).containsKey("birthDate") },
            )
        }

        @DisplayName("이메일 형식이 잘못되면, 400 BAD_REQUEST와 COMMON_002 에러를 반환한다.")
        @Test
        fun failWhenEmailFormatInvalid() {
            // arrange
            val request = UserV1Dto.RegisterRequest(
                loginId = "testuser",
                password = "Test123!",
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "invalid-email",
            )

            // act
            val response = testRestTemplate.postForEntity(
                ApiPaths.Users.REGISTER,
                request,
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE.code) },
                { assertThat(response.body?.meta?.fieldErrors).containsKey("email") },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me - 내 정보 조회")
    @Nested
    inner class GetMe {

        @DisplayName("정상 인증 시 200 OK와 마스킹된 사용자 정보를 반환한다.")
        @Test
        fun success() {
            // arrange - 회원가입
            val loginId = "testuser"
            val password = "Test123!"
            val registerRequest = UserV1Dto.RegisterRequest(
                loginId = loginId,
                password = password,
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "test@example.com",
            )
            testRestTemplate.postForEntity(ApiPaths.Users.REGISTER, registerRequest, Any::class.java)

            // act - 내 정보 조회
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, password)
            }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.loginId).isEqualTo("testuser") },
                { assertThat(response.body?.data?.name).isEqualTo("홍길*") },
                { assertThat(response.body?.data?.birthDate).isEqualTo("1990-01-01") },
                { assertThat(response.body?.data?.email).isEqualTo("test@example.com") },
            )
        }

        @DisplayName("인증 헤더 누락 시 401 UNAUTHORIZED와 USER_010 에러를 반환한다.")
        @Test
        fun failWhenHeaderMissing() {
            // act - 헤더 없이 요청
            val response = testRestTemplate.getForEntity(ApiPaths.Users.ME, ApiResponse::class.java)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED.code) },
            )
        }

        @DisplayName("잘못된 비밀번호 시 401 UNAUTHORIZED와 USER_010 에러를 반환한다.")
        @Test
        fun failWhenPasswordNotMatched() {
            // arrange - 회원가입
            val loginId = "testuser"
            val password = "Test123!"
            val registerRequest = UserV1Dto.RegisterRequest(
                loginId = loginId,
                password = password,
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "test@example.com",
            )
            testRestTemplate.postForEntity(ApiPaths.Users.REGISTER, registerRequest, Any::class.java)

            // act - 잘못된 비밀번호로 조회
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, "WrongPassword!")
            }
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED.code) },
            )
        }

        @DisplayName("존재하지 않는 사용자 시 401 UNAUTHORIZED와 USER_010 에러를 반환한다.")
        @Test
        fun failWhenUserNotFound() {
            // act - 존재하지 않는 사용자로 조회
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, "nonexistent")
                set(HEADER_LOGIN_PW, "Test123!")
            }
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED.code) },
            )
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password - 비밀번호 변경")
    @Nested
    inner class ChangePassword {

        @DisplayName("현재 비밀번호가 맞고 새 비밀번호가 규칙을 만족하면 200 OK를 반환한다.")
        @Test
        fun success() {
            // arrange - 회원가입
            val loginId = "testuser"
            val currentPassword = "OldPass123!"
            val newPassword = "NewPass456!"
            registerUser(loginId, currentPassword)

            // act - 비밀번호 변경
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, currentPassword)
            }
            val request = UserV1Dto.ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = newPassword,
            )
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(request, headers),
                ApiResponse::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

            // 변경된 비밀번호로 인증 가능한지 확인
            val newHeaders = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, newPassword)
            }
            val meResponse = testRestTemplate.exchange(
                ApiPaths.Users.ME,
                HttpMethod.GET,
                HttpEntity<Void>(newHeaders),
                ApiResponse::class.java,
            )
            assertThat(meResponse.statusCode).isEqualTo(HttpStatus.OK)
        }

        @DisplayName("인증 헤더 누락 시 401 UNAUTHORIZED와 USER_010 에러를 반환한다.")
        @Test
        fun failWhenHeaderMissing() {
            // act - 헤더 없이 요청
            val request = UserV1Dto.ChangePasswordRequest(
                currentPassword = "OldPass123!",
                newPassword = "NewPass456!",
            )
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(request),
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED.code) },
            )
        }

        @DisplayName("현재 비밀번호가 틀리면 400 BAD_REQUEST와 USER_011 에러를 반환한다.")
        @Test
        fun failWhenCurrentPasswordIsWrong() {
            // arrange - 회원가입
            val loginId = "testuser"
            val currentPassword = "Correct123!"
            registerUser(loginId, currentPassword)

            // act - 잘못된 현재 비밀번호로 변경 시도
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, currentPassword)
            }
            val request = UserV1Dto.ChangePasswordRequest(
                currentPassword = "WrongPass123!",
                newPassword = "NewPass456!",
            )
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(request, headers),
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.INVALID_CURRENT_PASSWORD.code) },
            )
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 400 BAD_REQUEST와 USER_012 에러를 반환한다.")
        @Test
        fun failWhenNewPasswordIsSameAsCurrent() {
            // arrange - 회원가입
            val loginId = "testuser"
            val currentPassword = "SamePass123!"
            registerUser(loginId, currentPassword)

            // act - 같은 비밀번호로 변경 시도
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, currentPassword)
            }
            val request = UserV1Dto.ChangePasswordRequest(
                currentPassword = currentPassword,
                newPassword = currentPassword,
            )
            val response = testRestTemplate.exchange(
                ApiPaths.Users.ME_PASSWORD,
                HttpMethod.PATCH,
                HttpEntity(request, headers),
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.SAME_PASSWORD.code) },
            )
        }

        private fun registerUser(loginId: String, password: String) {
            val registerRequest = UserV1Dto.RegisterRequest(
                loginId = loginId,
                password = password,
                name = "홍길동",
                birthDate = "1990-01-01",
                email = "test@example.com",
            )
            testRestTemplate.postForEntity(ApiPaths.Users.REGISTER, registerRequest, Any::class.java)
        }
    }
}
