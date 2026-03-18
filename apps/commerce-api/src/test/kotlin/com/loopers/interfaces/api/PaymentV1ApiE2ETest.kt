package com.loopers.interfaces.api

import com.loopers.application.payment.port.PgPaymentClient
import com.loopers.application.payment.port.PgPaymentResponse
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Money
import com.loopers.domain.payment.PaymentStatus
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.interfaces.api.payment.PaymentCallbackRequest
import com.loopers.interfaces.api.payment.PaymentRequest
import com.loopers.interfaces.api.payment.PaymentResponse
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.support.constant.ApiPaths
import com.loopers.support.error.PaymentErrorCode
import com.loopers.support.error.UserErrorCode
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val orderRepository: OrderRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgPaymentClient: PgPaymentClient

    companion object {
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
        private const val LOGIN_ID = "testuser"
        private const val PASSWORD = "Test123!"
    }

    @BeforeEach
    fun setUp() {
        val request = UserV1Dto.RegisterRequest(
            loginId = LOGIN_ID,
            password = PASSWORD,
            name = "홍길동",
            birthDate = "1990-01-01",
            email = "test@example.com",
        )
        testRestTemplate.postForEntity(ApiPaths.Users.REGISTER, request, Any::class.java)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun authHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set(HEADER_LOGIN_ID, LOGIN_ID)
            set(HEADER_LOGIN_PW, PASSWORD)
        }
    }

    private fun createOrder(): Order {
        return orderRepository.save(Order.create(userId = 1L, totalAmount = Money(50000)))
    }

    @DisplayName("POST /api/v1/payments - 결제 요청")
    @Nested
    inner class RequestPayment {

        @DisplayName("정상 요청이면 201 CREATED와 결제 정보를 반환한다.")
        @Test
        fun success() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
                transactionId = "txn_e2e_test",
                orderId = "test",
                status = "REQUESTED",
                message = null,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                ApiPaths.Payments.BASE,
                HttpMethod.POST,
                HttpEntity(
                    PaymentRequest(orderId = order.id, cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456"),
                    authHeaders(),
                ),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(response.body?.data?.transactionId).isEqualTo("txn_e2e_test") },
                { assertThat(response.body?.data?.amount).isEqualTo(50000) },
            )
        }

        @DisplayName("인증 없이 결제 요청하면 401 UNAUTHORIZED를 반환한다.")
        @Test
        fun failWhenUnauthenticated() {
            // act
            val response = testRestTemplate.postForEntity(
                ApiPaths.Payments.BASE,
                PaymentRequest(orderId = 1L, cardType = CardType.SAMSUNG, cardNo = "1234"),
                ApiResponse::class.java,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.AUTHENTICATION_FAILED.code) },
            )
        }

        @DisplayName("존재하지 않는 주문이면 404 NOT_FOUND를 반환한다.")
        @Test
        fun failWhenOrderNotFound() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                ApiPaths.Payments.BASE,
                HttpMethod.POST,
                HttpEntity(
                    PaymentRequest(orderId = 999L, cardType = CardType.SAMSUNG, cardNo = "1234"),
                    authHeaders(),
                ),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo(PaymentErrorCode.ORDER_NOT_FOUND.code) },
            )
        }
    }

    @DisplayName("POST /api/v1/payments/callback - 콜백 → 상태 변경 전체 흐름")
    @Nested
    inner class CallbackFlow {

        @DisplayName("결제 요청 후 성공 콜백을 수신하면 APPROVED가 된다.")
        @Test
        fun fullFlow() {
            // arrange - 결제 요청
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
                transactionId = "txn_flow_test",
                orderId = "test",
                status = "REQUESTED",
                message = null,
            )
            testRestTemplate.exchange(
                ApiPaths.Payments.BASE,
                HttpMethod.POST,
                HttpEntity(
                    PaymentRequest(orderId = order.id, cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456"),
                    authHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {},
            )

            // act - 콜백 수신 (인증 불필요)
            val callbackResponse = testRestTemplate.postForEntity(
                ApiPaths.Payments.CALLBACK,
                PaymentCallbackRequest(transactionId = "txn_flow_test", status = "SUCCESS", reason = null),
                ApiResponse::class.java,
            )

            // assert
            assertThat(callbackResponse.statusCode).isEqualTo(HttpStatus.OK)

            // 상태 조회로 APPROVED 확인
            val payment = paymentJpaRepository.findByTransactionId("txn_flow_test")
            assertThat(payment?.status).isEqualTo(PaymentStatus.APPROVED)
        }
    }

    @DisplayName("GET /api/v1/payments/{paymentId} - 결제 상태 조회")
    @Nested
    inner class GetPayment {

        @DisplayName("존재하는 결제를 조회하면 200 OK와 결제 정보를 반환한다.")
        @Test
        fun success() {
            // arrange
            val order = createOrder()
            every { pgPaymentClient.requestPayment(any()) } returns PgPaymentResponse(
                transactionId = "txn_get_test",
                orderId = "test",
                status = "REQUESTED",
                message = null,
            )
            testRestTemplate.exchange(
                ApiPaths.Payments.BASE,
                HttpMethod.POST,
                HttpEntity(
                    PaymentRequest(orderId = order.id, cardType = CardType.SAMSUNG, cardNo = "1234-5678-9012-3456"),
                    authHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {},
            )
            val paymentId = paymentJpaRepository.findByTransactionId("txn_get_test")!!.id

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/payments/$paymentId",
                HttpMethod.GET,
                null,
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.transactionId).isEqualTo("txn_get_test") },
            )
        }
    }
}
