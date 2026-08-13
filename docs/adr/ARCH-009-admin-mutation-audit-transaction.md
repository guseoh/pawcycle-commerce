# ARCH-009 관리자 mutation과 audit transaction ownership

## 상태

Accepted (`PR #139`)

## 결정

관리자 inventory adjustment, coupon create/update/issue, membership grade create/evaluate는 `AdminCommerceService` application service가 transaction owner가 된다. 각 use case는 mutation과 `AdminAuditService.append`를 하나의 `@Transactional` 경계에서 수행하며, audit 기록 실패는 전체 mutation rollback으로 이어진다.

Controller는 HTTP mapping, 인증 주체 전달, 요청·응답 변환만 담당한다. `CommerceService`의 기존 domain write 동작과 `AdminAuditService`의 payload·저장 계약은 변경하지 않는다.

## 영향

transaction ownership은 Controller가 아닌 application service에 있으며, API payload·DB schema·migration에는 변화가 없다. 스케줄러·결제 등 비관리자 audit use case의 기존 경계는 이 결정의 대상이 아니다.
