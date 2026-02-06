---
name: implement-feature
description: 요구사항 명세서 기반 기능 구현 워크플로우
---

# 기능 구현

요구사항: $ARGUMENTS

## 워크플로우

1. **요구사항 분석**
   - 기능 범위 파악
   - API 엔드포인트 정의
   - 불명확한 점은 질문

2. **기존 패턴 파악**
   - 유사한 기능 찾기
   - `.claude/rules/` 규칙 확인

3. **계층별 구현 (의존성 순서)**

   **a. Domain Layer**
   - Entity, Repository 인터페이스, Service
   - 단위 테스트 작성

   **b. Infrastructure Layer**
   - Repository 구현체

   **c. Application Layer**
   - Facade, Info DTO

   **d. Interface Layer**
   - Controller, DTO
   - E2E 테스트

4. **검증**
   ```bash
   ./gradlew test
   ./gradlew ktlintCheck
   ```

5. **커밋 단위로 분리**
   - 커밋 메시지 추천 후 개발자 승인 대기

6. **기술 결정 기록**
   - 중요한 결정은 `docs/decisions/YYYYMMDD-주제.md`에 기록
