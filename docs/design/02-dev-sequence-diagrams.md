# 개발자용 시퀀스 다이어그램

> **문서 목적**: 구현 전 설계 밑그림입니다. 객체 간 메시지 흐름을 시각화하여 **책임 분리, 호출 순서, 트랜잭션 경계**를 확인합니다.
>
> **독자**: 개발자, 코드 리뷰어
>
> **이 문서로 확인할 수 있는 것**: 누가 무엇을 책임지는가, 어떤 순서로 호출되는가, 트랜잭션이 어디서 시작하고 끝나는가

### 작성 원칙

- 액터는 **도메인/패키지 경계**로 나눕니다 (Controller, Facade, Service, Domain, Repository).
- 기능 하나당 시퀀스 하나를 만듭니다.
- 각 다이어그램은 **배경 → 흐름 → 핵심 포인트** 순서로 구성합니다.

### 공통 레이어 참조

```
Client → Controller → [ArgumentResolver] → Facade → Service → Domain ← Repository
```

| 레이어 | 패키지 | 책임 |
|--------|--------|------|
| Controller | `interfaces/api` | HTTP 계약, `@Valid` 입력 검증, 상태 코드 결정 |
| ArgumentResolver | `interfaces/api/auth` | 인증 헤더 추출 → userId 반환 (트랜잭션 밖) |
| Facade | `application` | 유스케이스 조합, 응답 데이터 준비 |
| Service | `domain` | 도메인 중재, 트랜잭션 경계 |
| Domain | `domain` | 비즈니스 불변식, 엔티티 상태 변경 |
| Repository | `infrastructure` | DB 접근, 쿼리 실행 |

