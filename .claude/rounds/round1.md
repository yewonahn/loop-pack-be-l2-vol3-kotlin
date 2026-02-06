# Round 1: 사용자 인증 기능

**기간**: 1주차
**주제**: 회원가입, 내 정보 조회, 비밀번호 변경

---

## 1. 구현된 API

| API | Method | Path | 설명 |
|-----|--------|------|------|
| 회원가입 | POST | `/api/v1/users` | 신규 회원 등록 |
| 내 정보 조회 | GET | `/api/v1/users/me` | 인증된 사용자 정보 반환 |
| 비밀번호 변경 | PATCH | `/api/v1/users/me/password` | 현재 비밀번호 확인 후 변경 |

API 경로는 `ApiPaths` 상수로 관리 (`support/constant/ApiPaths.kt`).

---

## 2. 도메인 설계 결정

### 2.1 User Entity

```kotlin
// domain/user/User.kt
class User private constructor(...) : BaseEntity() {
    companion object {
        fun create(loginId, encodedPassword, name, birthDate, email): User
    }
}
```

**결정 사항**:
- `private constructor` + `companion object.create()` 패턴으로 생성 시점 검증 강제
- `encodedPassword`를 받음 (암호화는 Service 책임)
- 검증 순서: loginId → email → name → birthDate

### 2.2 Password Value Object

```kotlin
// domain/user/Password.kt
@Embeddable
class Password protected constructor(val value: String) {
    companion object {
        fun validate(rawPassword: String, birthDate: LocalDate)  // 검증만
        fun fromEncoded(encodedPassword: String): Password       // 생성만
    }
}
```

**결정 사항**:
- 검증(`validate`)과 생성(`fromEncoded`)을 분리
- `validate()`는 평문 비밀번호 규칙 검증
- `fromEncoded()`는 암호화된 값으로 Password 객체 생성
- 이유: Service에서 "검증 → 암호화 → 저장" 순서를 명시적으로 제어

### 2.3 비밀번호 규칙

| 규칙 | 구현 위치 | 에러 코드 |
|------|----------|----------|
| 8~16자 | `Password.validateLength()` | USER_005 |
| 영문/숫자/특수문자만 | `Password.validateFormat()` | USER_006 |
| 생년월일 포함 불가 | `Password.validateNotContainsBirthDate()` | USER_007 |

---

## 3. 인증 설계 결정

### 3.1 헤더 기반 인증

```
X-Loopers-LoginId: {로그인ID}
X-Loopers-LoginPw: {비밀번호}
```

**결정 사항**:
- Spring Security Filter 대신 `HandlerMethodArgumentResolver` 사용
- 이유: 현재 권한 체크 요구사항 없음, YAGNI 원칙 적용
- 추후 Spring Security 전환 비용 낮음 (리팩토링 1~2시간 예상)

### 3.2 ArgumentResolver 구현

```kotlin
// interfaces/api/auth/CurrentUserIdArgumentResolver.kt
@Component
class CurrentUserIdArgumentResolver(
    private val userAuthService: UserAuthService,
) : HandlerMethodArgumentResolver {
    override fun resolveArgument(...): Long {
        val loginId = webRequest.getHeader(HEADER_LOGIN_ID)
            ?: throw UserException.invalidCredentials()
        // ...
        return userAuthService.authenticateAndGetId(loginId, password)
    }
}
```

**결정 사항**:
- Entity가 아닌 **userId(Long)** 반환
- 이유: ArgumentResolver는 트랜잭션 밖에서 실행 → Detached Entity 방지
- Controller에서 `@CurrentUserId userId: Long`으로 사용

### 3.3 인증 실패 응답

```kotlin
// 모든 인증 실패는 동일한 에러로 응답
throw UserException.invalidCredentials()
// → USER_010: "아이디 또는 비밀번호가 올바르지 않습니다."
```

**결정 사항**:
- 아이디 존재 여부, 비밀번호 불일치를 구분하지 않음
- 이유: 보안 (계정 존재 여부 노출 방지)

---

## 4. 에러 코드 설계

### 4.1 UserErrorCode 목록

