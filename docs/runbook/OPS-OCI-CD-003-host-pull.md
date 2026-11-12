# OPS-OCI-CD-003 — main 후보의 OCI Host Pull CD

고위험 / Repository Change. #473, 상위 #470. 저장소 구현·격리 회귀와 실제 운영 적용은 별개다. **기본 OFF**이며 운영 설치·adopt·상시 실행·Rollback·DB/Cloud/Secret 변경은 수행하지 않는다. G0 실측과 별도 최초 운영 승인 없이는 Production Verified가 아니다.

## 일반 앱 자동 배포 — application-v1

최초 운영자가 승인한 기존 정책의 `compatibility_mode=application-v1`을 사용한다. 새 Observer나 별도 승인 시스템은 없다. `frontend-cosmetic-v1`/`manual-pair-v1`은 기존 제한 모드로 남으며 일반 앱 경로로 fallback하지 않는다.

- Frontend-only/Backend-only는 해당 서비스만, 두 서비스 또는 공유 이미지 입력은 해당 두 서비스를 빌드한다. Backend HTTP 계약은 Backend+Frontend CI를 유지하며, Dockerfile/전용 ignore는 해당 앱+Production, classifier/workflow/미분류는 전체 CI다. 수동 image publish dispatch는 최초 정렬을 위한 동일 SHA pair를 계속 지원한다.
- Release의 `sha`는 배포 후보 main commit이고, 각 앱의 `source_sha`/Digest/config Digest는 실제 이미지 원본을 독립 추적한다. 여러 main 병합이 누적되면 최신 push가 실제 빌드한 서비스는 그 SHA를 사용하고, 나머지 변경 서비스는 마지막 이미지 입력 변경 main SHA의 필수 CI·image build 성공을 확인한다. PR HEAD나 이전 정상 후보로 우회하지 않는다. 검증할 push/image가 없으면 차단한다.
- 변경 없는 서비스는 current artifact 전체와 원래 source SHA를 보존한다. Release는 보존 artifact의 원본이 main 조상이며 해당 서비스의 이미지 입력이 같음을 기존 분류기로 확인한다. Compose는 선택적 `BACKEND_RELEASE_SHA`/`FRONTEND_RELEASE_SHA`를 사용하고 기존 `RELEASE_SHA` 기본값도 유지한다. 변경 서비스만 `--no-deps --pull never --wait`하며 Proxy는 up하지 않는다. 전후 미선택 서비스 ID/StartedAt와 sibling/network/certificate volume 불변을 확인한다.
- DB Observer·TLS 옵션·live Flyway snapshot·pair compatibility report는 일반 앱 preflight/activate/adopt/rollback에 필요하지 않다. DB를 관측·복구했다는 증거도 생성하지 않는다. Release State의 이 모드 record는 `db_epoch`/`compatibility_evidence=null`로 기록한다. Runtime hash와 승인된 migration Git tree·Compose/Nginx fingerprint는 고정한다.
- 실제 배포 입력의 migration/SQL·Backend main resources(운영 설정 포함)·env 파일·DB backfill/data-repair 경로·Compose/Nginx·publish workflow·미분류 변경은 자동 배포에서 제외한다. 이미지/배포 입력이 아닌 test fixture·문서 SQL은 배포 차단 사유가 아니다. current→최신 main의 모든 first-parent edge에서 검사하므로 위험 변경 후 revert해도 감독형 정렬 전 STOP한다.
- Java/TypeScript만 보고 비가역 데이터 변경이나 양방향 업무 호환을 자동 증명할 수는 없다. 리뷰에서 destructive data rewrite·구버전이 읽을 수 없는 상태 전이 등을 확인하면 **main merge commit 본문에 `PawCycle-Deploy: manual`을 독립된 한 줄로 보존**한다. 해당 이력 이후 자동 배포는 중단되며 승인된 수동 작업·정상 기준선 정렬이 필요하다. Health/CI 성공이 이 의미상 판단을 대신하지 않는다. 자동 실패 복귀는 코드/이미지 복귀이며 DB data rollback이 아니다.

Controller의 중복 preflight와 manifest 생성 중 중복 Registry 전체 검증을 제거했다. 실제 activate가 기존 deploy/renewal lock 안에서 source·image·config·disk·전후 Health와 최종 Guard를 검증한다. 실패 시 **시도한 동일 서비스 집합만** 검증된 current의 local image로 복귀하며, 미선택 서비스 drift나 복귀 실패·불확실한 state commit은 기존 transition/attempt/disabled를 보존한다. 수동 복구도 `oci-release.py --application-only`와 별도 승인 입력으로 같은 경계를 사용할 수 있다.

