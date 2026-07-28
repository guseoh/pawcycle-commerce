# OPS-019 Backend 작업 보고서

## 작업 정보

- 작업 ID: OPS-019
- 작업 등급: 고위험
- 역할: Backend
- 상태: 저장소 기반 준비, 실제 production 실행 미완료

## 작업 목적

Production 인증·session Smoke에 사용할 전용 회원 한 명을 기존 회원·인증 규칙으로 안전하게 생성하는 one-shot non-web Backend 명령 기반을 준비한다.

## 입력 문서

OPS-019 사용자 승인, `AUTH-003` 승인 인수인계, `AUTH-004` Backend 결과, 기존 `Member`·`EmailNormalizer`·`PasswordEncoder`·`MemberRepository` 계약과 위험 기반 Lean Harness를 입력으로 사용했다.

## 승인 입력

사용자는 production 회원이 없는 현재 상태에서 실제 실행 때 정한 전용 email과 강한 password로 회원 한 명만 생성해 반복 Smoke에 유지하는 방향을 승인했다. 이번 작업은 저장소 준비만 승인됐으며 production DB 연결, 회원 생성, 운영 container 실행과 OPS-018 재실행은 승인되지 않았다.

## 명시적 승인 근거 (고위험 필수)

사용자는 표준입력으로 email과 password를 각각 한 번 전달하고 기존 email 정규화, BCrypt encoder, 회원 repository와 transaction 경계를 재사용하는 non-web maintenance command 구현을 명시적으로 승인했다. 공개 endpoint, 직접 SQL, 외부 hash, schema 변경과 다른 도메인 데이터 생성은 금지했다.

## 변경 범위

- 명시적 enable flag와 non-web application 조건을 모두 만족할 때만 생성되는 maintenance runner
- 표준입력 두 줄을 읽고 고정된 비민감 PASS만 출력하는 one-shot command
- email 정규화, 빈 password 거부, 중복 잠금 조회, BCrypt encode와 회원 한 건 저장을 묶는 transaction service
- 입력 오류, 중복, 저장 실패, 비활성화와 민감정보 비노출 회귀 테스트
- Backend에서 Platform/SRE로 전달할 실행 경계

## 변경하지 않은 범위

Production DB·외부 network·운영 container에는 접근하지 않았다. 공개 API, `SecurityConfig`의 HTTP 인증·인가 규칙, Flyway·schema, 상품·SKU·구독·주문, Docker Compose, 운영 wrapper와 Runbook을 변경하지 않았다. 실제 email, password, memberId, password hash와 DB 연결값을 기록하지 않았다.

## 주요 결과

`pawcycle.maintenance.create-auth-smoke-member.enabled=true`와 non-web application type을 함께 요구한다. 둘 중 하나라도 없거나 servlet web application이면 runner와 service bean이 생성되지 않는다.

명령은 표준입력의 첫 줄을 email, 둘째 줄을 password로 읽는다. Email은 기존 `EmailNormalizer`로 정규화하고 password는 변형하지 않되 null·빈 값·공백만 있는 값은 거부한다. 정규화 email이 이미 존재하면 encode·save·update를 수행하지 않고 실패한다.

성공 경로는 기존 `PasswordEncoder`로 hash를 만들고 `MemberRepository.saveAndFlush`로 회원 한 건만 저장한다. 성공 출력은 고정된 PASS 한 줄이며 email, password, memberId와 hash를 포함하지 않는다.

## 핵심 결정과 대안

공개·관리자 API나 임시 HTTP endpoint 대신 기존 애플리케이션의 non-web `ApplicationRunner`를 선택했다. 일반 web process에서 enable flag가 잘못 전달돼도 실행되지 않도록 property 조건만 쓰는 대안보다 non-web 조건을 함께 사용했다.

직접 SQL과 외부 hash 생성은 기존 인증 규칙을 우회하므로 제외했다. 기존 local QA bootstrap은 회원 외 상품·SKU를 만들고 production profile을 차단하므로 재사용하지 않았다.

## 계층과 transaction 경계

