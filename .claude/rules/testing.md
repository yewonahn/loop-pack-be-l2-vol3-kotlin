---
paths:
  - "apps/commerce-api/src/test/**/*.kt"
---

# 테스트 규칙

## TDD 진행 방식

**기본 사이클**: Red → Green → Refactor

**흐름** (OUT TO IN + Inside-Out 병행):
1. **Domain Unit Test** - 규칙 먼저 고정 (Password, Email, BirthDate 등)
2. **Controller Test** - 요청/응답 계약 정의
3. **Service Test** - 중재/조합 로직
4. **E2E Test** - HTTP 플로우

**새 기능 구현 시 제안 순서**:
1. Domain Unit Test 목록
2. Controller Test 시나리오
3. Service Test 시나리오
4. E2E Test 시나리오 (Given/When/Then)

## 테스트 피라미드 역할

| 레벨 | 대상 | 특징 |
|------|------|------|
| Unit | 도메인 규칙, 값 객체, 순수 로직 | 외부 의존 없음, 빠름 |
| Integration | Service + Repository, 트랜잭션 | DB 연동, Testcontainers |
| E2E | HTTP 레벨 시나리오 | 전체 흐름 검증 |

테스트 작성 시 각 케이스가 어느 레벨에 있어야 적절한지 함께 설명.

## Mock 사용 기준

**원칙**: 격리 목적이 명확할 때만 사용

| 테스트 유형 | Mock 사용 |
|------------|----------|
| Domain Unit | ❌ 사용하지 않음 |
| Service Integration | ⭕ 외부 의존성 격리 시 |
| E2E | ❌ 사용하지 않음 |

- Service 테스트에서 Repository 등 외부 의존성 격리할 때 주로 사용
- Domain 테스트는 순수 로직이므로 Mock 불필요
- E2E는 실제 흐름 검증이므로 Mock 사용 X

## 단언문 규칙

**원칙**: 테스트당 **핵심 단언문 1개**

- 테스트의 "목적"과 직접 관련된 것만 검증
- 부수적인 assert로 유지보수성 떨어뜨리지 않음
- 각 테스트가 무엇을 검증하는지 한 문장으로 설명 가능해야 함

```kotlin
// ✅ 좋음 - 핵심만 검증
@DisplayName("로그인ID에 특수문자가 포함되면 INVALID_LOGIN_ID_FORMAT 에러가 발생한다")
@Test
fun failWhenLoginIdContainsSpecialCharacter() {
    val exception = assertThrows<UserException> {
        User.create(loginId = "test!", ...)
    }
    assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_LOGIN_ID_FORMAT)
}

// ❌ 나쁨 - 관련 없는 검증 포함
@Test
fun failWhenLoginIdContainsSpecialCharacter() {
    val exception = assertThrows<UserException> { ... }
    assertThat(exception.errorCode).isEqualTo(...)
    assertThat(exception.message).isNotBlank()  // 불필요
    assertThat(exception.cause).isNull()        // 불필요
}
```

## 파일명 패턴

| 유형 | 패턴 | 예시 |
|------|------|------|
| 단위 | `{Class}Test.kt` | `UserTest.kt` |
| 통합 | `{Class}IntegrationTest.kt` | `UserServiceIntegrationTest.kt` |
| E2E | `{Api}E2ETest.kt` | `UserV1ApiE2ETest.kt` |

## 테스트 구조

```kotlin
class UserTest {

    @DisplayName("유저 생성")
    @Nested
    inner class Create {

        @DisplayName("정상 입력이면 성공한다")
        @Test
        fun success() {
            // arrange
            // act
            // assert (핵심 1개)
        }
    }
}
```

## 테스트 격리

- `@AfterEach`에서 `databaseCleanUp.truncateAllTables()`
- Testcontainers 사용

## 테스트 더블 사용 기준

| 종류 | 사용 시점 |
|------|----------|
| Dummy | 필요 없는 인자 채우기만 |
| Stub | 고정된 응답 필요 (상태 기반 검증) |
| Mock | 호출 여부/횟수 검증 (행위 기반 검증) |
| Fake | 인메모리 구현, 실제와 비슷하지만 가벼운 대역 |

도메인 로직은 가능하면 Fake/Stub로 빠르고 안정적으로 검증.
