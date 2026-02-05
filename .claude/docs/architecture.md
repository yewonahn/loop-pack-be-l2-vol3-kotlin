# 아키텍처 가이드

## 4계층 레이어 구조

```
interfaces/api/  →  application/  →  domain/  →  infrastructure/
(Controller)        (Facade)         (Service)    (Repository)
```

| 계층 | 컴포넌트 | 책임 | 어노테이션 |
|------|---------|------|-----------|
| Interface | Controller, DTO, ApiSpec | HTTP 요청/응답 변환, 입력 검증 | @RestController |
| Application | Facade, Info DTO | 유스케이스 조율, 응답 데이터 준비 | @Component |
| Domain | Service, Repository(I), Entity | 핵심 비즈니스 로직, 트랜잭션 경계 | @Component, @Transactional |
| Infrastructure | RepositoryImpl, JpaRepository | 외부 시스템 연동 (DB 등) | @Repository |

## 의존성 규칙

- 상위 계층 → 하위 계층 방향으로만 의존
- Domain Layer는 Infrastructure를 알지 못함 (인터페이스로 추상화)
- 비즈니스 로직은 반드시 Domain Layer에 위치

## 패키지 구조 원칙

**기능 기반(Feature-based) 구조 사용:**

```
domain/
├── user/           # User 도메인 (모든 관련 파일)
│   ├── User.kt
│   ├── UserRepository.kt
│   └── UserService.kt
└── order/          # Order 도메인
    └── ...
```

**폴더 분리 기준:**
- 파일 10개 미만: 분리 불필요
- 파일 10개 이상: **하위 도메인 단위**로 분리 (entity/service/repository 분리 아님)

## 트랜잭션과 Entity 전달 패턴

**문제: Detached Entity**
```kotlin
// ❌ 잘못된 패턴: Controller에서 받은 Entity를 Service로 전달
@GetMapping("/me")
fun getMe(@CurrentUser user: User) {  // user는 Detached 상태
    userService.updateUser(user)       // 트랜잭션 밖에서 조회된 Entity
}
```

**해결: ID 전달**
```kotlin
// ✅ 올바른 패턴: ID를 전달하고 Service에서 조회
@GetMapping("/me")
fun getMe(@CurrentUserId userId: Long) {
    userService.updateUser(userId)  // Service가 트랜잭션 내에서 조회
}
```

**이유:**
- ArgumentResolver는 트랜잭션 밖에서 실행됨
- Service의 @Transactional 시작 시 새로운 Persistence Context 생성
- Detached Entity는 변경 감지(Dirty Checking) 불가

## 인증 패턴 (@CurrentUserId)

> 참조: `interfaces/api/auth/CurrentUserIdArgumentResolver.kt`, `interfaces/api/user/UserV1Controller.kt:39`

```kotlin
// ArgumentResolver: userId만 반환 (Entity 아님)
override fun resolveArgument(...): Long {
    val loginId = webRequest.getHeader(HEADER_LOGIN_ID)
        ?: throw UserException.invalidCredentials()
    val password = webRequest.getHeader(HEADER_LOGIN_PW)
        ?: throw UserException.invalidCredentials()
    return userAuthService.authenticateAndGetId(loginId, password)
}

// Controller: userId 사용
@GetMapping("/me")
fun getMe(@CurrentUserId userId: Long): ApiResponse<UserV1Dto.UserResponse> {
    val userInfo = userFacade.getMyInfo(userId)
    return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo))
}

// Facade: 트랜잭션 내에서 조회
@Transactional(readOnly = true)
fun getMyInfo(userId: Long): UserInfo {
    val user = userRepository.findById(userId)
        ?: throw UserException.invalidCredentials()
    return toUserInfo(user)
}
```

## Utils 활용 패턴

> 참조: `support/utils/MaskingUtils.kt`, `application/user/UserFacade.kt:45`

**재사용 가능한 순수 함수 → support/utils/에 위치**

```kotlin
// support/utils/MaskingUtils.kt
object MaskingUtils {
    fun maskLastCharacter(value: String): String {
        if (value.length <= 1) return "*"
        return value.dropLast(1) + "*"
    }
}

// Facade에서 사용 (application/user/UserFacade.kt:45)
private fun toUserInfo(user: User): UserInfo {
    return UserInfo(
        loginId = user.loginId,
        name = MaskingUtils.maskLastCharacter(user.name),
        birthDate = user.birthDate,
        email = user.email,
    )
}
```

**DTO의 from()은 단순 매핑만:**
```kotlin
// ✅ 단순 매핑
fun from(info: UserInfo) = UserResponse(
    name = info.name,  // 이미 마스킹됨
    ...
)

// ❌ 변환 로직 포함 금지
fun from(info: UserInfo) = UserResponse(
    name = maskLastCharacter(info.name),  // DTO에서 변환하지 않음
    ...
)
```
