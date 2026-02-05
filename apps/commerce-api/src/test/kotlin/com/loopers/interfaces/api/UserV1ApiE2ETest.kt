package com.loopers.interfaces.api

import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.user.UserV1Dto
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
        private const val REGISTER_ENDPOINT = "/api/v1/users"
        private const val ME_ENDPOINT = "/api/v1/users/me"
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
                REGISTER_ENDPOINT,
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

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, 409 CONFLICT를 반환한다.")
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
            testRestTemplate.postForEntity(REGISTER_ENDPOINT, firstRequest, Any::class.java)

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
                REGISTER_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(duplicateRequest),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 BAD_REQUEST를 반환한다.")
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
                REGISTER_ENDPOINT,
                request,
                ApiResponse::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("생년월일 형식이 잘못되면, 400 BAD_REQUEST를 반환한다.")
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
                REGISTER_ENDPOINT,
                request,
                ApiResponse::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이메일 형식이 잘못되면, 400 BAD_REQUEST를 반환한다.")
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
                REGISTER_ENDPOINT,
                request,
                ApiResponse::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
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
            testRestTemplate.postForEntity(REGISTER_ENDPOINT, registerRequest, Any::class.java)

            // act - 내 정보 조회
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, password)
            }
            val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            val response = testRestTemplate.exchange(
                ME_ENDPOINT,
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

        @DisplayName("인증 헤더 누락 시 401 UNAUTHORIZED를 반환한다.")
        @Test
        fun failWhenHeaderMissing() {
            // act - 헤더 없이 요청
            val response = testRestTemplate.getForEntity(ME_ENDPOINT, ApiResponse::class.java)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("잘못된 비밀번호 시 401 UNAUTHORIZED를 반환한다.")
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
            testRestTemplate.postForEntity(REGISTER_ENDPOINT, registerRequest, Any::class.java)

            // act - 잘못된 비밀번호로 조회
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, loginId)
                set(HEADER_LOGIN_PW, "WrongPassword!")
            }
            val response = testRestTemplate.exchange(
                ME_ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                ApiResponse::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 사용자 시 401 UNAUTHORIZED를 반환한다.")
        @Test
        fun failWhenUserNotFound() {
            // act - 존재하지 않는 사용자로 조회
            val headers = HttpHeaders().apply {
                set(HEADER_LOGIN_ID, "nonexistent")
                set(HEADER_LOGIN_PW, "Test123!")
            }
            val response = testRestTemplate.exchange(
                ME_ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                ApiResponse::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
