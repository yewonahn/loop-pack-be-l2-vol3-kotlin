# Kotlin 코드 스타일

## 네이밍

- **클래스**: PascalCase (`UserService`, `OrderRepository`)
- **함수/변수**: camelCase (`findById`, `loginId`)
- **상수**: SCREAMING_SNAKE_CASE (`MAX_RETRY_COUNT`)
- **패키지**: lowercase (`com.loopers.domain.user`)

## 메서드 네이밍 컨벤션

| 패턴 | 의미 | 반환 |
|------|------|------|
| `getXxx()` | 단건 조회 | 없으면 예외 |
| `findXxx()` | 단건 조회 | 없으면 null |
| `findAllXxx()` | 복수 조회 | List |
| `existsByXxx()` | 존재 여부 | Boolean |
| `register()` | 생성 | Entity |
| `createXxx()` | 생성 | Entity |

## Null Safety

- `!!` 사용 금지 → `requireNotNull()` 또는 `?: throw`
- nullable 타입 명시적 처리 필수

## Data Class

```kotlin
// ✅ 좋음
data class UserInfo(
    val loginId: String,
    val name: String,
)

// ❌ 나쁨 - Entity는 data class 아님
data class User(...)
```

## Companion Object

팩토리 메서드는 companion object에:

```kotlin
class User private constructor(...) {
    companion object {
        fun create(...): User { ... }
    }
}
```
