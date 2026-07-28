# OPS-019 Backend → Platform/SRE 인수인계

## 작업 정보

- 작업 ID: OPS-019
- 작업 등급: 고위험

## 전달 목적

Production 인증·session Smoke 전용 회원 한 명을 만들 Backend one-shot command의 실행·보안 경계를 Platform/SRE에 전달한다.

## 대상 역할 또는 운영자

Platform/SRE와 별도 승인을 수행할 사용자

## 입력 문서

OPS-019 Backend 보고서, AUTH-003 승인 인증 계약, AUTH-004 구현 결과와 기존 production 배포 계약이다.

## 완료된 작업

기존 `EmailNormalizer`, `PasswordEncoder`, `MemberRepository`와 transaction을 사용하는 회원 한 건 생성 command와 단위·통합 테스트 기반을 준비했다. Application main의 Bootstrap이 Spring Context 전에 명시적 non-web application type과 정확한 enable flag를 검사하며, 일반 web process에서는 maintenance runner가 생성되지 않는다.

## 사용 가능한 결과

- Enable flag: `pawcycle.maintenance.create-auth-smoke-member.enabled=true`
- 필수 application type: `spring.main.web-application-type=none`
- 표준입력 첫 줄: email
- 표준입력 둘째 줄: password
- 성공 process stdout: 마지막 newline을 포함한 `PASS: production auth smoke member created` 한 줄
- gate·중복·입력·저장 실패: nonzero와 고정된 비민감 오류, 기존 회원 변경 없음
- Maintenance 안전값: Flyway, banner와 startup info 비활성, Context 생명주기 framework stdout·stderr 폐기

실제 credential 값, memberId, hash와 DB 연결값은 이 문서에 없으며 추가해서는 안 된다.

## 관련 파일

- `backend/src/main/java/com/pawcycle/backend/member/maintenance/**`
- `backend/src/test/java/com/pawcycle/backend/member/maintenance/**`
- `docs/reports/OPS-019/be-report.md`

## 확정된 결정

Credential은 CLI 인자·환경 변수·파일이 아니라 표준입력 두 줄로만 전달한다. Email은 기존 인증 정규화 규칙을 사용하고 password는 trim하지 않는다. 동일 email이 있으면 update, password reset, overwrite 없이 실패한다.

Non-web 요청의 enable gate가 누락·false·오타·오값 또는 중복이면 application Context, DataSource, Hikari, Flyway와 JPA 초기화 전에 종료한다. 유효한 maintenance 실행에서도 Bootstrap이 `spring.flyway.enabled=false`를 강제하며 같은 운영자 인자로 다시 활성화할 수 없게 한다.

## 미확정 결정

Production image의 fat jar에서 maintenance main application을 실행하는 wrapper 방식, TTY prompt 구현, container 실행·정리 명령과 실제 실행 시점은 Platform/SRE가 후속 승인 범위에서 정한다.

## 승인 필요 항목

Production DB 연결과 회원 생성, 운영 container 실행, OPS-018 session Smoke는 각각 실제 운영 고위험 사용자 승인 뒤 수행해야 한다. 생성 회원 삭제나 password 변경도 별도 승인 없이는 수행하지 않는다.

## 소비자 검증 포인트

- 일반 web process에서 enable flag 유무와 관계없이 runner가 생기지 않는가
- non-web type의 enable gate가 누락·false·오타·오값이면 Context·DataSource 전에 nonzero로 종료하는가
- 운영자가 Flyway·banner·startup log를 활성화하는 인자를 추가해도 Backend Bootstrap의 안전값이 우선하는가
- TTY에서 email은 대화형으로, password는 echo 없이 정확히 한 번 읽는가
- credential이 argv, environment, 파일, shell history와 container log에 남지 않는가
- 성공 stdout이 PASS 한 줄뿐이고 실패 시 입력·hash·JDBC URL·DB 사용자·원시 예외가 보이지 않는가
- container가 한 번 실행 후 종료하며 web service로 남지 않는가

## 검증 결과

Backend 단위·통합 테스트, build, 고위험 산출물 validator와 Repository Validation의 최종 결과는 OPS-019 보고서와 GitHub Checks를 권위 원본으로 확인한다. 이번 작업에서는 실제 production network·DB·container를 사용하지 않았다.

## 지켜야 할 규칙

Wrapper는 `set -x`를 사용하지 않고 credential을 command substitution, argv, 환경 변수, 일반 파일 또는 log로 전달하지 않는다. 실제 값과 원시 stdin을 출력하지 않는다. 회원 외 table을 변경하거나 직접 SQL·외부 hash를 사용하지 않는다.

## 적용·실행 방법

이번 인수인계는 실행 가능한 운영 wrapper가 아니다. Platform/SRE는 기존 production image·runtime 계약 안에서 non-web application type과 enable flag를 정확히 명시하고, 보호된 TTY에서 표준입력 두 줄을 전달하며, 종료 코드와 고정 PASS를 확인하는 wrapper·Runbook을 별도 작업으로 준비한다. Context 이전 gate, Flyway 차단, framework 출력 억제와 process 결과 형식은 Backend Bootstrap 책임이고, TTY password echo 차단과 container 실행·정리는 SRE 책임이다.

## 실패와 정리 경계

입력 부족, email 오류, 빈 password, 중복과 persistence 실패는 nonzero 종료로 처리한다. Command의 transaction 실패에는 부분 회원이 남지 않아야 한다. 실패했다고 직접 SQL로 정리하거나 재시도 전에 기존 회원을 변경하지 않는다.

성공 회원은 반복 Smoke에 유지한다는 승인 대상이므로 이 command에 cleanup 기능이 없다. 잘못된 성공 생성이나 삭제 필요성이 확인되면 운영을 중단하고 별도 고위험 사용자 결정을 요청한다.

## 알려진 위험

Backend command 자체는 TTY echo를 제어하지 않는다. Application main의 Bootstrap 경계는 검증했지만 실제 production container의 jar 실행 경로와 one-shot 종료는 아직 검증하지 않았다. 강한 password의 구체 복잡도 규칙은 승인되지 않아 command가 추가 정책을 강제하지 않는다.

## 남은 위험과 주의 사항

실제 production DB 연결과 member row 생성, 생성 credential로 로그인 가능한지, session·CSRF 회전과 logout이 정상인지 검증되지 않았다. OPS-019 저장소 준비를 OPS-018 운영 검증 완료로 확대하지 않는다.

## 다음 권장 작업

Platform/SRE가 비공개 TTY wrapper와 Runbook을 준비하고 독립 review를 받은 뒤, 사용자가 별도 승인한 고위험 실행에서 회원 한 건 생성과 OPS-018 Smoke를 순서대로 수행한다.

## 완료 조건

후속 wrapper가 credential 비노출, non-web/enable 이중 gate, one-shot 종료, 고정 PASS·nonzero failure와 재실행 중복 거부를 검증 가능하게 보존한다.

## 중단 조건

Credential을 argv·환경 변수·파일로 전달해야 하거나, web application을 활성화해야 하거나, 직접 SQL·외부 hash·회원 외 데이터 쓰기·schema 변경이 필요하면 중단하고 Backend와 사용자에게 보고한다.