**인증 방식**:
- **고객 API**: `@CurrentUserId` → `CurrentUserIdArgumentResolver` → `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더
- **어드민 API**: LDAP 헤더 기반 (고객 인증과 완전 분리)

**트랜잭션 소유 원칙**:
- **단일 도메인 흐름**: Service가 `@Transactional`을 소유합니다 (원칙).
- **다중 도메인 흐름** (좋아요 등록/취소, 브랜드 삭제 등): Facade가 여러 Service를 하나의 흐름으로 조합해야 합니다. 이 경우 Facade에 `@Transactional`을 적용하여 여러 Service 호출을 하나의 트랜잭션으로 묶습니다. Service 메서드는 기본적으로 `@Transactional(propagation = REQUIRED)`이므로 Facade의 트랜잭션에 참여합니다.

---

### 범위

다중 도메인이 엮이거나 트랜잭션 설계가 필요한 흐름만 다룹니다. 단순 CRUD(브랜드 등록/수정, 상품 CRUD)와 단일 도메인 조회(상품 목록, 주문 조회)는 생략합니다.

| # | 흐름 | 다루는 이유 |
|---|------|-----------|
| 1 | 주문 생성 | 3개 도메인(Product, Brand, Order), 전수 조사, 트랜잭션 분리 |
| 2 | 브랜드 삭제 (연쇄) | 2개 도메인(Brand, Product), 벌크 UPDATE, 실행 순서 |
| 3 | 좋아요 등록 | 2개 도메인(Like, Product), 인기도 동기화 |
| 4 | 좋아요 취소 | 2개 도메인(Like, Product), hard delete, 음수 방어 |

---

## 1. 주문 생성

### 배경

Product, Brand, Order **3개 도메인**이 하나의 흐름에서 엮이고, 확인(읽기)과 확정(쓰기)에서 트랜잭션 경계가 달라집니다.

- 검증 책임이 Controller(`@Valid`)와 Facade(비즈니스 검증) 사이에 나뉩니다.
- 전수 조사의 에러 수집이 일반적인 fail-fast와 다르므로, 구현 시 주의가 필요합니다.
- 트랜잭션은 확정 단계(Phase 5)에만 걸립니다.

### 흐름

```mermaid
sequenceDiagram
    actor Client
    participant Controller as OrderController
    participant Auth as ArgumentResolver
    participant Facade as OrderFacade
    participant ProductSvc as ProductService
    participant BrandSvc as BrandService
    participant OrderSvc as OrderService
    participant Order as Order (Domain)
    participant Repo as Repository

    %% === 인증 (트랜잭션 밖) ===
    Client->>Controller: POST /api/v1/orders
    Controller->>Auth: @CurrentUserId 해석
    Auth->>Auth: X-Loopers-LoginId/LoginPw 헤더 추출
    Auth->>Repo: UserAuthService.authenticateAndGetId()
    alt 인증 실패
        Auth-->>Client: 401 (UserException.invalidCredentials)
    end
    Auth-->>Controller: userId: Long

    %% === Phase 1: 요청 형식 검증 (Controller) ===
    Controller->>Controller: @Valid 검증<br/>(빈 항목, 수량 ≤ 0)

    alt 형식 오류
        Controller-->>Client: 400 (INVALID_INPUT_VALUE + fieldErrors)
    end

    Controller->>Facade: createOrder(userId, request)

    %% === Phase 1 추가: 비즈니스 형식 검증 (Facade) ===
    Facade->>Facade: 상품 중복 검사, 종류 상한(20), 수량 상한(99)

    alt 비즈니스 형식 오류
        Facade-->>Client: 400 (DUPLICATE_PRODUCT_IN_ORDER 등)
    end

    %% === Phase 2-4: 전수 조사 + 오류 판정 (읽기) ===
    rect rgb(240, 248, 255)
        Note over Facade, Repo: 확인 단계 (Phase 2-4) — 데이터를 읽기만 함

        Facade->>ProductSvc: getProductsByIds(productIds)
        ProductSvc->>Repo: ProductRepository.findAllByIds()
        Repo-->>ProductSvc: List<Product>

        Facade->>Facade: products에서 brandIds 추출
        Facade->>BrandSvc: getBrandsByIds(brandIds)
        BrandSvc->>Repo: BrandRepository.findAllByIds(brandIds)
        Repo-->>BrandSvc: List<Brand>
        BrandSvc-->>Facade: List<Brand>

        Facade->>Facade: Phase 2 — 상품 상태 전수 조사<br/>(존재 여부, 삭제 여부 — 모든 상품 검사)

        Facade->>Facade: Phase 3 — 재고 전수 조사<br/>(요청 수량 vs 현재 재고 — 모든 상품 검사)

        Facade->>Facade: Phase 4 — 오류 판정: 수집된 오류가 있는가?
    end

    alt 오류 1건 이상 발견
        Facade-->>Client: 400 (ORDER_VALIDATION_FAILED)<br/>+ 오류 목록 [{productId, reason, message}, ...]
    end

    %% === Phase 5: 확정 (쓰기 — 단일 트랜잭션) ===
    rect rgb(240, 255, 240)
        Note over OrderSvc, Repo: Phase 5 확정 — @Transactional

        Facade->>OrderSvc: placeOrder(userId, products, items)

        loop 각 상품별
            OrderSvc->>Order: Product.decreaseStock(quantity)
            Note right of Order: 재고 부족 시 예외<br/>(이미 Phase 3에서 검증했지만 방어)
        end

        OrderSvc->>Order: Order.create(userId, items, products, brands)
        Note right of Order: 스냅샷 생성: productName, productPrice,<br/>brandName, imageUrl을 OrderItem에 복사<br/>totalAmount = Σ(price × quantity) 서버 계산

        OrderSvc->>Repo: OrderRepository.save(order)
        OrderSvc->>Repo: ProductRepository.saveAll(products)
    end

    OrderSvc-->>Facade: Order
    Facade->>Facade: Order → OrderInfo 변환
    Facade-->>Controller: OrderInfo
    Controller-->>Client: 201 Created + ApiResponse<OrderResponse>
