# OPS-OCI-CD-005 — Live MySQL Compatibility Observer (G0)

상태: **Repository implementation only / Production OFF**. 저장소 PR은 OCI DB 접근·Secret/계정 생성·Baseline Adopt·Policy·Timer 설치·배포 승인이 아니다. 기존 OPS-OCI-CD-003/004 및 OPS-OCI-CD-02의 Gate를 유지한다.

## 구현

OPS-OCI-CD-006의 별도 opt-in [제한적 색상 변환 모드](OPS-OCI-CD-006-frontend-auto.md)는 root 정책 아래 기계적으로 동작 불변을 입증한 경우에만 pair-proof를 대신한다. 아래의 기본 수동 모드는 변경되지 않으며 자동 검증 실패에 대한 fallback이나 범위 확대 수단이 아니다.

infra/production/oci-compat-observer.py는 설치된 Python 3.9 표준 라이브러리, /usr/bin/mysql 및 /usr/bin/git만 사용한다. 고정 SQL SELECT를 통해 실제 MySQL server UUID·schema·version과 Flyway의 성공 이력, migration filename·checksum을 수집하고 비민감 SHA-256 fingerprint로 묶는다. 데이터 행·credential·mysql 원시 출력을 외부 로그로 내보내지 않는다. 별도의 root 보호 Runtime bundle·Git mirror에서 정확한 SHA 쌍과 migration tree, 변경 파일 hash를 대조한다. 이 제한형 Observer의 자동 허용 변경 경로는 `frontend/src/`·`frontend/public/`뿐이다. Backend, 빌드 입력, CI Workflow, Release 도구, Compose/Nginx, 문서와 미분류 경로가 함께 바뀌면 별도 검증기로 차단한다.

**양방향 앱/데이터 호환은 MySQL/Flyway snapshot만으로 증명되지 않는다.** 실제 격리 MySQL 8.4에서 current→target→current를 검증한 리뷰된 evidence artifact와 운영자가 검증·승인한 exact-pair proof를 요구한다. Observer는 두 파일의 root 보호·hash·만료·SHA 쌍, 인증·주문·결제·재고·정기배송·Frontend API 불변식의 PASS 또는 근거 있는 제외와 실제 live DB snapshot을 재확인해야만 Controller의 JSON 계약을 출력한다. 이런 증거 파일이 없거나 실패하면 종료 코드 1로 차단한다.

## 실행 전 운영자 입력 (모두 별도 승인 필요)

- /opt/pawcycle/cd-source.git: root-owned 0700 bare Git mirror, non-force 갱신 경계 유지.
- /opt/pawcycle/runtime/current: root 보호 .bundle.* symlink; backend.env mode 0600 및 g0-t2a-canonical-v1 완료 marker. 값/내용을 출력하지 않는다.
- /etc/pawcycle/oci-compat-mysql.cnf: root regular 0600. 전용 최소 SELECT 권한 DB 사용자, schema, 승인한 DB private endpoint와 신뢰할 서버 CA(`ssl-ca` 또는 `ssl-capath`)를 지정한다. 실행 시 `--protocol=TCP --ssl-mode=VERIFY_CA`를 강제한다. TLS와 CA 체인 검증은 필수이며 CA 누락·잘못된/만료된 인증서·검증 옵션 미지원·TLS 불가 시 즉시 중단한다. `REQUIRED`/`PREFERRED`/평문·socket으로 재시도하지 않는다. 기존 앱 비밀번호를 복사·출력하지 않는다.
- /opt/pawcycle/compat-evidence/<approved-file>: root regular 0600의 실제 격리 pair report JSON. format=1, current, target, isolation=disposable-mysql-8.4, forward_pass, reverse_pass, reviewed와 각 domain의 result/reason/test_reference가 필수다. 검증하지 않은 결과를 PASS로 쓰지 않는다.
- /etc/pawcycle/oci-compat-proof.json: root regular 0600의 운영자 승인 증거. format=1, current, target, runtime_env_sha256, db_epoch, db_snapshot_sha256, migration_tree, changed_paths_sha256, verified_unix, expires_unix, reviewed_by_operator, evidence 및 semantic_artifact.path/sha256을 연결한다. 최대 승인 수명은 24시간이다. 임의 입력이나 테스트 fixture를 운영 승인으로 사용하지 않는다.
- Observer 설치 실행파일: root regular 0700. Controller policy compatibility_observer 및 observer_sha256에 정확한 설치 bytes를 결합한다.

