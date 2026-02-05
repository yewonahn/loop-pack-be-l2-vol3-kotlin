# 개발 규칙

## 대원칙: 증강 코딩 (Augmented Coding)

```
개발자: 방향성, 의사결정, 최종 승인
AI: 제안, 구현, 반복 작업
```

**중간 보고 필요 시점:**
- 반복적인 동작을 할 때
- 요청하지 않은 기능을 구현하려 할 때
- 테스트를 임의로 삭제하려 할 때

## 커밋 단위 작업

**작업 흐름:**
1. 논리적으로 분리 가능한 단위로 작업
2. 작업 완료 시 커밋 메시지 추천
3. 개발자가 직접 검증 후 커밋
4. 다음 작업 진행

**좋은 커밋 단위:**
- `feat: 날짜 형식 검증을 위한 Custom Validator 추가`
- `feat: DTO에 입력값 검증 어노테이션 추가`
- `test: RegisterRequest 검증 단위 테스트 추가`

**나쁜 예시:**
- 한 커밋에 Validator + DTO + 핸들러 + 테스트 전부

## 커밋 컨벤션

```
feat: 새 기능
fix: 버그 수정
docs: 문서
refactor: 리팩토링
test: 테스트 코드
chore: 빌드, 설정
```

## TDD Workflow

```
Red → Green → Refactor

1. Red: 실패하는 테스트 먼저 작성
2. Green: 테스트 통과하는 최소 코드
3. Refactor: 품질 개선 (테스트 통과 유지)
```

## 코드 스타일

- 불필요한 주석 금지 (코드가 스스로 설명)
- 주석이 필요하면 "왜"를 설명
- 코드 포매팅은 ktlint에 맡김

## 네이밍 컨벤션

**메서드:**
- `getXxx()` - 단건 조회 (없으면 예외)
- `findXxx()` - 단건 조회 (없으면 null)
- `existsByXxx()` - 존재 여부
- `register()` / `createXxx()` - 생성

**변수:**
- 의도를 드러내는 이름 (`data` ❌ → `userList` ✅)
- boolean은 `is`, `has`, `can` 접두사

## 개발 순서 (새 기능)

```
1. 요구사항 분석 → 도메인 파악
2. API 설계 (엔드포인트 정의)
3. 의존성 없는 도메인부터 개발:
   Model (Unit Test) → Repository → Service (Integration Test) → Facade → Controller (E2E Test)
```

## PR 리뷰 포인트

개발 과정에서의 기술적 결정은 `docs/decisions/YYYYMMDD-주제.md`에 기록

**기록 내용:**
1. 어떤 문제/요구사항?
2. 어떤 선택지들?
3. 각 선택지의 장단점?
4. 최종 결정과 이유?