```

### 핵심 포인트

**책임 분리:**
- Controller는 `@Valid`로 필수/형식 검증만 담당합니다. 빈 항목, 수량 음수 등이 해당됩니다.
- Facade는 비즈니스 형식 검증(중복 상품, 상한) + 전수 조사 오케스트레이션 + 응답 변환을 담당합니다.
- Service는 `@Transactional` 경계 안에서 재고 차감, 주문 생성, 저장을 담당합니다.
- Domain은 `Product.decreaseStock()`, `Order.create()` — 비즈니스 불변식을 엔티티가 스스로 지킵니다.

**호출 순서:**
- 인증(ArgumentResolver) → 형식 검증(Controller) → 비즈니스 검증(Facade) → 확정(Service) 순서로 진행됩니다. 앞 단계를 통과해야 다음 단계로 진행합니다.

**트랜잭션 경계:**
- Phase 2-4(읽기)는 트랜잭션 밖에서 실행 가능합니다. Phase 5(쓰기)만 `@Transactional`로 묶습니다.
- 재고 차감 + 스냅샷 생성 + 주문 저장이 하나의 트랜잭션에서 원자적으로 처리됩니다.

**구현 시 참고:**
- **N+1 방지**: `findAllByIds()`로 상품과 브랜드를 각각 한 번에 조회합니다. 상품별/브랜드별 개별 쿼리 호출은 금지입니다.
- **전수 조사 구현**: Phase 2-3에서 에러 발견 시 바로 throw하지 않고 List에 수집한 뒤, 모든 상품 검사 후 일괄 반환합니다.
- **스냅샷 생성 위치**: `Order.create()` 또는 `OrderItem.create()` 도메인 메서드 내부에서 처리합니다.
- **금액 계산 주체**: `Order.calculateTotalAmount()`가 도메인 내부에서 계산합니다. 클라이언트 전달값은 무시합니다.

**검증 책임과 도메인 원칙:**
- Phase 2-3의 전수 조사에서는 Facade가 상품 상태(isDeleted)와 재고(stock)를 직접 읽고 오류를 수집합니다. 도메인 메서드(`decreaseStock()`)는 예외를 즉시 throw하므로 "모든 오류를 수집"하는 전수 조사 패턴과 맞지 않기 때문입니다.
- Phase 5의 확정 단계에서는 `Product.decreaseStock()`이 재고 부족 시 예외를 throw합니다. 이미 Phase 3에서 검증했지만, Phase 3~5 사이의 시간차를 방어하는 **이중 안전장치** 역할입니다. 도메인 불변식은 확정 단계에서 엔티티가 스스로 지킵니다.

---

## 2. 브랜드 삭제 (연쇄 삭제)

### 배경

2개 도메인(Brand, Product)에 걸친 연쇄 삭제입니다.

- 상품 → 브랜드 순서를 지켜야 데이터 일관성이 보장됩니다.
- 두 UPDATE가 하나의 트랜잭션에서 원자적으로 처리되어야 합니다.
- 좋아요 데이터는 의도적으로 건드리지 않습니다 (조회 시 필터링으로 처리).

### 흐름

```mermaid
sequenceDiagram
    actor Admin
    participant Controller as AdminBrandController
    participant Auth as AdminAuthResolver
    participant Facade as BrandFacade
    participant BrandSvc as BrandService
    participant ProductSvc as ProductService
    participant Repo as Repository

    %% === 인증 ===
    Admin->>Controller: DELETE /api/admin/v1/brands/{brandId}
    Controller->>Auth: 어드민 인증 헤더 확인
    alt 인증 실패
        Auth-->>Admin: 401 Unauthorized
    end
    Auth-->>Controller: 인증 통과

    Controller->>Facade: deleteBrand(brandId)

    %% === 검증 ===
    Facade->>BrandSvc: getBrand(brandId)
    BrandSvc->>Repo: BrandRepository.findById(brandId)

    alt 브랜드 미존재 또는 이미 삭제됨
        BrandSvc-->>Admin: 404 (BrandException.notFound)
    end

    %% === 연쇄 삭제 (단일 트랜잭션) ===
    rect rgb(255, 245, 238)
        Note over Facade, Repo: @Transactional (Facade 소유 — 다중 도메인) — 반드시 상품 먼저, 브랜드 나중에

        Facade->>ProductSvc: softDeleteAllByBrandId(brandId)
        ProductSvc->>Repo: UPDATE products<br/>SET deleted_at = NOW()<br/>WHERE brand_id = ? AND deleted_at IS NULL
        Note right of Repo: 벌크 UPDATE (개별 조회 X)<br/>비즈니스 용어: "판매 중지"<br/>기술적으로: soft delete (deleted_at 설정)

        Facade->>BrandSvc: softDelete(brandId)
        BrandSvc->>Repo: UPDATE brands<br/>SET deleted_at = NOW()<br/>WHERE id = ?
    end

    Facade-->>Controller: void
    Controller-->>Admin: 200 OK

    Note over Admin, Repo: [연쇄 영향]<br/>좋아요 데이터: 삭제 안 함 (조회 시 필터링)<br/>주문 스냅샷: 영향 없음 (불변 데이터)
