# FOUNDATION-004 Backend → Platform/SRE 인수인계

## 전달 목적

Platform/SRE가 첫 구독 MVP 브라우저 통합 검증용 local-only 실행 환경과 Runbook을 구성할 수 있도록 Backend bootstrap 설정·데이터·reset 계약을 전달한다.

## 대상 역할

- Platform/SRE

## 입력 문서

- `docs/reports/FOUNDATION-004/be-report.md`
- `docs/adr/AUTH-003-session-authentication-approved-inputs.md`
- `docs/handoffs/FRONTEND-001/fe-to-qa.md`
- `backend/src/main/resources/application-local-integration.properties`

## 사용 가능한 결과

- 기본 비활성 `local-integration` profile
- 환경 변수 기반 전용 QA 회원 bootstrap
- 공개 상품과 구독 가능 SKU의 결정적 fixture
- 동일 fixture 반복 실행과 충돌 fail-fast
- 전용 QA 회원 구독만 삭제하는 명시적 reset
- 전체 bootstrap·reset의 단일 transaction과 rollback

## 실행 조건

다음 조건이 모두 충족될 때만 bootstrap runner가 실행된다.

1. active profile에 `local-integration`이 있다.
2. active profile에 `test`, `production`, `prod`가 없다.
3. `PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED=true`다.
4. QA email·password 환경 변수가 존재하고 유효하다.
5. 기존 datasource 환경 변수가 유효한 로컬 MySQL 8.4를 가리킨다.

## 설정 이름

```text
SPRING_PROFILES_ACTIVE=local-integration
SPRING_DATASOURCE_URL=<로컬 MySQL JDBC URL>
SPRING_DATASOURCE_USERNAME=<로컬 환경 변수에서 제공>
SPRING_DATASOURCE_PASSWORD=<로컬 환경 변수에서 제공>
PAWCYCLE_LOCAL_QA_BOOTSTRAP_ENABLED=true
PAWCYCLE_LOCAL_QA_BOOTSTRAP_EMAIL=<로컬 환경 변수에서 제공>
PAWCYCLE_LOCAL_QA_BOOTSTRAP_PASSWORD=<로컬 환경 변수에서 제공>
PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false
```

- email local-part는 예약 식별자 `qa-foundation-004`여야 한다.
- 실제 email·password·hash·JDBC credential을 Runbook 예시, 로그, 명령 이력과 저장소에 기록하지 않는다.
- email·password에는 Backend fallback과 기본값이 없다.
- profile 파일의 bootstrap과 reset 기본값은 `false`다.

## reset 방법

- 평상시: `PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=false`
- 빈 구독 상태 준비 시에만: `PAWCYCLE_LOCAL_QA_BOOTSTRAP_RESET_SUBSCRIPTIONS=true`
- reset은 정규화된 전용 QA 회원 ID의 `subscriptions`만 삭제한다.
- 다른 회원, 상품, SKU와 비fixture 데이터는 변경하지 않는다.
- reset 후 계속 기동할 수 있지만, 불필요한 반복 삭제를 피하도록 Runbook은 빈 상태 준비 후 다시 `false`로 되돌리는 절차를 포함한다.

## fixture 식별과 충돌

- 회원: 예약 local-part + 환경 변수 domain으로 구성된 정규화 email
- 상품: FOUNDATION-004 예약 이름과 전체 필드 일치
- SKU: fixture 상품 ID + FOUNDATION-004 예약 이름과 가격·구독 가능·표시 순서 일치
- 회원 password 불일치, 상품·SKU 필드 불일치 또는 복수 후보는 시작 실패다.
- 충돌 시 row를 수정·선택·삭제하거나 다른 데이터로 대체하지 않는다.

## 트랜잭션과 실패 동작