Command는 표준입력과 고정 출력만 담당한다. Service는 email 정규화, password 검증, 중복 잠금 조회, encode와 저장을 조율한다. `ProductionAuthSmokeMemberService.create`의 단일 `@Transactional` 경계 안에서 조회와 `saveAndFlush`를 수행한다.

중복은 저장 전에 실패한다. Encode·조회·저장 중 runtime 실패는 입력과 persistence 원인을 포함하지 않는 고정 예외로 바꾸어 transaction 밖으로 전파하므로 rollback된다. DB unique 제약은 동시 실행 경쟁에서도 두 번째 저장을 거부한다.

## 변경 파일

- `backend/src/main/java/com/pawcycle/backend/member/maintenance/**`
- `backend/src/main/java/com/pawcycle/backend/common/security/SecurityConfig.java`
- `backend/src/test/java/com/pawcycle/backend/member/maintenance/**`
- `docs/reports/OPS-019/be-report.md`
- `docs/handoffs/OPS-019/be-to-sre.md`

## API 영향

공개·관리자·인증 HTTP endpoint와 요청·응답 계약 변경은 없다. 기존 servlet `SecurityFilterChain` 규칙은 그대로이며 non-web context에서만 생성되지 않도록 application type 조건을 명시했다.

## DB 영향

Schema, Flyway와 repository 계약 변경은 없다. 실제 후속 실행이 성공하면 기존 `members` table에 회원 한 건이 추가된다. 중복 email은 기존 row와 password hash를 변경하지 않는다.

## 보안 영향

Credential을 CLI 인자, 환경 변수, 저장소 파일로 받지 않는다. Command와 예외는 email, password, memberId, hash와 persistence 원인을 출력하지 않는다. 실제 TTY prompt와 password echo 차단은 후속 Platform/SRE wrapper 책임으로 분리했다.

## 운영 영향

일반 application boot와 servlet web process에는 영향이 없다. 실제 production 실행은 별도 사용자 승인과 SRE wrapper·Runbook이 필요하다. 이번 작업에서는 maintenance flag를 사용해 애플리케이션을 시작하거나 DB에 연결하지 않았다.

## 성능 영향

일회성 회원 한 건의 잠금 조회, BCrypt encode와 insert만 예정한다. 성능 최적화나 BCrypt 설정 변경은 없다.

## 실행한 검증

- Git·GitHub에서 기존 local·remote `feat/be`가 각각 병합 완료 PR의 잔여 head이고 열린 PR·추가 worktree·미병합 작업이 없음을 확인
- 최신 `origin/main`에서 새 `feat/be` 생성 후 local·remote·main 차이 0 확인
- OPS-019 고위험 task artifact validator: 통과
- 관련 Markdown UTF-8 strict decode: 통과
- commit 제목 규칙 검사: 통과
- `git diff --check`: 통과
- Backend 집중 test와 build: 로컬 Java 25 부재로 실행 전 중단, GitHub Repository Validation에서 확인
- 첫 Repository Validation은 Spring Boot 4.1에 없는 `ConditionalOnWebApplication.Type.NOT_WEB` 참조로 compile에 실패했고, 지원되는 `@ConditionalOnNotWebApplication`으로 같은 non-web 조건을 교체했다.
- 원격 Repository Validation: 동적 run 번호·SHA를 문서에 고정하지 않고 GitHub Checks를 권위 원본으로 확인

## 적용 전 검증 (고위험 필수)

변경 전 production 회원이 0건이고 승인된 test credential이 없다는 사용자 확인을 입력으로 사용했다. 저장소에서는 최신 main, clean worktree, 열린 PR 없음과 기존 member/auth schema·normalizer·encoder·repository 계약을 확인했다. Production DB와 network에는 접근하지 않았다.

## 적용 후 검증 (고위험 필수)

저장소 변경 후 maintenance 비활성·web process 차단, 정상 한 건 생성, 중복 무변경, 입력·저장 실패와 민감정보 비노출을 단위·통합 테스트로 검증한다. 이번 작업의 적용 대상은 저장소 코드이며 production 회원 생성은 수행하지 않는다.

