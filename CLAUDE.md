# CLAUDE.md - Loopers Commerce Project

AI와 협업하여 개발할 때의 가이드라인입니다.
**모든 기술적 선택에는 "왜?"가 있어야 합니다.**

---

## 프로젝트 개요

### 기술 스택

| Category | Technology | Version |
|----------|------------|---------|
| Language | Kotlin | 2.0.20 |
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Cloud | Spring Cloud | 2024.0.1 |
| Database | MySQL | 8.0 |
| Cache | Redis | 7.0 |
| Messaging | Apache Kafka | 3.5.1 |
| Testing | JUnit 5, Mockito, SpringMockk | - |
| Code Quality | KtLint | 1.0.1 |

### 모듈 구조

```
loopers-kotlin-spring-template/
├── apps/
│   ├── commerce-api/          # REST API 서비스 (port 8080)
│   ├── commerce-batch/        # 배치 처리 서비스
│   └── commerce-streamer/     # Kafka 스트림 서비스
├── modules/
│   ├── jpa/                   # MySQL + QueryDSL
│   ├── redis/                 # Redis Master/Replica
│   └── kafka/                 # Kafka Producer/Consumer
└── supports/
    ├── jackson/               # JSON 직렬화
    ├── logging/               # 로깅 + Slack
    └── monitoring/            # Prometheus 메트릭
```

### 주요 명령어

```bash
# 인프라 실행
docker compose -f docker/infra-compose.yml up -d

# 빌드
./gradlew build

# 테스트
./gradlew test

# 코드 스타일 검사
./gradlew ktlintCheck

# API 실행
./gradlew :apps:commerce-api:bootRun
```

---

## 아키텍처 원칙

### 4계층 레이어 구조

```
┌─────────────────────────────────────────────────────────────┐
│  api/ (Interface Layer)                                      │
│  - Controllers, DTOs, ApiSpec (OpenAPI)                      │
│  - 책임: HTTP 요청/응답 변환, 입력 검증                         │
│  - 어노테이션: @RestController                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  application/ (Application Layer)                            │
│  - Facades, Info DTOs                                        │
│  - 책임: 유스케이스 조율, 도메인 → 애플리케이션 DTO 변환         │
│  - 어노테이션: @Component                                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  domain/ (Domain Layer)                                      │
│  - Services, Repository Interfaces, Domain Models (Entity)   │
│  - 책임: 핵심 비즈니스 로직, 도메인 규칙, 트랜잭션 경계          │
│  - 어노테이션: @Component, @Transactional                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  infrastructure/ (Infrastructure Layer)                      │
│  - Repository Implementations, JPA Repositories              │
│  - 책임: 외부 시스템 연동 (DB, 외부 API 등)                     │
│  - 어노테이션: @Repository                                    │
└─────────────────────────────────────────────────────────────┘
```

### 의존성 규칙

- 상위 계층 → 하위 계층 방향으로만 의존
- Domain Layer는 Infrastructure를 알지 못함 (인터페이스로 추상화)
- 비즈니스 로직은 반드시 Domain Layer에 위치

---

## 코드 패턴

### Domain Model (Entity)

```kotlin
@Entity
@Table(name = "example")
class ExampleModel(
    name: String,
    description: String,
) : BaseEntity() {
    var name: String = name
        protected set

    var description: String = description
        protected set

    init {
        if (name.isBlank()) throw CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "이름은 비어있을 수 없습니다.")
        if (description.isBlank()) throw CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "설명은 비어있을 수 없습니다.")
    }

    fun update(newDescription: String) {
        if (newDescription.isBlank()) throw CoreException(CommonErrorCode.INVALID_INPUT_VALUE, "설명은 비어있을 수 없습니다.")
        this.description = newDescription
    }
}
```

### Repository (Interface + Impl)

```kotlin
// domain/ - 인터페이스
interface ExampleRepository {
    fun find(id: Long): ExampleModel?
    fun save(example: ExampleModel): ExampleModel
}

// infrastructure/ - 구현체
@Repository
class ExampleRepositoryImpl(
    private val exampleJpaRepository: ExampleJpaRepository,
) : ExampleRepository {
    override fun find(id: Long): ExampleModel? = exampleJpaRepository.findByIdOrNull(id)
    override fun save(example: ExampleModel): ExampleModel = exampleJpaRepository.save(example)
}
```

### Service

```kotlin
@Component
class ExampleService(
    private val exampleRepository: ExampleRepository,
) {
    @Transactional(readOnly = true)
    fun getExample(id: Long): ExampleModel {
        return exampleRepository.find(id)
            ?: throw CoreException(CommonErrorCode.RESOURCE_NOT_FOUND, "[id = $id] 예시를 찾을 수 없습니다.")
    }

    @Transactional
    fun create(name: String, description: String): ExampleModel {
        return exampleRepository.save(ExampleModel(name = name, description = description))
    }
}
```

### Facade

```kotlin
@Component
class ExampleFacade(
    private val exampleService: ExampleService,
) {
    fun getExample(id: Long): ExampleInfo {
        return exampleService.getExample(id)
            .let { ExampleInfo.from(it) }
    }
}
```

### Controller

```kotlin
@RestController
@RequestMapping("/api/v1/examples")
class ExampleV1Controller(
    private val exampleFacade: ExampleFacade,
) : ExampleV1ApiSpec {

    @GetMapping("/{id}")
    override fun getExample(@PathVariable id: Long): ApiResponse<ExampleV1Dto.Response> {
        return ApiResponse.success(
            ExampleV1Dto.Response.from(exampleFacade.getExample(id))
        )
    }
}
```

