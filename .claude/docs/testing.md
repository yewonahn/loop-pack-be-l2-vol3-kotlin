# 테스트 가이드

## 테스트 피라미드

```
        /E2E\         적게 - TestRestTemplate, 전체 HTTP 흐름
       /─────\
      /Integra\       적당히 - @SpringBootTest + Testcontainers
     /  tion   \
    /───────────\
   /    Unit     \    많이 - 순수 JVM, Spring 없이
  /───────────────\
```

## 테스트 유형별 책임

| 유형 | 대상 | 환경 | 파일명 패턴 |
|------|------|------|------------|
| Unit | Domain Model, VO | 순수 JVM | `UserTest.kt` |
| Integration | Service + DB | @SpringBootTest + Testcontainers | `UserServiceIntegrationTest.kt` |
| E2E | Controller → DB | @SpringBootTest(RANDOM_PORT) | `UserV1ApiE2ETest.kt` |

## 테스트 코드 컨벤션

```kotlin
class UserTest {

    @DisplayName("유저 생성")  // 한글 컨텍스트
    @Nested
    inner class Create {

        @DisplayName("로그인ID, 비밀번호가 주어지면 성공한다.")
        @Test
        fun success() {
            // arrange
            val loginId = "testuser"

            // act
            val user = User.create(loginId = loginId, ...)

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(loginId) },
            )
        }

        @DisplayName("로그인ID에 특수문자가 포함되면 실패한다.")
        @Test
        fun failWhenLoginIdContainsSpecialCharacter() {
            // act & assert
            val exception = assertThrows<UserException> {
                User.create(loginId = "test_user!", ...)
            }
            assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_LOGIN_ID_FORMAT)
        }
    }
}
```

## 통합/E2E 테스트 설정

```kotlin
@SpringBootTest
class ExampleServiceIntegrationTest @Autowired constructor(
    private val exampleService: ExampleService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()  // 테스트 격리
    }
}
```

## 테스트 더블 사용 기준

| 역할 | 사용 시점 |
|------|----------|
| Stub | 고정된 응답만 필요할 때 |
| Mock | 호출 여부/횟수 검증 필요할 때 |
| Fake | 상태 관리가 필요할 때 (InMemoryRepository) |

## 실행 명령어

```bash
# 전체 테스트
./gradlew test

# 특정 테스트
./gradlew test --tests "*.UserTest"
./gradlew test --tests "*.UserV1ApiE2ETest"

# E2E만
./gradlew :apps:commerce-api:test --tests "*.E2ETest"
```