## 후보와 CI

`classify-production-image-changes.py`는 이미지 build와 서비스 deploy 필요를 구분한다. Frontend-only image input은 Frontend만 해당 SHA로 발행한다. Backend-only도 Backend만 발행하며, 공유 build input·미분류 변경과 수동 publish dispatch는 동일 SHA pair를 발행한다. docs/test/harness와 Release 도구 변경만으로 재배포하지 않는다. Compose/Nginx는 runtime 위험, 미분류는 unknown이며 자동 정책으로 허용할 수 없다. 경로 분류는 SAFE 판정이 아니다. 경로 판정만으로 데이터 의미상 호환성을 보증하지 않으며 일반 앱 모드와 아래 legacy 호환 경로를 구분한다.

`validate-conventions.yml`은 main push에도 실행한다. 실제 squash/main SHA와 PR 모두 `classify-validation-changes.py`로 영향 lane을 선택한다. Frontend-only는 Frontend, Backend HTTP 계약은 Backend+Frontend, DB는 Backend/MySQL, Production은 Production, classifier/workflow·미분류 변경은 전체 lane을 유지한다. 비교 base를 확인할 수 없는 push도 전체 lane이다. Controller는 설치된 동일 분류기로 후보 first-parent edge의 필수 Job을 계산하며 classify/aggregate와 선택된 각 Job의 성공을 요구한다. batch push가 더 넓은 lane을 실행해도 전체 run 성공이 필요하며 필수 Job의 SKIP은 허용하지 않는다. PR metadata job은 PR에만 적용한다. Host는 GitHub public read-only API에서 해당 repository·workflow path·push event·main branch·정확한 SHA·최신 run/attempt와 각 필수 job의 성공을 확인한다. PR HEAD, aggregate status 또는 Publish 성공으로 대체하지 않는다. API 장애·rate limit·누락·pagination 초과는 차단이며 credential을 임의 추가하지 않는다.

Host는 current부터 신뢰한 main까지 first-parent 이력에서 최신 deploy 변경 SHA를 선택한다. 후속 docs-only commit은 그 후보를 숨기지 않는다. 최신 후보가 pending/실패/위험이면 이전 성공 후보로 내려가지 않는다. 전체 current→candidate diff에도 정책 범위를 적용한다. Activation 직전 기존 deploy/HTTPS lock 안에서 main tip·후보·CI·정책·배포 범위·Runtime 근거(legacy 모드는 호환 근거도)를 재검증한다. 검사 중 main이 진행하면 적용을 중단한다. GitHub와 Docker 사이에 분산 transaction은 없으며, 최종 원격 조회 이후의 merge는 다음 timer에서 새 후보가 된다. 이미 확인한 최신 후보 뒤에서 오래된 CI 완료를 배포하는 경로는 없다.

Registry tag의 raw digest·linux/amd64 child/config ID·OCI revision/source를 Host가 직접 조회하고 기존 `Host.registry`로 다시 확인한다. GitHub Artifact를 manifest나 executable로 소비하지 않는다. 이미지 크기 상한은 사전 승인된 capacity 정책에서만 가져오며 실제 local image와 기존 CLI의 용량 계산을 유지한다.

## 설치 주체와 최초 정책

최초 운영 전환의 순서·승인 입력·STOP·복구 조건은 [OPS-OCI-CD-004](OPS-OCI-CD-004-first-transition.md)를 따른다. 이 도구 변경 PR은 새 앱 배포 후보나 최초 활성화 승인을 생성하지 않는다.

설치 명령을 자동 실행하는 workflow/script는 없다. 별도 승인된 운영자는 다음을 **실측·검토 후** 준비해야 한다. 이 목록은 설치 승인 자체가 아니다.