```

### 핵심 포인트

**책임 분리:**
- Facade는 삭제 순서 오케스트레이션을 담당합니다 (상품 먼저 → 브랜드 나중에).
- ProductService는 소속 상품 벌크 soft delete(= 비즈니스 용어로 "판매 중지")를 담당합니다.
- BrandService는 브랜드 존재 확인 + soft delete를 담당합니다.
- Like 관련 Service는 **호출하지 않습니다** (의도적). 좋아요 데이터는 조회 시 필터링으로 처리합니다.

**호출 순서:**
- 상품 soft delete → 브랜드 soft delete 순서입니다. 순서가 반대면 "브랜드 없는 활성 상품"이 일시적으로 존재하게 됩니다.

**트랜잭션 경계:**
- 상품 벌크 UPDATE + 브랜드 UPDATE가 하나의 `@Transactional` 안에서 처리됩니다. 하나라도 실패하면 전부 롤백됩니다.

**구현 시 참고:**
- **벌크 UPDATE**: 소속 상품을 개별 조회하지 않고, WHERE 조건으로 일괄 soft delete합니다.
- **이미 삭제된 브랜드 재삭제**: 404를 반환합니다 (삭제된 리소스는 존재하지 않는 것으로 간주합니다).

---

## 3. 좋아요 등록

### 배경

2개 도메인(Like, Product)이 하나의 트랜잭션에서 동기화되어야 합니다. 좋아요 저장과 인기도 증가가 분리되면 데이터 불일치가 발생합니다.

- 상품 존재 확인 → 중복 체크 → 저장 → 인기도 동기화 순서가 중요합니다.
- Like 저장과 Product.likeCount 증가가 같은 트랜잭션이어야 합니다.
- 중복 방어는 애플리케이션 레벨 검증 + DB UNIQUE 제약으로 이중 처리합니다.

### 흐름

```mermaid
sequenceDiagram
    actor Client
    participant Controller as LikeController
    participant Auth as ArgumentResolver
    participant Facade as LikeFacade
    participant LikeSvc as LikeService
    participant ProductSvc as ProductService
    participant Repo as Repository

    %% === 인증 ===
    Client->>Controller: POST /api/v1/likes
    Controller->>Auth: @CurrentUserId 해석
    alt 인증 실패
        Auth-->>Client: 401 (invalidCredentials)
    end
    Auth-->>Controller: userId: Long

    Controller->>Controller: @Valid 검증 (productId NotNull)
    Controller->>Facade: createLike(userId, productId)

    %% === 검증 + 생성 (단일 트랜잭션) ===
    rect rgb(240, 248, 255)
        Note over Facade, Repo: @Transactional (Facade 소유 — 다중 도메인)

        %% Step 1: 상품 존재 확인
        Facade->>ProductSvc: getProduct(productId)
        ProductSvc->>Repo: ProductRepository.findById(productId)

        alt 상품 미존재 또는 판매 중지됨 (isDeleted)
            ProductSvc-->>Client: 404 (ProductException.notFound)
        end

        %% Step 2: 중복 좋아요 확인
        Facade->>LikeSvc: checkDuplicate(userId, productId)
        LikeSvc->>Repo: LikeRepository.findByUserIdAndProductId()

        alt 이미 좋아요 함
            LikeSvc-->>Client: 409 (LikeException.alreadyExists)
        end

        %% Step 3: 좋아요 저장
        Facade->>LikeSvc: create(userId, productId)
        LikeSvc->>Repo: LikeRepository.save(Like.create())

        %% Step 4: 인기도 동기화
        Facade->>ProductSvc: increaseLikeCount(productId)
        ProductSvc->>Repo: UPDATE products<br/>SET like_count = like_count + 1<br/>WHERE id = ?
        Note right of Repo: 원자적 UPDATE (동시성 방어)
    end

    Facade-->>Controller: void
    Controller-->>Client: 201 Created
