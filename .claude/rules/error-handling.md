# 예외 처리 가이드

## 아키텍처 개요

```
ApiControllerAdvice (전역 핸들러)
        ▲
ApiResponse (일관된 응답 포맷)
        ▲
CoreException ← UserException, OrderException, ...
        ▲
ErrorCode (CommonErrorCode, UserErrorCode, ...)
```

## ErrorCode 정의

**네이밍:** `{도메인}_{에러유형}` (예: `USER_NOT_FOUND`, `DUPLICATE_LOGIN_ID`)

**코드 분리 기준:**
| 상황 | 결정 |
|------|------|
| 클라이언트 분기 필요 없음 | 코드 통합, 메시지로 구분 |
| 클라이언트가 다른 처리 필요 | 코드 분리 |
| 모니터링/알람 집계 필요 | 코드 분리 |

## 도메인 예외 클래스

**팩토리 메서드 패턴 사용** - 참조: `support/error/UserException.kt`

```kotlin
class UserException private constructor(
    errorCode: UserErrorCode,
    message: String = errorCode.message,  // 기본값 사용
    cause: Throwable? = null,
) : CoreException(errorCode, message, cause) {

    companion object {
        fun invalidCredentials() = UserException(UserErrorCode.AUTHENTICATION_FAILED)
        fun duplicateLoginId() = UserException(UserErrorCode.DUPLICATE_LOGIN_ID)
        fun invalidCurrentPassword() = UserException(UserErrorCode.INVALID_CURRENT_PASSWORD)
        fun samePassword() = UserException(UserErrorCode.SAME_PASSWORD)
        // ... 등
    }
}
```

## 예외 발생 위치

| 위치 | 예외 종류 | 예시 |
|------|----------|------|
| Domain Model | 도메인 규칙 위반 | 로그인ID 형식 오류 |
| Service | 비즈니스 규칙 위반 | 중복 로그인ID |
| ArgumentResolver | 인증 실패 | 헤더 누락, 잘못된 비밀번호 |

## 보안 원칙

**인증 실패 시 상세 원인을 노출하지 않음:**
```kotlin
// ✅ 좋음 - 동일한 에러
throw UserException.invalidCredentials()  // 사용자 없음
throw UserException.invalidCredentials()  // 비밀번호 틀림

// ❌ 나쁨 - 사용자 존재 여부 노출
throw UserException.userNotFound()    // 아이디가 없음을 알려줌
throw UserException.wrongPassword()   // 아이디는 맞다는 것을 알려줌
```

## 테스트에서 예외 검증

> 참조: `domain/user/UserTest.kt`

```kotlin
val exception = assertThrows<UserException> {
    User.create(loginId = "test!", ...)  // 특수문자 포함
}
assertThat(exception.errorCode).isEqualTo(UserErrorCode.INVALID_LOGIN_ID_FORMAT)
```

## E2E 테스트에서 에러 응답 검증

```kotlin
assertAll(
    { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
    { assertThat(response.body?.meta?.errorCode).isEqualTo(UserErrorCode.DUPLICATE_LOGIN_ID.code) },
)
```
