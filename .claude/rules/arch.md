# Architecture Rules

## 레이어 간 의존성

```
Controller → Facade → Service → Domain ← Repository
```

- 상위 → 하위 방향으로만 의존
- Controller는 **Facade만** 호출 (Service 직접 호출 금지)
- Domain 계층은 Spring, JPA, 웹 프레임워크에 직접 의존하지 않음
- Service에서는 도메인 메서드 호출 (비즈니스 규칙을 if/else로 풀어쓰지 않음)

## 각 레이어 책임

### Controller (interfaces/api)

- HTTP 요청/응답 매핑만
- 비즈니스 규칙 넣지 않음
- DTO ↔ Command/Response 변환
- HTTP 상태코드 결정
- `@Valid`로 입력 검증

### Service/Facade (application, domain)

- 도메인 객체를 사용해 유스케이스 조합
- 트랜잭션 경계 관리
- Facade: 응답 데이터 준비 (마스킹 등)

### Domain (domain)

- 핵심 비즈니스 규칙
- 비밀번호 규칙, 생년월일 검증, ID/이메일 포맷 등
- Entity는 private constructor + companion object factory
- Repository 인터페이스 정의

### Repository/Infrastructure (infrastructure)

- JPA 등 영속성 기술 구현
- 도메인/서비스는 구현 세부사항 모름
- `@Repository` 어노테이션 사용

## DTO / Command / Response

- Controller에서 Request DTO를 받아 Facade에 전달
- Facade/Service는 도메인 객체로 로직 실행
- Response DTO는 Controller에서 Info 객체로부터 생성
- DTO의 `from()`은 단순 매핑만 (변환 로직 금지)

## 트랜잭션 경계

- ArgumentResolver는 트랜잭션 밖에서 실행
- Controller → Service로 Entity 대신 **ID 전달**
- Service가 트랜잭션 내에서 Entity 조회

## 도메인 규칙 위치

| 규칙 | 위치 |
|------|------|
| 비밀번호 길이/형식 | `Password` Value Object |
| 로그인ID 형식 | `User.create()` |
| 중복 로그인ID | `UserService.register()` |
| 마스킹 | `MaskingUtils` + `Facade` |
