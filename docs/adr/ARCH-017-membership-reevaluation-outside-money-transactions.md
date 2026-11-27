# ARCH-017: 멤버십 재평가를 돈 트랜잭션 밖으로 분리

## 상태

Accepted — 사용자 결정 2026-11-19(재평가 요청 기록 + 정기 작업). 구현: PAYMENT-MEMBERSHIP-001.

## 날짜

2026-11-19

## 작업 ID

`PAYMENT-MEMBERSHIP-001`

## 맥락(Context)

결제 확정(`PaymentApplicationService.applyNormalSuccess`), 결제 대사(`PaymentReconciliationService`), 정기결제 완료(`SubscriptionBillingProcessor.completeSuccess`), 환불 완료(`RefundService`)는 돈 상태를 바꾸는 트랜잭션의 마지막에 `MembershipEvaluationService.evaluate`를 호출했다.

평가는 `SELECT ... FROM member_memberships WHERE member_id=? FOR UPDATE` 뒤 `INSERT`(또는 `UPDATE`)다. 멤버십 행이 없는 회원은 이 SELECT가 gap lock을 잡는다. 행이 없는 회원 두 명이 동시에 첫 결제를 확정하면 같은 gap의 gap lock을 함께 쥔 채 서로의 insert intention을 기다려 교착이 난다. MySQL이 한쪽을 롤백하면 **결제 확정 트랜잭션 전체**가 롤백된다. 결제사 승인은 이미 끝났으므로 결제는 PROCESSING, 주문은 PAYMENT_PENDING으로 남는다(#417의 고립 결제와 같은 상태).

이 현상은 PERF-SCALE-002 4단계 H의 1만 규모 혼합 부하 예열 구간에서 재현됐다. backend 로그(`CannotAcquireLockException`)와 `SHOW ENGINE INNODB STATUS`의 최근 교착 기록으로 확인했다. 기존 `MembershipEvaluationWriteProtocolIntegrationTests`는 같은 회원의 동시 INSERT 교착만 고정하고 있었다.

## 결정(Decision)

- 돈 트랜잭션 안에서는 멤버십을 평가하지 않는다. 대신 `MembershipReevaluationService.request(memberId)`가 같은 트랜잭션에 `membership_reevaluation_requests` 행 하나를 upsert한다(`Propagation.MANDATORY`). 이 쓰기는 `member_memberships` gap lock을 잡지 않는다.
- 일반 구매 성공 대사는 빠른 재주문과 동일하게 회원 행을 먼저 잠근다. 결제·주문·재고·장바구니를 잠그기 전에 회원 행에 공유 잠금을 잡아, 재평가 요청의 외래 키 검사가 빠른 재주문의 회원 잠금과 순서를 뒤집지 않도록 한다.
- `MembershipReevaluationTrigger`(기본 켜짐, 10초 간격, 기본 scheduler thread)가 `MembershipReevaluationService.processDue`로 기한이 된 요청을 오래된 순서로 꺼낸다. 회원마다 **자기 트랜잭션**에서 평가하고, 같은 트랜잭션에서 요청을 지운다.
- 요청은 `request_version`을 가진다. 평가 중 새 요청이 들어오면 version이 올라가 삭제 조건(`request_version = 읽은 값`)이 맞지 않으므로 요청이 남고, 다음 실행에서 최신 결제 금액으로 다시 평가한다.
- 평가가 실패하면 요청을 남기고 `attempts`를 올리며 `next_attempt_at`을 뒤로 민다(10초부터 두 배씩, 최대 1시간). 계속 실패하는 요청이 다른 요청을 막지 않는다.
- 관리자 재평가(`MembershipAdminApplicationService`)는 지금처럼 동기로 둔다.

## 결과(Consequences)

- 정기 작업의 멤버십 평가 실패는 이미 커밋된 결제 확정·대사·정기결제·환불을 롤백하지 않는다.
- 요청은 돈 변경과 원자적으로 커밋되므로 서버가 재시작돼도 사라지지 않는다.
- 요청 경로에서 멤버십 평가용 커넥션을 추가로 잡지 않는다. 커밋 직후 콜백에서 새 트랜잭션을 여는 방식은 원래 커넥션을 쥔 채 두 번째 커넥션을 잡아, 기본 풀 10개에서 고갈 위험이 있어 택하지 않았다.
- 정기 작업이 한 번에 한 회원씩 처리하므로 요청 경로끼리의 gap 교착과 같은 회원의 동시 평가가 사라진다. 관리자 재평가와 겹치는 경우만 남고, 이때는 잠금 실패로 기록하고 다음 실행에서 다시 한다.
- **등급 반영이 즉시가 아니라 작업 주기(기본 10초) 안으로 늦어진다.** 등급은 연간 누적 결제 금액 기준이라 실사용 영향이 작다고 판단했다.
- backend 인스턴스가 여러 대가 되면 같은 요청을 두 작업이 동시에 처리할 수 있다. 지금은 한 대다. 여러 대로 늘릴 때 `FOR UPDATE SKIP LOCKED` 같은 점유 방식을 다시 결정한다.

## 대안(Alternatives)

| 대안 | 기각 이유 |
| --- | --- |
| 커밋 직후 콜백(`afterCommit`)에서 `REQUIRES_NEW`로 평가 | 원래 커넥션 반납 전에 두 번째 커넥션을 잡고, 커밋과 콜백 사이에 서버가 죽으면 재평가가 사라진다 |
| 교착 시 결제 확정 재시도 | 확률만 줄이고 원인을 남긴다 |
| 가입 시 멤버십 행 미리 생성 | 가입 흐름과 backfill migration이 크고, 행이 빠진 회원이 생기면 재발한다 |

## 범위 밖(별도 후속)

- `MembershipEvaluationService.evaluate`가 결제 금액을 잠금 전에 읽어, 같은 회원 평가가 겹치면 오래된 금액으로 덮어쓸 수 있는 경합. 정기 작업 경로에서는 동시 평가가 없어 줄어들지만, 관리자 재평가와 겹치는 경우가 남는다.
- 최근 365일 기준이 지나 등급이 내려가야 할 때의 정기 재평가 정책.

## 검증

`MembershipReevaluationIntegrationTests`(실제 MySQL)와 기존 결제·대사·환불·정기결제 테스트. 회귀 테스트는 다른 트랜잭션이 `member_memberships` gap lock을 쥔 상태에서도 결제 성공 확정이 기다리지 않고 끝나는지 확인한다.
