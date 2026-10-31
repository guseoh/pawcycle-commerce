# OPS-OCI-CD-006 — 제한적 Frontend 자동 배포

고위험 / Repository Change. **Production OFF / 운영 미실행**. 이 문서는 최초 운영 승인을 위한 절차이며 설치·Adopt·Timer·Production/Cloud/DB/Secret 변경 승인이 아니다. [Host Pull](OPS-OCI-CD-003-host-pull.md), [최초 전환](OPS-OCI-CD-004-first-transition.md), [Release/Recovery](OPS-OCI-CD-02-release-recovery.md)의 기존 Gate를 유지한다.

## 최초 자동 허용 범위

정책 `compatibility_mode=frontend-cosmetic-v1`만 별도 release pair 수동 proof 없이 실행한다. 기존 `manual-pair-v1`(필드 생략 시 기본값)은 [#484 Observer의 pair proof와 리뷰된 양방향 artifact](OPS-OCI-CD-005-compat-observer.md)를 계속 요구한다. 자동 검증 실패 시 수동 경로로 fallback하지 않는다.

- 기존 `frontend/src/app/globals.css`의 `--accent:#RRGGBB;`, `--accent-soft:#RRGGBB;` 두 값만 변경할 수 있다. 각 RGB 채널의 차이는 **최초 승인 baseline 대비 16 이하**다. 각 release마다 budget을 초기화하지 않는다. 나머지 모든 CSS bytes는 동일해야 한다. 선택자·레이아웃·접근성/상태 토큰·URL·주석 추가도 자동 허용하지 않는다.
- root 정책의 `automatic_baseline_sha`는 최초 검토한 정상 main commit이다. 운영자는 두 토큰이 장식용이고 API·인증·주문·결제·재고·구독 로직에서 해석되지 않음을 이 baseline에서 검토한다. 후속 JS/TS·설정·Backend·build·CI·Release·미분류 경로 변경은 모두 차단한다. 정적 자산도 이 최초 버전에서는 허용하지 않는다. 기존 Frontend Dockerfile은 public 자산을 최종 이미지에 복사하지 않으므로 확장자만 보고 자산 반영을 주장하지 않는다.
- 부수적인 `docs/**/*.md`, `README.md`의 일반 파일 추가/수정만 허용한다. docs-only는 이미지 발행·배포하지 않는다. 삭제·rename·symlink·실행 mode 변경은 자동 증거에서 차단한다.
- Observer는 bare mirror의 정확한 current→target raw diff(파일 mode·blob ID 포함)와 baseline→최신 cached main의 **모든 first-parent edge**를 검증한다. 위험 commit이 나중에 revert돼도 STOP한다. main의 merge 결과를 검사하며, main에 노출되지 않은 topic branch 중간 commit은 배포 후보가 아니다. 최신 deploy 후보 뒤의 docs tail은 허용하지만 CI/Release 도구 tail은 차단한다. 256 commit을 넘으면 새 감독형 baseline 검토가 필요하다.

이 증거는 domain E2E를 실행했다고 표시하지 않는다. 승인된 source의 동작 부분이 불변인 제한적 변환을 입증한다. live MySQL identity/Flyway epoch·migration tree·Runtime hash를 계속 독립 관측하며 DB 행·credential은 기록하지 않는다. 기존 정상 current·local image 복귀·HTTPS smoke·sibling 경계도 유지한다.

GHCR workflow는 Frontend-only input이면 Frontend만 target SHA로 발행한다. 수동 기준선 정렬의 동일 SHA pair는 명시적 publish dispatch로 준비한다. 자동 경로는 **검증된 current Backend artifact를 그대로 보존**한다. Backend의 repository/digest/config digest/budget과 최초 image source SHA를 current에서 복사하며 재빌드된 target Backend를 자동 승인하지 않는다. Manifest의 선택적 `backend.source_sha`로 원본 tag/revision을 Registry·local image에서 계속 검증하고, 원본 source가 target main의 조상이며 Backend/build 입력이 동일함도 Release 엔진이 확인한다. Frontend만 target SHA 이미지를 사용한다. 이 opt-in 자동 경로는 기존 Backend 이미지의 검증만 수행하며 Backend를 새 SHA로 retag/pull/Compose up하지 않는다. Nginx 설정 Fingerprint가 동일하므로 Proxy도 Compose up하지 않는다. Frontend만 `--no-deps`로 교체하고, 전후 Backend·Proxy 컨테이너 ID/StartedAt이 다르면 실패 처리한다. 자동 실패 복귀 역시 Frontend만 적용하며 보존 서비스의 변경이 관측되면 수동 Recovery 마커를 유지한다. 기존 수동 Release/rollback은 종전의 전체 서비스 동작을 유지한다. 수동 manifest의 기본 동일 SHA 계약은 유지된다.

## 정책과 증거

기본 OFF 예제의 공통 hash·capacity·expiry·승인 필드는 그대로 채워야 한다. 최초 승인 이후에만 다음 제한을 사용한다.

```text
compatibility_mode: frontend-cosmetic-v1
automatic_baseline_sha: 운영자가 검토하고 실제 정상 배포한 정확한 main SHA
allowed_prefixes: [frontend/src/app/globals.css]
allowed_risks: [application]
compatibility_observer / observer_sha256: 별도 설치한 검토본과 bytes digest
```

Controller는 보호된 정책 경로를 설치 Observer의 `--auto-policy`로 전달한다. Observer는 정책 보호·승인·만료·범위를 직접 검증하고 exact pair·main·baseline·정책 identity·이력 hash·live DB·Runtime을 묶은 90초 이하 증거를 생성한다. Controller는 이를 대조하고 기존 Backend artifact의 완전한 identity 보존을 확인해 기존 Release의 120초 이하 메모리 approval을 구성한다. 정책 변경·Kill Switch·CI·후보·호환 증거를 기존 deploy/renewal lock 안에서 재검증한다. 저장소/CI가 운영 approval 파일을 생성하지 않는다. pair마다 운영자 파일을 갱신할 필요는 없다.

정책 수명이 지나면 STOP하며 자동 연장하지 않는다. API 장애·exact main SHA CI 실패/누락·이미지 출처/변조·DB/Runtime drift·용량 부족은 배포 0건이다. Lock 경합·중복 Timer·연속 병합은 기존 Controller의 최신 후보/attempt 경계를 사용한다. Activation 이후 실패는 정상 current local 복귀를 시도하고 durable attempt/disabled를 유지한다. DB restore/migration/downgrade는 실행하지 않는다. 수동 Recovery와 attempt 해제는 별도 승인이다.

## 최초 app01 적용의 최소 순서 — 별도 운영 승인 필요

1. CD를 OFF로 유지한 상태에서 실제 Production SHA·이미지 graph·정상 HTTPS/인증서·Runtime·live DB epoch·migration·sibling·disk peak/복귀 budget·lock 상태를 독립 확인한다. 이 저장소 작업에서는 실제 Production SHA를 조회하거나 추정하지 않았다.
2. 실제 current→최신 main의 누적 diff와 main 중간 이력을 확인한다. 자동 범위 밖이면 **최초 1회 감독형 기준선 정렬**이 선행돼야 한다. 전체 누적 변경의 CI·양방향 호환·DB 불변·capacity·복구를 별도로 승인하고 기존 수동 Release 절차로 정확한 main 이미지를 배포·HTTPS 검증한다. 기존 권위 state가 있으면 수동 activate를 사용하며, Adopt로 SHA를 덮어쓰거나 현재 컨테이너를 최신 main으로 재표기하지 않는다. 필요한 Backend/DB 호환 증거를 만들 수 없으면 여기서 STOP한다. 최초 state가 없는 경우만 OPS-OCI-CD-004의 승인된 Adopt를 따른다. 도구/docs-only main에 이미지나 전체 CI 근거가 없으면 기존 readiness/publish 절차의 별도 승인된 exact-SHA 근거 준비가 필요하며, 없는 이미지를 배포했다고 간주하지 않는다.
3. 검증된 실제 정상 main SHA를 `automatic_baseline_sha`로 고정한다. 설치본 Controller/Release/두 classifier/환경 parser와 Observer bytes를 검토·hash 고정하고 보호된 정책·Runtime·read-only MySQL TLS 입력을 준비한다. 기존 수동 pair-proof 파일을 자동용 가짜 PASS로 대체하지 않는다. 향후 source의 설치본 자동 갱신도 하지 않는다.
4. 제한 범위·만료·capacity·journal 알림·중단/Recovery를 포함한 최초 정책과 설치·sentinel·Timer 활성화를 명시적으로 승인한다. Kill Switch 동작을 확인한 뒤 승인된 운영자가 활성화한다. 현재와 baseline이 같으면 첫 tick은 no-candidate다.
5. 별도 검토/병합된 허용 색상 변경 하나로 main push의 exact SHA CI와 GHCR 성공을 기다린다. `oci-cd-result.json`/journal의 `activate-verified`, 권위 current SHA·실제 컨테이너 image ID와 provenance, 외부 HTTPS의 변경된 CSS bytes/화면·기존 앱 smoke·sibling 불변을 독립 확인한다. 이것이 최초 운영 성공 기준이며 PR/CI Green만으로 Production Verified라고 하지 않는다.

긴급 중단은 기존 `/opt/pawcycle/state/oci-cd-disabled`, sentinel 제거, 정책 OFF 또는 Timer 중지를 사용한다. 진행 중 Activation은 기존 smoke/복귀를 마칠 수 있다. 실패하면 자동 재시도를 해제하지 말고 OPS-OCI-CD-02의 수동 Recovery로 조사한다. 저장소 복구는 일반 revert PR이며 운영 정책/설치본/현재 release의 복구와 구분한다.

## 검증의 범위

`test-oci-compat-observer.py`는 실제 disposable Git 이력과 fake live MySQL로 수동/자동 증거·전체 경로 차단·위험 중간 commit/revert·superseded 후보·색상 누적 budget을 검증한다. `test-oci-cd.py`는 실제 Controller/Observer/Release state·flock 경계를 연결하고 CI/Registry/Docker/DB 외부 효과를 fake로 격리해 pair-proof 없는 자동 성공·연속 병합·중복 tick·차단·실패 local 복귀를 검증한다. 별도 artifact를 domain 검증 PASS로 조작하지 않는다. 기존 Production contracts와 Harness 회귀를 유지한다.

실제 app01 설치·Adopt·Timer·live DB/TLS·실제 보존 Backend artifact/target Frontend provenance·pull/unpack peak·인터넷 HTTPS CSS 반영·실제 장애 복귀는 이 작업에서 미실행이다. 아주 작은 색상 변경도 시각적 품질/대비에 영향을 줄 수 있으므로 PR의 UI 검토는 필요하다. 원본 Backend tag/digest/config 또는 source 입력이 불일치하면 STOP한다.