## 독립 검증 (고위험 필수)

GitHub Repository Validation의 Java 25·MySQL 8.4 환경에서 Backend test와 build를 독립 검증하고, 사용자/Tech Lead가 인증·credential·transaction 경계와 SRE 인수인계를 최종 판단한다. 동적 run 번호와 head SHA는 문서에 고정하지 않는다.

## 실행하지 못한 검증과 이유

로컬에는 프로젝트가 요구하는 Java 25 toolchain이 없어 집중 Gradle test가 dependency 계산 전에 중단됐다. JDK를 낮추거나 새 dependency를 도입하지 않고 원격 Repository Validation에서 확인한다. Production DB 연결, 실제 회원 생성, container 실행과 OPS-018은 명시적 제외 범위라 실행하지 않았다.

## QA 필요 여부

별도 QA 문서는 이번 Backend 저장소 준비 단계에서 작성하지 않는다. Java 25·MySQL 8.4 CI를 독립 검증으로 사용하고, 실제 credential 입력·production 실행·session Smoke는 후속 고위험 OPS-018에서 사용자 승인과 운영 검증으로 분리한다.

## QA 문서 경로 또는 생략 사유

실제 production 동작을 실행하지 않았고 공개 API 계약도 바꾸지 않는다. 후속 SRE wrapper 검토와 실제 OPS-018 검증이 이 명령의 운영 소비자 검증이다.

## AI 리뷰 반영 여부

PR 생성 후 CodeRabbit과 Codex Review 지적을 현재 코드·승인 범위와 대조해 유효한 지적만 반영한다.

## AI 리뷰 미반영 항목과 이유

PR review 완료 후 미반영 항목이 있으면 사용자 승인, 기존 계약과 제외 범위에 근거해 PR에 기록한다.

## 적용 방법

이번 PR은 코드와 테스트만 준비한다. 실제 실행은 Platform/SRE가 별도 승인된 wrapper와 Runbook에서 non-web mode, enable flag, 표준입력·TTY 보호와 one-shot 종료를 함께 보장한 뒤 수행한다.

## 복구·롤백 증거 (고위험 필수)

저장소 변경은 일반 revert PR로 복구할 수 있다. Command 실패는 unchecked 예외가 transaction 밖으로 전파되어 insert를 rollback하며 부분 회원을 남기지 않는다. 실제 성공 회원은 반복 Smoke에 유지한다는 승인 범위이므로 이 명령에 삭제·reset 기능을 넣지 않았다. 후속 실제 생성 뒤 삭제가 필요하면 별도 고위험 사용자 승인이 필요하다.

## 위험과 제한

Backend 명령은 표준입력 두 줄의 순서만 정의하며 TTY에서 password echo를 끄지 않는다. SRE wrapper 없이 직접 실행하면 입력 보호와 fat jar 실행 방식이 보장되지 않는다. 강한 password의 구체 정책은 새로 만들지 않고 실제 사용자가 선택할 책임으로 유지했다.

## 남은 위험

실제 production DB 연결, container one-shot 종료, TTY 입력 보호, 생성 회원을 사용한 ALARM과 session Smoke는 미검증이다. 동시 실행에서는 unique 제약으로 중복 저장을 막지만 운영자는 command를 한 번만 실행해야 한다.

## 다음 작업

Platform/SRE가 이 인수인계를 입력으로 비공개 one-shot wrapper와 Runbook을 준비한다. 별도 사용자 승인 뒤 회원 한 건을 생성하고 OPS-018의 production 인증·session Smoke를 수행한다.

## Git 결과

최신 main 기반 `feat/be`에서 하나의 논리 commit으로 push한다. 최종 commit과 원격 상태는 Git을 권위 원본으로 확인한다.

## PR 결과

`feat/be`에서 `main` 대상 PR을 생성하고 자동 병합하지 않는다. Repository Validation과 review 상태는 GitHub를 권위 원본으로 확인한다.