### API 응답 형식

```kotlin
// 성공
ApiResponse.success(data)

// 실패 시 CoreException 던지면 ApiControllerAdvice가 처리
throw CoreException(CommonErrorCode.RESOURCE_NOT_FOUND, "리소스를 찾을 수 없습니다")
```

---

## 개발 규칙

### 대원칙: 증강 코딩 (Augmented Coding)

```
┌─────────────────────────────────────────────────────────────┐
│  개발자의 역할                                                │
│  - 방향성 및 주요 의사 결정                                    │
│  - 설계 주도권 유지                                           │
│  - 최종 승인                                                  │
├─────────────────────────────────────────────────────────────┤
│  AI의 역할                                                    │
│  - 제안 및 구현                                               │
│  - 반복적인 작업 수행                                          │
│  - 개발자 승인 후 작업 수행                                    │
└─────────────────────────────────────────────────────────────┘
```

**중간 결과 보고 필요 시점:**
- AI가 반복적인 동작을 할 때
- 요청하지 않은 기능을 구현하려 할 때
- 테스트를 임의로 삭제하려 할 때

### TDD Workflow: Red → Green → Refactor

**1. Red Phase:** 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 테스트 케이스 작성
- 아직 구현이 없으므로 실패해야 함

**2. Green Phase:** 테스트를 통과하는 최소한의 코드 작성
- 오버엔지니어링 금지
- 테스트 통과에만 집중

**3. Refactor Phase:** 품질 개선
- 불필요한 코드 제거
- 가독성 개선
- 모든 테스트가 여전히 통과해야 함

---

## 테스트 전략

### 테스트 피라미드

```
          ▲
         /E\          E2E 테스트 (적게)
        /2E \         - TestRestTemplate
       /─────\        - 전체 HTTP 흐름 검증
      /Integra\       통합 테스트 (적당히)
     /  tion   \      - @SpringBootTest
    /───────────\     - Service, Repository 연동
   /    Unit     \    단위 테스트 (많이, 빠르게)
  /───────────────\   - 순수 JVM, Spring 없이
                      - Domain Model, VO
```

### 테스트 유형별 책임

| 테스트 유형 | 대상 | 환경 | 검증 내용 |
|------------|------|------|----------|
| Unit | Domain Model | 순수 JVM | 비즈니스 규칙, 유효성 검증 |
| Integration | Service | @SpringBootTest + Testcontainers | 비즈니스 흐름, DB 연동 |
| E2E | Controller → DB | @SpringBootTest(RANDOM_PORT) | HTTP 요청/응답 전체 시나리오 |

### 테스트 코드 컨벤션

```kotlin
// 파일명: {테스트대상}{테스트타입}Test.kt
// 예: ExampleModelTest.kt, ExampleServiceIntegrationTest.kt, ExampleV1ApiE2ETest.kt

class ExampleModelTest {

    @DisplayName("예시 모델을 생성할 때,")  // 한글로 컨텍스트 설명
    @Nested
    inner class Create {

        @DisplayName("이름과 설명이 주어지면, 정상적으로 생성된다.")  // 한글로 기대 결과
        @Test
        fun success() {
            // arrange (준비)
            val name = "제목"
            val description = "설명"

            // act (실행)
            val result = ExampleModel(name = name, description = description)

            // assert (검증)
            assertAll(
                { assertThat(result.name).isEqualTo(name) },
                { assertThat(result.description).isEqualTo(description) },
            )
        }

        @DisplayName("이름이 빈칸이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun failsWhenNameIsBlank() {
            // arrange
            val name = "   "

            // act & assert
            val result = assertThrows<CoreException> {
                ExampleModel(name = name, description = "설명")
            }
            assertThat(result.errorCode).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE)
        }
    }
}
```

### 통합/E2E 테스트 설정

```kotlin
@SpringBootTest
class ExampleServiceIntegrationTest @Autowired constructor(
    private val exampleService: ExampleService,
    private val exampleJpaRepository: ExampleJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,  // 테스트 격리
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()  // 매 테스트 후 DB 초기화
    }
}
```

### 테스트 더블 사용 기준

| 역할 | 사용 시점 | 예시 |
|------|----------|------|
| Stub | 고정된 응답만 필요할 때 | `when(repo.find(1L)).thenReturn(entity)` |
| Mock | 호출 여부/횟수 검증 필요할 때 | `verify(publisher).publish(any())` |
| Fake | 상태 관리가 필요할 때 | `InMemoryRepository` |

---

## 주의사항

### Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현
- null-safety 하지 않은 코드 (nullable 타입 명시적 처리 필수)
- println 남기기 (Logger 사용)
- 테스트 없이 구현 완료 선언
- 임의로 테스트 삭제
- 요청하지 않은 기능 임의 구현

### Recommendation

- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 개발 완료된 API는 `http/*.http`에 분류해 작성
- 기존 코드 패턴 분석 후 일관성 유지
- 모든 기술적 선택에 "왜?"를 설명

### Priority

1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴과 일관성 유지

---

## 커밋 컨벤션

```
feat: 새 기능
fix: 버그 수정
docs: 문서 (README, 템플릿 등)
refactor: 리팩토링
test: 테스트 코드
chore: 빌드, 설정 변경
```

---

## 개발 순서 (새 기능 추가 시)

```
1. 요구사항 분석 → 도메인 파악
2. 테이블 설계 (ERD)
3. API 설계 (엔드포인트 정의)
4. 패키지 구조 잡기
5. 의존성 없는 도메인부터 개발:
   Model (Unit Test) → Repository → Service (Integration Test) → Facade → Controller (E2E Test)
```
