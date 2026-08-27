# MVP4-QA-002 Customer Product Experience QA Report

- 작업 등급: 일반
- 실행 구분: 저장소 변경

## 목적

Customer Catalog Data V3 기반 local-integration에서 MVP4 Customer Product Experience의 실제 Home → Product List → Product Detail 및 인증 구매 진입 흐름을 검증하고, 반복 가능한 QA gate와 남은 위험을 증거로 남긴다.

## 실행 정보

- 환경: Windows PowerShell 7.6.4, Docker Desktop Linux Engine / Compose v2.40.0, 기존 Java 25 / Node 24 Dockerfile
- 브라우저: 기존 Codex in-app browser, desktop 1440×900 / mobile 360×800 및 320×800
- Clean project: `pawcycle-mvp4-customer-qa`
- Clean MySQL volume: `pawcycle-mvp4-customer-qa-mysql-data`
- 새 test framework/dependency 추가 없음
- credential, cookie, CSRF token, DB password는 기록하지 않음

## 결과 또는 증거

| 실행 | 결과 |
| --- | --- |
| Clean/Auth Compose | `config --quiet` 통과. project/volume/profile/V3/auth/reset 값은 credential 출력 없이 별도로 확인 |
| QA image build / startup | Backend bootJar, Frontend build 통과. 5개 service healthy |
| Clean API preflight | Product 100, DOG 50, CAT 50, Brand 10, 공개 Category 27(9+18), public SKU 166 |
| 대표 상품 | 공개 검색으로 발견. 2 option groups, 4 SKUs, MAIN 1 + DETAIL 3, sections 3, 할인/품절/구매 가능/정기배송 상태 확인 |
| Desktop Home/List | Hero/discovery/3 collections/anonymous login 확인. DOG/CAT, category/subcategory/brand, facet 단독·복수, 가격·구독·구매 조건, 잘못된 가격 범위 차단, chip 제거/초기화, 6종 sort, pagination과 Back/Forward 확인 |
| Detail | q 검색→상세, gallery 전환, brand/trust empty/detail sections/Review·Q&A/related 확인. 불완전/품절 Cart 차단 및 SKU 전환에 따른 가격/할인/재고/max 갱신 확인 |
| Mobile | Home/CAT quick link, Header menu/category/search, filter panel 확인. 320px Home/List 및 360px Detail에서 horizontal overflow 없음. dialog option/quantity 상태 유지, 닫기와 opener focus 복귀 확인 |
| Auth mode | 기존 bootstrap으로 재생성 후 5 services healthy. DB 전체 Product 101/SKU 167/Brand 10/Category 29, 공개 Product 100 확인 |
| Auth API | QA 계정으로 CSRF/login/me, Wishlist add/remove, purchasable SKU Cart add(quantity=2) 및 조회 일치 확인. 임시 Cart 삭제와 logout 완료 |
| Auth browser | 로그인 후 원래 상품 복귀. Wishlist badge 0→1→0, Cart 1kg/2팩 quantity 2·17,800원·35,600원 및 Header badge 2 일치. 품절/0/1.5 수량 차단. 2kg/1팩 26,700원·13% 할인·재고44 확인. `/subscriptions/new?productId=...&skuId=...` 진입 확인 |
| 기존 FOUNDATION smoke | **실패**. `Full` 1회에서 `Expected exactly one FOUNDATION-004 product fixture`. 기존 smoke/bootstrap/category 정책은 수정하지 않음 |
| Cleanup | 전용 5개 container와 network를 내리고 전용 MySQL volume만 task 생성 이력/project label 확인 후 삭제. 기존 `pawcycle-local-integration-mysql-data` 보존 확인 |

### Review correction 검증

CodeRabbit review correction에서는 smoke의 모든 HTTP redirect를 차단하고, repeated facet 요청이 두 단독 facet Product ID 집합의 정확한 교집합인지 검증하도록 계약을 강화했다. Runbook은 성공/실패 모두 전용 project `down`과 shell 환경 변수 원복을 수행하도록 `try/finally` 경계를 정의하고, runtime에서는 project label, 전용 volume label, `local-integration` profile, V3/auth/reset non-secret 값을 assertion하도록 변경했다. 이 correction에서 새 제품 기능이나 Production 실행은 수행하지 않는다.

## 위험·제한

현재 확인 범위에서 확정 Customer 구매 흐름 BLOCKER는 없다. 아래 항목은 후속 correction 또는 명시적 재검증 대상이다.

| ID | 분류 | 내용 |
| --- | --- | --- |
| C-01 | CORRECTION | Clean Home Hero의 “다음 배송을 한눈에” 카드에서 흰 배경 위 흰색 제목/링크와 흰색 82% 설명이 확인됨. Frontend CSS correction 필요 |
| C-02 | CORRECTION | 대표 상품 품절 조합에서 구매는 차단되지만 disabled quantity 1 옆에 “현재 재고 0개 이하로 선택해 주세요.”가 노출되어 품절과 수량 오류 UX 분리가 필요 |
| C-03 | CORRECTION | 320px `/products` 검색 form에서 input 폭 약 24px, 버튼 약 240.8px로 입력값 확인이 어려움. 전체 horizontal overflow는 없음 |
| C-04 | CORRECTION | Home REVIEW_COUNT collection 문구가 모든 card의 review 없음 상태와 어색함. Review seed 부재 자체는 데이터 계약 |
| Q-01 | CORRECTION | FOUNDATION-004 smoke는 public response `products` 기대와 현재 `items` 계약이 다르고, QA fixture category가 inactive여서 public discovery에서 제외됨. 단순 field rename으로 해결되지 않으므로 별도 QA correction 필요 |
| F-01 | FOLLOW-UP | 기존 image optimization/external image, dependency high 6건, component test framework, AbortController는 이번 범위에서 다루지 않음 |

미확인·제한:

- native purchase dialog의 실제 키보드 Escape는 자동 키 입력으로 확정하지 못해 수동 재확인 필요
- 목록 검색 Enter 제출은 자동 입력에서 확정하지 못했고 버튼 클릭 경로는 통과
- Wishlist 최초 loading 상태와 느린 Wishlist GET 중 Cart 동시 조작은 브라우저에서 재현하지 못함
- 외부 image 강제 실패, discovery metadata 실패, seeded review/Q&A 작성, 실제 subscription 생성·결제는 미실행
- 전체 Catalog SKU 조합 전수 browser 조작은 수행하지 않았고 대표 상품 4개 조합을 확인
- recent products 최종 표시는 임시 browser tab 종료로 미확인

이 미확인 항목 때문에 전체 Browser QA Gate를 근거 없이 Green으로 선언하지 않는다.