```

### 핵심 포인트

**책임 분리:**
- Facade는 검증 순서 오케스트레이션을 담당합니다 (상품 확인 → 중복 체크 → 저장 → 인기도 동기화).
- ProductService는 상품 존재 및 판매 중지 여부 확인(`isDeleted()`) + 인기도(likeCount) 원자적 증가를 담당합니다.
- LikeService는 중복 체크 + Like 엔티티 생성/저장을 담당합니다.
- Domain은 `Like.create()` 팩토리 메서드를 통해 엔티티를 생성합니다.

**호출 순서:**
- 상품 존재 확인 → 중복 좋아요 확인 순서입니다. 존재하지 않는 상품에 대한 중복 체크는 불필요한 쿼리이므로 상품 확인이 먼저 실행됩니다.

**트랜잭션 경계:**
- Like 저장 + Product.likeCount 증가가 같은 `@Transactional` 안에서 처리됩니다. "좋아요는 됐는데 인기도는 안 올라가는" 불일치를 방지합니다.

**구현 시 참고:**
- **likeCount 원자적 UPDATE**: `SET like_count = like_count + 1`로 DB 레벨에서 원자적으로 증가시킵니다. 애플리케이션에서 읽고 +1 후 저장하면 동시 요청 시 lost update가 발생합니다.
- **이중 안전장치**: `UNIQUE(user_id, product_id)` DB 제약 + 애플리케이션 레벨 검증을 병행합니다. 레이스 컨디션을 방어합니다.

---

## 4. 좋아요 취소

### 배경

등록의 역방향이지만 **hard delete** + **삭제된 상품에 대한 취소 허용**이라는 차이가 있습니다.

- 등록과 달리 상품 존재 확인을 하지 않습니다 (삭제된 상품이어도 취소 허용).
- soft delete가 아닌 물리 삭제(hard delete)입니다.
- likeCount 음수 방어는 DB 레벨(`GREATEST`)에서 처리합니다.

### 흐름

```mermaid
sequenceDiagram
    actor Client
    participant Controller as LikeController
    participant Auth as ArgumentResolver
    participant Facade as LikeFacade
    participant LikeSvc as LikeService
    participant ProductSvc as ProductService
    participant Repo as Repository

    %% === 인증 ===
    Client->>Controller: DELETE /api/v1/likes/{productId}
    Controller->>Auth: @CurrentUserId 해석
    alt 인증 실패
        Auth-->>Client: 401 (invalidCredentials)
    end
    Auth-->>Controller: userId: Long

    Controller->>Facade: cancelLike(userId, productId)

    %% === 검증 + 삭제 (단일 트랜잭션) ===
    rect rgb(255, 245, 238)
        Note over Facade, Repo: @Transactional (Facade 소유 — 다중 도메인)

        %% Step 1: 좋아요 존재 확인
        Facade->>LikeSvc: getLike(userId, productId)
        LikeSvc->>Repo: LikeRepository.findByUserIdAndProductId()

        alt 좋아요 기록 없음
            LikeSvc-->>Client: 404 (LikeException.notFound)
        end

        Note over Facade: 상품 존재 여부는 확인하지 않음<br/>(삭제된 상품이어도 좋아요 취소 허용)

        %% Step 2: 좋아요 삭제 (Hard Delete)
        Facade->>LikeSvc: delete(like)
        LikeSvc->>Repo: LikeRepository.delete(like)
        Note right of Repo: DELETE FROM likes<br/>WHERE id = ?<br/>(물리 삭제 — soft delete 아님)

        %% Step 3: 인기도 동기화
        Facade->>ProductSvc: decreaseLikeCount(productId)
        ProductSvc->>Repo: UPDATE products<br/>SET like_count = GREATEST(like_count - 1, 0)<br/>WHERE id = ?
        Note right of Repo: GREATEST로 음수 방어
    end

    Facade-->>Controller: void
    Controller-->>Client: 200 OK
```

### 핵심 포인트

**책임 분리:**
- Facade는 취소 흐름 오케스트레이션을 담당합니다 (좋아요 확인 → 삭제 → 인기도 동기화).
- LikeService는 좋아요 존재 확인 + 물리 삭제를 담당합니다.
- ProductService는 인기도(likeCount) 원자적 감소를 담당합니다 (음수 방어 포함).

**호출 순서 — 등록과의 차이:**
- **등록**: 상품 존재 확인 → 중복 체크 → 저장 → 인기도 +1
- **취소**: 좋아요 존재 확인 → 삭제 → 인기도 -1. **상품 존재 확인이 없습니다.**
- 이유: 상품이 판매 중지되었더라도 "내 좋아요 목록에서 제거"하는 것은 자연스러운 행위이기 때문입니다.

**트랜잭션 경계:**
- Like 삭제 + Product.likeCount 감소가 같은 `@Transactional` 안에서 처리됩니다. "좋아요는 취소됐는데 인기도는 안 줄어드는" 불일치를 방지합니다.

**구현 시 참고:**
- **Hard Delete**: `LikeRepository.delete()` — soft delete가 아닌 물리 삭제입니다. 법적 보존 의무가 없고, 등록/취소가 빈번하여 레코드 누적을 방지합니다.
- **likeCount 음수 방어**: `GREATEST(like_count - 1, 0)`으로 DB 레벨에서 최소값 0을 보장합니다. 동시성 버그로 인한 음수를 방지합니다.