- `/opt/pawcycle/cd-controller/`: 검토된 `oci-cd.py`, `oci-release.py`, `production_env.py`, `scripts/classify-production-image-changes.py`, `scripts/classify-validation-changes.py`를 같은 directory에 root 소유로 설치. ancestor에 group/other write·symlink 금지. 최초 정책 `controller_sha256`에 다섯 파일 bytes digest를 기록한다. timer는 `python3 -I`로 설치본만 실행하며 Release CLI도 인접한 `production_env.py`를 명시적으로 읽는다. cwd/PYTHONPATH의 동명 모듈로 대체하지 않는다. fetched main의 Python·shell·PR code를 실행하거나 설치본으로 복사하지 않는다. 새 controller 배포는 별도 운영 변경이다.
- `/opt/pawcycle/cd-source.git`: root-owned 전용 bare mirror. 정확한 HTTPS origin만 허용. 기존 opc/root checkout을 chown/reset/rebase하지 않는다. CD lock 하에서 hooks·global credential helper 없이 `refs/heads/cd-main`을 non-force fetch한다. 이전 cached main과 후손 관계를 검사하고 expected old SHA를 사용한 atomic ref 갱신으로 `origin/main`을 공개한다. Source는 git show용이며 checkout하지 않는다.
- 기존 CLI의 `/opt/pawcycle/state`, `/opt/pawcycle/runtime`, deploy/renewal lock, HTTPS adoption, backend env, 인증서 volumes·network를 그대로 사용한다. 별도 승인된 정상 baseline adopt와 Nginx fingerprint 확인이 먼저다.
- `/etc/pawcycle/oci-cd-policy.json`: root-owned regular `600`, hardlink/symlink 금지. 예제는 OFF·미승인·budget 0으로 실행 불가하다. 승인 evidence·만료·app01·origin·허용 prefix/risk·실측 runtime hash/DB epoch/migration tree·Compose/Nginx hash·양쪽 이미지 unpacked 상한·reserve/rollback 추가 쓰기 budget과 capacity evidence를 기록한다. `sensitive`는 별도 검토한 정책·검증기가 명시적으로 허용해야 한다. unknown/runtime은 항상 차단한다.
- `/etc/pawcycle/oci-cd-enabled`: 별도 최초 승인 뒤 root `600` 파일에 `enabled`를 기록하는 enable sentinel이다. 정책 ON과 sentinel 둘 다 필요하다. systemd unit 복사/enable/start는 별도 운영 승인 대상이며 저장소 작업에서 실행하지 않는다.

## Legacy 모드의 DB·애플리케이션 호환 검증기

[OPS-OCI-CD-006](OPS-OCI-CD-006-frontend-auto.md)은 기존 수동 호환 경로를 유지하면서, 최초 승인된 장식용 CSS literal 변환과 검증된 기존 Backend digest 보존만 자동 증명하는 opt-in 모드를 추가한다. 일반 Frontend/Backend 코드의 의미상 호환을 자동 승인하지 않으며 최초 설치·baseline 정렬·운영 검증은 여전히 G0다.

이 저장소는 live DB 관측 방법을 추측하거나 호환 승인값을 만들어 주지 않는다. `compatibility_observer`는 정책에 경로·bytes SHA-256을 고정한 별도 root-owned `700` executable이다. current/target SHA를 argv로 받고 제한된 시간에 JSON을 출력한다. source/Artifact가 제공한 command나 URL을 실행하지 않는다. 운영자가 설치하는 검증기와 그 종속 실행환경은 신뢰 경계에 포함되며 보호·검토가 필요하다.

검증기는 실제 DB identity/epoch, runtime baseline, 해당 앱 쌍의 forward/reverse 데이터 의미와 실패 복귀 안전성을 독립 확인해야 한다. 자동 허용 범위별 구체적인 불변식·관측 방법을 최초 정책에서 승인해야 한다. 경로 allowlist·동일 migration tree·CI green만으로 true를 출력하는 검증기는 이 계약을 충족하지 않는다. 결과 계약은 다음과 같다(실제 승인 evidence 예제가 아님).

```text
current, target: 정확한 SHA
db_epoch, runtime_env_sha256: 독립 관측한 승인 baseline
application_data_compatible, rollback_safe: 양방향 검증이 끝난 true
evidence: 비민감 검증 evidence reference
observations: 검증한 불변식·관측 결과의 비민감 요약
checked_unix, expires_unix: 관측시각~최대 120초
```

Host는 current·target·epoch·runtime·시간·근거를 바인딩해 확인한다. 이 executable은 구현된 보안/증거 어댑터이며 **실제 검증기는 아직 설치·검증되지 않은 G0 blocker**다. 미설치·실패·호환 근거 누락은 Activation 이전에 차단한다. 실제 DB 변경을 도구가 자동 추론할 수 있다고 주장하지 않는다.

검증된 입력으로 120초 이하의 approval을 메모리에 생성해 기존 `activate`의 잠금 내 검증으로 전달한다. 정확한 manifest identity·current·DB epoch·compatibility·capacity에 결합하며 CI가 운영 approval을 생성하지 않는다. `adopt`, 수동 `rollback`과 `recover-state`를 이 정책으로 호출할 수 없다.

## 실행·기록·중단·복구

`pawcycle-oci-cd.timer`는 약 2분마다 outbound 조회한다. 상주 Runner·새 VM·Public SSH·Orchestrator는 없다. source update와 전체 reconcile은 별도 `oci-cd.lock`, 실제 Release는 기존 deploy/renewal lock을 사용한다. Controller의 SIGTERM은 기존 engine의 interrupt 복귀 경계를 사용한다. 동일 current 뒤 docs-only는 no-op이다.

