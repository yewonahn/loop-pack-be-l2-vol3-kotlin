---
paths:
  - "apps/commerce-api/src/main/kotlin/**/domain/**/*.kt"
---

# Domain Layer 규칙

## Entity

- `private constructor` + `companion object { fun create() }`
- 생성 시 유효성 검증
- 불변 필드는 `val`, 가변 필드는 `var ... protected set`

```kotlin
@Entity
class User private constructor(
    loginId: String,
    ...
) : BaseEntity() {

    @Column(nullable = false)
    var loginId: String = loginId
        protected set

    companion object {
        fun create(loginId: String, ...): User {
            validateLoginId(loginId)
            return User(loginId, ...)
        }
    }
}
```

## Service

- `@Component` + `@Transactional`
- Repository 인터페이스만 의존 (구현체 X)
- 비즈니스 규칙 검증

```kotlin
@Component
class UserService(
    private val userRepository: UserRepository,  // 인터페이스
) {
    @Transactional
    fun register(...): User {
        if (userRepository.existsByLoginId(loginId)) {
            throw UserException.duplicateLoginId()
        }
        // ...
    }
}
```

## Repository Interface

- Domain Layer에 위치
- 구현은 Infrastructure Layer

```kotlin
// domain/user/UserRepository.kt
interface UserRepository {
    fun findById(id: Long): User?
    fun save(user: User): User
}
```

## 예외

- `UserException.xxx()` 팩토리 메서드 사용
- 인증 실패는 항상 `invalidCredentials()` (정보 노출 방지)