| 코드 | HTTP | 메시지 |
|------|------|--------|
| USER_001 | 400 | 로그인 ID는 영문과 숫자만 사용 가능합니다 |
| USER_002 | 400 | 이메일 형식이 올바르지 않습니다 |
| USER_003 | 400 | 이름은 2~20자여야 합니다 |
| USER_004 | 400 | 생년월일은 미래 날짜일 수 없습니다 |
| USER_005 | 400 | 비밀번호는 8~16자여야 합니다 |
| USER_006 | 400 | 비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다 |
| USER_007 | 400 | 비밀번호에 생년월일을 포함할 수 없습니다 |
| USER_008 | 409 | 이미 사용 중인 로그인 ID입니다 |
| USER_009 | 404 | 사용자를 찾을 수 없습니다 *(정의만 있음, 미사용)* |
| USER_010 | 401 | 아이디 또는 비밀번호가 올바르지 않습니다 |
| USER_011 | 400 | 현재 비밀번호가 올바르지 않습니다 |
| USER_012 | 400 | 새 비밀번호는 현재 비밀번호와 달라야 합니다 |
| USER_013 | 400 | 로그인 ID는 4~20자여야 합니다 |

### 4.2 예외 클래스 구조

```kotlin
// UserException - 팩토리 메서드 패턴
class UserException private constructor(...) : CoreException(...) {
    companion object {
        fun invalidCredentials() = UserException(AUTHENTICATION_FAILED)
        fun duplicateLoginId() = UserException(DUPLICATE_LOGIN_ID)
        // ...
    }
}
```

**결정 사항**:
- 팩토리 메서드로 예외 생성 중앙화
- IDE 자동완성으로 사용 가능한 예외 목록 확인 가능

---

## 5. DTO 검증 설계

### 5.1 이중 검증 전략

| 검증 위치 | 검증 내용 | 목적 |
|----------|----------|------|
| DTO (`@Valid`) | 필수값, 형식, 길이 | Fast-fail, HTTP 400 |
| Domain | 비즈니스 규칙 | 불변식 보장 |

**결정 사항**:
- DTO 검증 규칙은 Domain 규칙과 **동일하게 유지**
- 예: `password` - DTO에서 `@Size(min=8, max=16)`, Domain에서도 8~16자 검증
- 이유: DTO 검증 통과 후 Domain 검증에서 실패하면 혼란 야기

### 5.2 커스텀 Validator

```kotlin
// @ValidDateFormat - 날짜 형식 검증
@field:ValidDateFormat
val birthDate: String
```

**결정 사항**:
- `yyyy-MM-dd` 형식만 허용
- 파싱 실패 시 `MethodArgumentNotValidException` → COMMON_002

---

## 6. 응답 마스킹

### 6.1 마스킹 규칙

| 필드 | 규칙 | 예시 |
|------|------|------|
| name | 마지막 글자 `*` 처리 | 홍길동 → 홍길* |

**결정 사항**:
- 마스킹은 `UserFacade`에서 처리
- `MaskingUtils.maskLastCharacter()` 유틸 사용
- DTO의 `from()`은 단순 매핑만 (마스킹 로직 금지)

---

## 7. 테스트 구조

### 7.1 테스트 파일 목록

| 파일 | 유형 | 대상 |
|------|------|------|
| `domain/user/UserTest.kt` | Unit | User.create() 검증 |
| `domain/user/PasswordTest.kt` | Unit | Password.validate() 검증 |
| `domain/user/UserAuthServiceTest.kt` | Unit | 인증 로직 검증 |
| `domain/user/UserServiceIntegrationTest.kt` | Integration | Service + Repository 연동 |
| `interfaces/api/user/RegisterRequestValidationTest.kt` | Unit | DTO 검증 |
| `application/user/UserFacadeTest.kt` | Unit | Facade 마스킹 등 |
| `interfaces/api/UserV1ApiE2ETest.kt` | E2E | 전체 HTTP 플로우 |

### 7.2 E2E 테스트 특징

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(MySqlTestContainersConfig::class)
class UserV1ApiE2ETest {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()  // 테스트 격리
    }
}
```

**결정 사항**:
- Testcontainers로 실제 MySQL 사용
- 매 테스트 후 테이블 truncate로 격리
- 실패 케이스에서 **에러 코드 검증** 포함

---

## 8. 다음 라운드를 위한 메모

- [ ] Spring Security 전환 시점 검토 (권한 체크 요구사항 발생 시)
- [ ] 로그인 API 추가 시 세션/JWT 방식 결정 필요
- [ ] 이메일 중복 검증 요구사항 확인 필요
