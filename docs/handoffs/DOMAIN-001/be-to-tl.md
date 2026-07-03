# DOMAIN-001 BE → TL 인수인계

## 전달 목적

DOMAIN-001에서 PS-001과 PS-002의 첫 번째 수직 MVP 요구사항을 도메인 책임, 불변 조건, 용어와 후속 결정 항목으로 정리한 결과를 Tech Lead에게 전달한다.

이번 작업은 도메인 발견과 설계 문서화다. Java, Spring, JPA, API, 데이터베이스와 애플리케이션 코드는 작성하지 않았다.

## 선행 조건 확인

- PR `#6 fix(validation): DOMAIN과 API 작업 ID 인식 추가`가 `main`에 병합된 뒤 시작했다.
- 최신 `main`에 `docs/product/PS-002-first-mvp-requirements.md`가 존재함을 확인했다.
- 최신 `main`에 `docs/handoffs/PS-002/po-to-be.md`가 존재함을 확인했다.
- 최신 `main`에 `docs/handoffs/BOOTSTRAP-006/tl-to-be.md`가 존재함을 확인했다.
- Backend Engineer 역할 브랜치 `feat/be`를 최신 `origin/main`에서 새로 준비했다.

## 산출물

- `docs/domain/DOMAIN-001-first-mvp-subscription-domain.md`
- `docs/domain/glossary.md`
- `docs/domain/rules.md`
- `docs/reports/DOMAIN-001/be-report.md`
- `docs/handoffs/DOMAIN-001/be-to-tl.md`

## 핵심 설계 결정

- 첫 번째 MVP에서는 로그인한 회원이 구독 고객 역할을 수행한다.
- Product는 상품 공통 정보, SKU는 실제 구독 선택 단위다.
- 구독 하나는 SKU 하나만 대상으로 한다.
- 구독 생성에는 존재하며 구독 가능한 SKU만 사용할 수 있다.
- 구독 수량은 1~10이다.
- 배송 주기는 2주, 4주, 8주다.
- 다음 주문 예정일은 Asia/Seoul 기준 구독 생성일에 배송 주기를 더해 계산한다.
- 휴일, 주말과 영업일 보정은 적용하지 않는다.
- 첫 번째 MVP에는 구독 상태 모델과 정기 주문 자동 생성이 없다.
- 회원은 자신의 구독 목록과 상세만 조회할 수 있다.

## API-001에 전달할 내용

- 비로그인 접근의 HTTP 상태와 오류 응답
- 존재하지 않는 SKU와 구독 불가능 SKU의 API 표현
- 수량 범위 위반과 허용되지 않은 배송 주기의 API 표현
- 다른 회원 구독 접근을 조회할 수 없는 구독으로 처리하는 API 표현
- 날짜 최종 표현
- 구독 생성 요청·응답 구조
- 내 구독 목록·상세 응답 구조

## DATA 또는 구현 설계에 전달할 내용

- Product와 SKU Aggregate 경계
- Product와 SKU 저장 구조
- SKU 구독 가능 여부 저장 방식
- Subscription이 SKU를 참조하는 방식
- SubscriptionQuantity와 DeliveryCycle 저장 표현
- 생성일과 다음 주문 예정일의 저장 여부와 컬럼 타입
- 날짜 공급 추상화 사용 여부

## UX에 전달할 제약

- 사용자는 최초 주문 예정일을 선택하지 않는다.
- 선택 가능한 배송 주기는 2주, 4주, 8주다.
- 다음 주문 예정일만 보여주며 배송 예정일은 보여주지 않는다.
- 구독 수량은 1~10이다.
- 구독 불가능한 SKU는 구독 대상으로 사용할 수 없음을 구분해야 한다.
- 첫 번째 MVP에는 구독 상태, 변경, 건너뛰기, 일시정지, 재개, 해지 액션이 없다.

## 실행한 검증 명령

```powershell
git status --short --branch
git diff --check
git diff --stat
py -3 scripts/test-validate-task-artifacts.py
Write-Output 'DOMAIN-001' | py -3 scripts/validate-task-artifacts.py --from-stdin
C:\Program Files\Git\bin\bash.exe scripts/validate-commit-message.sh --message "docs(domain): DOMAIN-001 구독 도메인 설계"
rg -n "(T[B]D|TO[D]O|추후[[:space:]]+작성|검증[[:space:]]+후[[:space:]]+작성)" docs/domain docs/reports/DOMAIN-001 docs/handoffs/DOMAIN-001
```

모든 로컬 검증은 통과했다. PR 생성 후 Repository Validation과 Collaboration Notification도 확인한다.

## TL 검토 요청

- Product와 SKU Aggregate 경계가 후속 구현 설계로 넘어가도 되는지 확인이 필요하다.
- Subscription과 SKU 참조 방식을 DATA 또는 구현 설계에서 확정하는 흐름이 적절한지 확인이 필요하다.
- API-001이 오류 표현과 날짜 표현을 책임지는 분리가 적절한지 확인이 필요하다.

## 남아 있는 제한

- 이번 작업은 도메인 문서만 변경했다.
- API 요청·응답, HTTP 상태와 오류 JSON은 확정하지 않았다.
- DB 테이블, 컬럼, FK와 물리 ERD는 확정하지 않았다.
- Java, Spring, JPA, Controller, Service, Repository 코드는 작성하지 않았다.
- 결제, 재고, 배송, 정기 주문 자동 생성, 구독 상태 모델은 첫 번째 MVP 범위에서 제외했다.