## OCI 서비스 정의 인증서와 CA 신뢰 경계

OCI 서비스 정의 인증서가 내부 FQDN과 일치하지 않는 배포에서는 서버 인증서를 교체하지 않고 `VERIFY_CA`를 사용할 수 있다. [MySQL 8.0 TLS 옵션 계약](https://dev.mysql.com/doc/refman/8.0/en/connection-options.html#option_general_ssl-mode)에 따라 CA 검증과 암호화는 유지하지만 **hostname identity는 검증하지 않는다**. 같은 신뢰 CA가 발행한 다른 서버 인증서를 구별하는 보장은 없으므로 DB private endpoint 고정, app01의 승인된 source에서 해당 DB TCP 3306만 허용하는 기존 NSG/라우팅 경계와 최소 SELECT 계정을 함께 유지해야 한다. 이 변경은 NSG·DNS·DB 인증서나 계정을 수정하지 않는다. DB UUID/Flyway fingerprint의 기존 drift 검사도 유지하지만 TLS hostname 검증을 대신한다고 주장하지 않는다.

CA는 대상 DB에 연결된 신뢰 가능한 관리 경로에서 확보하고, 해당 DB/인증서와의 연결 근거·CA fingerprint·유효기간을 독립 확인한 뒤 보호 경로에 설치한다. OCI Certificates에서 관리되는 CA이면 권한 있는 [CA version bundle 조회/다운로드](https://docs.oracle.com/en-us/iaas/Content/certificates/viewing-certificate-authority-version-bundles.htm)를 사용할 수 있다. 서비스 정의 CA가 이 API로 노출된다고 가정하거나 HeatWave on AWS의 regional bundle을 OCI에 전용하지 않는다. 관리 경로에서 CA를 확보할 수 없으면 DBA/Oracle 지원을 통해 신뢰 근거를 확보할 때까지 STOP한다. 미검증 TLS handshake에서 받은 CA를 그대로 신뢰하거나 전역 trust store를 확대해 우회하지 않는다.

CA 파일/디렉터리와 ancestor는 root 소유·symlink 및 group/other write 금지로 보호하고, 승인한 CA만 포함한다. 옵션 파일에는 위 보호 경로를 명시한다. 최초 운영 승인은 CA 출처·fingerprint, endpoint/NSG 근거, 실제 MySQL CLI의 CA 검증 성공 및 잘못된 CA/검증 실패 시 차단 근거, 변경된 Observer bytes hash와 복구 경계를 기존 통합 Packet에 묶는다. 이 PR의 fake 회귀는 실제 CA 확보나 live TLS 성공 증거가 아니다. 실패 시 CD OFF를 유지하며 검증 수준을 낮추지 않는다. 이전 Observer 설치본/정책 hash로 복구하면 기존 hostname 불일치 차단이 다시 적용될 수 있다.

## 검증과 남은 작업

격리 회귀는 infra/production/test-oci-compat-observer.py에서 fake Git/MySQL만 사용하며 CI Production contracts lane에 포함한다. 이는 실제 운영 DB 접근·격리 version-pair/비즈니스 의미 검증을 대체하지 않는다. 정확한 운영 MySQL 상태·전용 read-only 계정/TLS·테스트 근거·정상 Baseline/Nginx·Release State Adopt·이미지 매니페스트·capacity·rollback·알림·최초 정책·Timer 모두 미확인/미실행이다.

사용자의 백업 실증 후순위 결정은 유지하되, 기존 Recovery Gate의 예외를 허구의 검증으로 처리하지 않는다. 해당 위험을 명시한 별도 승인 및 안전한 제한 범위 검토 전까지 Production CD는 OFF다.