- 회원 잠금 조회, 필요 시 회원·상품·SKU 생성과 선택적 reset은 하나의 transaction이다.
- 같은 QA email은 비관적 쓰기 잠금으로 직렬화한다.
- credential invalid, fixture 충돌, DB 오류 또는 reset 오류가 발생하면 시작이 중단되고 transaction이 rollback된다.
- 오류 메시지는 설정 종류와 충돌 영역만 설명하며 credential 값을 포함하지 않는다.

## Platform/SRE Runbook 작업 범위

1. 로컬 MySQL 8.4 datasource 준비와 Secret 전달 방식
2. Java 25로 Backend를 `local-integration` profile에서 실행하는 명령
3. Frontend와 Backend `/api/**`를 same-origin으로 연결하는 승인된 로컬 실행 구조
4. bootstrap 최초 실행·반복 실행·reset 실행 절차
5. 기동 실패 시 profile, enable flag, 환경 변수 존재 여부, fixture 충돌을 값 노출 없이 점검하는 진단 순서
6. 종료·재기동과 reset flag 복원 절차

Docker Compose, reverse proxy와 구체적인 same-origin 구현은 Platform/SRE 승인 범위에서 결정한다. Backend 코드를 변경해 CORS나 cookie 계약을 우회하지 않는다.

## 미결정 사항 또는 승인 필요 항목

- Backend bootstrap 계약에는 미결정 사항이 없다.
- Docker Compose, reverse proxy와 구체적인 same-origin 로컬 실행 구조는 Platform/SRE가 자신의 승인 범위에서 설계하고 사용자 검토를 받는다.

## 검증 포인트

1. bootstrap 비활성화 상태에서 데이터가 생성되지 않는다.
2. local 외 profile과 local+production/test 조합에서 runner가 없다.
3. 환경 변수 누락·invalid 시 일부 fixture 없이 시작이 실패한다.
4. 최초 실행 후 전용 회원·공개 상품·구독 가능 SKU가 각각 하나다.
5. 같은 설정의 반복 실행 후 row 수가 증가하지 않는다.
6. reset `false`에서 기존 QA 구독이 유지된다.
7. reset `true`에서 QA 회원 구독만 사라지고 다른 회원 데이터가 보존된다.
8. Backend 로그·Frontend 응답·shell history에 raw password, hash, session ID와 CSRF token이 없다.

## 중단 조건

- 실제 credential 또는 JDBC Secret을 저장소·Runbook에 기록해야 한다.
- `local-integration`과 production/test profile을 확실히 분리할 수 없다.
- 기존 fixture 충돌을 자동 덮어쓰기·삭제해야 한다.
- DB schema·migration, CORS, cookie 또는 API 계약 변경이 필요하다.
- 다른 회원·비fixture 데이터 삭제가 필요하다.
- Java 25, MySQL 8.4 또는 same-origin 실행 경계를 제공할 수 없다.

## QA 필요 여부

- 필요. Platform/SRE Runbook 완성 뒤 QA가 실제 브라우저에서 로그인과 구독 수직 흐름을 검증한다.

## 남은 위험 또는 주의 사항

- 현재 Backend 작업 PC에는 Java 25와 datasource 환경이 없어 local bootstrap의 실제 기동 검증을 수행하지 못했다.
- 같은 QA email은 DB lock으로 직렬화하지만 local 검증 환경은 단일 Backend 인스턴스 실행을 기준으로 한다.
- reset flag를 계속 `true`로 두면 재기동마다 QA 회원 구독이 삭제되므로 빈 상태 준비 후 `false`로 복원해야 한다.
- 실제 browser same-origin 흐름은 Platform/SRE 환경 완성과 QA 검증 전까지 미확인이다.

## 다음 권장 작업

- 같은 `FOUNDATION-004` 작업 ID로 local-only 실행 환경과 Runbook을 완성한다.
- 완료 후 QA에 환경 변수 값이 아니라 설정 이름, 실행 절차와 테스트 데이터 초기화 절차만 전달한다.
