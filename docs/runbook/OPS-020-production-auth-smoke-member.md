# OPS-020 Production 인증 Smoke 회원 생성 Runbook

## 상태와 목적

이 문서는 OPS-019 one-shot Backend 명령을 Production에서 실행하기 위한 저장소 준비 절차다. Wrapper·fake Docker/PTY 테스트·격리 MySQL 8.4 lifecycle 테스트가 준비됐지만 실제 Production DB 연결과 회원 생성은 수행하지 않았다. 실제 실행은 별도 고위험 사용자 승인이 있어야 한다.

## 영향

승인된 실행이 성공하면 현재 Production `members` table에 인증·Session Smoke 전용 회원 한 건이 추가된다. 상품·SKU·구독·주문, schema, Flyway history, 기존 service·volume·network는 변경하지 않는다. 성공 회원은 삭제하거나 password를 변경하지 않고 OPS-018에서 재사용한다.

## 입력과 권위 원본

- 절차: `infra/production/create-production-auth-smoke-member.sh`
- Backend 계약: `docs/handoffs/OPS-019/be-to-sre.md`
- 승인 release: 현재 Production `current-sha`와 같은 40자 SHA이며 OPS-019를 포함해야 한다.
- Backend image: lowercase GHCR repository, 승인 SHA tag, OCI revision과 기록된 registry digest가 모두 일치해야 한다.
- Runtime/state: `/opt/pawcycle/runtime`, `/opt/pawcycle/state`의 기존 root 전용 파일

Email과 password는 저장소, 명령 인자, 환경 변수, shell history, command substitution, 일반 파일과 Docker log에 넣지 않는다. Wrapper가 `/dev/tty`에서 email을 한 번, echo를 끈 password를 한 번 읽어 Backend Container stdin 두 줄로만 전달한다.

## 실제 실행 전 점검

1. 사용자가 Production 회원 한 건 생성을 별도 고위험 작업으로 명시 승인했는지 확인한다.
2. root shell이 실제 TTY에 연결됐고 `set -x`가 꺼졌는지 확인한다.
3. 현재 release가 OPS-019를 포함하며 Backend image publish와 배포 검증이 완료됐는지 확인한다.
4. `/opt/pawcycle/runtime`과 `/opt/pawcycle/state`가 non-symlink directory, mode `700`인지 확인한다.
5. `current-sha`, `<sha>.images`, runtime `backend.env`와 `.complete`가 regular non-symlink file, mode `600`인지 확인한다.
6. Production MySQL이 running·healthy이고 `pawcycle-production-data` internal network에 연결됐는지 확인한다.
7. `pawcycle-ops020-auth-smoke-member` Container가 남아 있지 않은지 확인한다.
8. 운영 email과 강한 password는 사용자가 그 자리에서 정하며 다른 화면·문서·clipboard에 복제하지 않는다.

값을 출력하지 않고 실행 인자만 구성한다.

```bash
sudo infra/production/create-production-auth-smoke-member.sh \
  --sha '<현재 승인된 40자 SHA>' \
  --backend-image 'ghcr.io/<owner>/<repository>-backend'
```

Wrapper는 root·TTY 검사를 Docker 접근 전에 수행한다. 그 뒤 current release, runtime/state mode, OPS-019 source, SHA tag·OCI revision·registry digest, non-root image user, MySQL health와 data network를 읽기 전용으로 검증한다. 모든 preflight가 성공한 뒤에만 TTY prompt가 나타난다.

## Container 안전 계약

One-shot Container는 digest reference, `--rm --interactive`, restart 없음, port publish 없음, volume mount 없음, `--log-driver none`, Production data network 하나만 사용한다. Root filesystem은 read-only이고 `/tmp` tmpfs, user `pawcycle`, no-new-privileges, all capability drop, memory·CPU·PID limit을 적용한다. 현재 web Backend·MySQL·Frontend·Proxy service를 중지·재시작·변경하지 않는다.

Backend 인자는 non-web type, 정확한 enable `true`, Flyway `false`다. Wrapper는 signal·오류·정상 종료에서 terminal echo를 복구하고 자신이 시작한 고정 이름 Container만 제거한다.

## 성공 판정

stdout이 다음 한 줄과 마지막 newline만 포함하고 종료 코드가 0일 때만 성공이다.

```text
PASS: production auth smoke member created
```

성공 뒤 회원을 삭제하거나 password를 변경하지 않는다. 실제 credential로 OPS-018을 실행하는 것은 별도 고위험 승인 단계다.

## 실패와 중단

- Preflight 오류: 어떤 credential도 입력하지 말고 state·runtime·image·MySQL 계약을 비민감 정보로 재확인한다.
- 입력 오류 또는 Container nonzero: 자동 재시도하지 않는다.
- 중복 email 오류: 기존 row와 hash를 보존하므로 다른 password로 재실행하지 않는다. 승인 credential의 소유 상태를 확인하고 중단한다.
- PASS를 보지 못했지만 회원 생성 여부가 불명확함: 재실행·삭제·password reset·직접 SQL을 하지 않는다. 사용자/Tech Lead와 Backend에 에스컬레이션한다.
- raw Docker·DB 오류가 필요해도 runtime env, JDBC URL, DB 사용자, email, password, hash와 원시 로그를 공유하지 않는다.
- echo가 복구되지 않으면 즉시 `stty sane`으로 terminal만 복구하고 회원 생성 상태는 불명확으로 취급한다.

## 복구와 rollback

실패 transaction은 Backend가 rollback하며 wrapper는 one-shot Container를 정리한다. 성공 회원은 OPS-018 반복 Smoke용으로 유지하므로 자동 rollback·cleanup 대상이 아니다. 회원 삭제·password 변경, schema restore, service rollback은 별도 고위험 승인 없이는 수행하지 않는다.

저장소 변경은 일반 revert PR로 복구할 수 있다. 실제 성공 회원은 코드 revert로 제거되지 않는다.

## 에스컬레이션

Immutable image 확인 불가, current SHA 불일치, runtime/state mode 불일치, MySQL unhealthy, data network 불일치, duplicate 또는 성공 여부 불명확이면 실행을 중단하고 사용자/Tech Lead에게 보고한다. 보고에는 단계와 종료 코드만 포함하고 식별자·credential·원시 오류는 포함하지 않는다.

## 후속 학습

실제 승인 실행 결과는 별도 고위험 작업에서 비민감 증거로 기록한다. OPS-020 저장소 준비 완료를 실제 회원 생성 또는 OPS-018 인증·Session Smoke 완료로 확대하지 않는다.
