---
paths:
  - "apps/commerce-api/src/main/kotlin/**/interfaces/api/**/*.kt"
---

# API Layer 규칙

## Controller

- Facade만 호출 (Service 직접 호출 금지)
- `@Valid`로 입력 검증
- `ApiPaths` 상수 사용
- `ApiResponse.success()`로 응답 래핑

```kotlin
@RestController
@RequestMapping(ApiPaths.Users.BASE)
class UserV1Controller(
    private val userFacade: UserFacade,  // Facade만
) {
    @PostMapping
    fun register(@Valid @RequestBody request: RegisterRequest): ApiResponse<UserResponse> {
        val userInfo = userFacade.register(...)
        return ApiResponse.success(UserResponse.from(userInfo))
    }
}
```

## DTO

- `from()` 메서드는 **단순 매핑만** (변환 로직 금지)
- 검증 어노테이션은 Domain 규칙과 일치

```kotlin
// ✅ 좋음
fun from(info: UserInfo) = UserResponse(
    name = info.name,  // 이미 Facade에서 마스킹됨
)

// ❌ 나쁨
fun from(info: UserInfo) = UserResponse(
    name = MaskingUtils.maskLastCharacter(info.name),  // DTO에서 변환 금지
)
```

## 인증

- `@CurrentUserId`로 userId(Long) 받음
- Entity가 아닌 ID 전달 (Detached Entity 방지)
