# MVP4-QA-003 QA 결과

## 목적

MVP4-QA-002 Q-01의 legacy Product list 필드 사용과 FOUNDATION category의 무조건 비활성화를 수정한다. 시작 main은 `c591c278d5cae2c6853bc6cdd1d6d66a024ce816`이며, 검증은 2026-08-27~28 KST에 로컬에서 수행했다.

## 결과 또는 증거

- smoke는 URL encode한 FOUNDATION prefix와 `subscribable=true`로 검색하고 canonical `items`에서 fixture 1개를 검증한다. 기존 public detail/SKU/delivery cycle 검증을 유지한다.
- Configuration은 V3 미설정/false일 때 fixture 공개, true일 때 숨김을 전달한다. Service는 QA category만 정렬하며 기존 3-arg 호출의 숨김 기본값과 collision/reset 동작을 유지한다. Product PUBLIC + Category active + Brand active 조건은 변경하지 않았다.

| 검증 | 실제 결과 |
| --- | --- |
| 관련 Backend/MySQL 테스트 | 42/42 통과: Service 11, Configuration 14, Bootstrap Integration 6, Product Query 7, Controller 4. 생성·재사용·양방향 visibility 전환·멱등성·다른 category 보존 포함 |
| PowerShell parser | 변경된 `smoke.ps1` 오류 없음 |
| Standard Full | 통과: frontend 200, public fixture 1개, detail/SKU, CSRF/login/me, subscription create/list/detail, logout |
| Standard Preserved | 동일 volume에서 backend/proxy 재시작 후 통과, QA 구독 1개 보존 |
| Standard Empty | 예약 QA member 1개와 모든 QA 구독의 FOUNDATION SKU 연결 확인 후 reset=true로 통과. reset=false 복원 후에도 Empty 통과 |
| 일반 데이터 보존 | QA 구독 0→1→1→0, 다른 member 구독 4→4. 일반 volume 삭제 없이 서비스만 정지 |
| Customer V3 Auth | 기존 전용 overlay/smoke 통과. Product 100, DOG 50, CAT 50, Brand 10, Category 27(9+18), SKU 166 |
| FOUNDATION 격리 | V3 Auth DB에 fixture 1개와 inactive QA category 1개 존재. public prefix 검색 0개, discovery QA category 0개 |
| Backend build | `gradlew.bat --no-daemon build -x test` 통과. 로컬 smoke용 backend 이미지 빌드 통과 |

Customer QA는 일반 volume과 분리했으며, 검증 종료 후 이번에 생성한 전용 컨테이너·volume만 정리했다. 기존 5개 untracked 검증 디렉터리는 보존했다.

## 위험·제한

- 전체 `test build`는 Windows에서 334개 중 기존 subprocess 테스트 7개가 CreateProcess error=206으로 실패했다. Linux Java 25 이미지 재검증에서는 해당 7개가 통과했지만, `python3` 부재로 기존 데이터 생성 테스트 1개가 실패했다. 이 테스트는 Windows에서 통과했다. 동일 334개 테스트 각각의 통과 이력은 있으나 단일 전체 실행 Green으로 표시하지 않는다.
- Linux 재검증은 backend만 복사한 임시 환경이었다. 추가 임시 로컬 환경은 구성하지 않으며 제품 코드·테스트·의존성·validator를 환경 문제 해결 목적으로 변경하지 않는다.
- 사용자 결정에 따라 canonical GitHub Actions `Repository Validation`의 `Backend and MySQL validation`을 최종 전체 검증으로 사용한다. 전체 저장소 checkout, Ubuntu, MySQL 8.4, Java 25 환경의 `./gradlew test`와 `./gradlew build -x test` 결과를 확인하며, CI 실패 또는 Backend lane 생략은 merge blocker다. 실제 결과는 PR 본문과 연결된 workflow run에 기록한다.
- 전체 Browser QA와 frontend full test는 변경 범위 밖이므로 반복하지 않았다.
- 과거 Q-01 기록, V3 data, schema, public visibility 정책은 변경하지 않았다. Ready·merge·Production·AWS·CodeRabbit 수동 리뷰는 실행하지 않았다. 복구는 변경 diff의 일반 revert와 기존 Runbook을 따르며 일반 volume을 삭제하지 않는다.