`oci-cd-result.json`과 systemd journal(`SyslogIdentifier=pawcycle-oci-cd`)에 SHA·이전 SHA·시작/종료·성공/차단/실패 gate·disabled를 기록한다. unchanged block은 반복 메시지를 생략한다. journal이 현재 최소 성공/실패/차단 알림 경로다. 외부 push 알림은 전송 대상·Credential 미확정으로 미연결이며 운영자가 journal 수신/외부 전달 필요성을 최초 활성화에서 확정해야 한다. 원시 subprocess/observer 출력·Credential은 기록하지 않는다.

긴급 차단은 root `600`의 `/opt/pawcycle/state/oci-cd-disabled` 설치 또는 sentinel 제거/정책 OFF/timer 중지다. Activation 직전 정책·kill switch를 다시 확인한다. 이미 시작한 Activation은 CLI가 smoke 또는 허용된 복귀를 마칠 수 있으며 즉시 프로세스 kill로 안전한 중단을 보장하지 않는다.

Release의 deploy/renewal lock 안에서 최종 Guard와 만료 승인 검사까지 통과한 뒤, 첫 transition 기록 전에 내구 `oci-cd-attempt`를 기록한다. Guard의 main 진행·후보 교체·CI 일시 실패·중단은 attempt를 만들지 않으며 다음 timer에서 재평가한다. attempt 기록 이후 실패·interrupt·commit 불확실성은 disabled/attempt를 남기며 자동 retry하지 않는다. 성공한 smoke·state commit 뒤에만 attempt를 제거한다. 광범위 prune·volume 삭제·DB restore/downgrade는 없다. 기존 CLI는 검증된 정상 current로만 local image 복귀하며 실패하면 transition을 보존한다. 인증서·Managed MySQL·sibling project 경계는 바꾸지 않는다.

`stop_on`의 gate-failure/db-runtime-drift는 해당 시도의 적용 중단을 뜻하며 `blocked`로 다음 timer에서 재평가한다. 정책 OFF·운영자 kill switch도 `blocked`지만 명시적 disable은 계속 존중한다. activation-failure/transition은 durable attempt 또는 transition이 남은 `failed-disabled`이며 수동 Recovery가 필요하다. Guard 중 운영자 kill이 설정돼도 새로운 attempt를 생성하지 않는다. attempt 쓰기 직후 transition 이전의 crash는 변경 여부를 추정해 자동 해제하지 않는다.

Fetch는 `--refmap=`으로 기본 `remote.origin.fetch` mapping을 비활성화해 `cd-main`만 갱신한다. Fetch 전에 읽은 `origin/main` SHA를 보존하고, FF 검사 뒤 그 SHA를 expected-old로 명시적 `update-ref` CAS에 전달한다. Fetch 중/직후 경쟁 변경도 덮어쓰지 않는다. 실제 disposable bare 저장소 회귀가 초기 Fetch·정상 FF·non-FF 거부·Fetch 중 경쟁·CAS 직전 경쟁을 검증한다.

수동 Recovery 전에는 CD kill switch를 유지한다. 운영자는 현재 실제 image/config/smoke·권위 state·transition을 독립 조사하고 기존 Runbook의 별도 승인된 recover-state/rollback을 수행한다. 원인·검증이 끝난 뒤 새 운영 승인으로 attempt/disabled 해제 여부를 결정한다. 자동 해제·stale lock 강제 해제 기능은 없다. 저장소 복구는 일반 revert PR이다.

## 검증과 남은 G0

`test-oci-cd.py`는 실제 root 파일 권한·flock·atomic state와 disposable Git 이력을 사용하되 외부 runtime/CI/observer는 fake다. 기존 Release의 image/provenance/Nginx/capacity/recovery 회귀와 Nginx/Compose contract를 함께 확인한다. 격리 proof는 실제 호환 승인으로 사용할 수 없다.

아래 Observer·DB 호환성 G0는 legacy 모드에 해당한다. application-v1의 필수 전환 항목은 위 일반 앱 절차와 OPS-OCI-CD-004를 따른다. Legacy G0에는 live app01 접근·권한·egress, 정상 current/previous 채택, effective Nginx·인증서 갱신 경합, DB 양방향 호환 검증기·baseline, 실제 pull/unpack/recreate/rollback peak budget, root source/controller 설치, 정책·journal/외부 알림 판단·kill switch, 최초 enable 승인과 실제 merge→SHA/HTTPS 관측이 남아 있다. 이 근거가 없어도 저장소 구현은 가능하지만 실제 자동 실행은 계속 OFF다.
