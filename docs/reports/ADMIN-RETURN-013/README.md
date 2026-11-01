# ADMIN-RETURN-013 — 반품 입고 취소 안전성

2026-11-01 · 고위험 · 저장소 변경 · 기준 main c55e0a57f1efa09a604c78f517db527196781137

## D8 변경

- RECEIVE_RETURN의 browser confirm을 명시적 확인 dialog로 교체. 재고 복원/복원 안 함을 radio로 선택하고 입고 확정과 작업 취소를 분리.
- 대상 reference ID 표시, 취소 초기 focus, native dialog와 Escape. 서버 payload와 availableActions 유지.
- 동기적으로 잠기는 executor가 완료 전 중복 실행을 차단. 취소(null)는 요청하지 않음. 실패 후 명시 재시도 가능.
- 기존 action명의 한국어 label. 고객 theme 적용 없음.

## 검증

- lint: 0 errors, 기존 6 warnings. typecheck PASS. npm test 185/185 PASS (추가 executor 회귀 2개).
- lockfile 그대로 독립 설치 후 npm run build PASS. 초기 외부 junction build 실패는 설치로 해결.
- Chrome 1440×900, loopback 합성 Admin fixture: 재고 복원 안 함을 선택하고 취소 → 요청 기록 [].
- 입고 확정 빠른 두 번 클릭 → [{restock:false}] 1건. 이어 재고 복원 확정 → {restock:true} 1건 추가.
- 모든 요청은 fixture 메모리에만 기록. 실제 Backend/재고/반품/결제 처리 없음.

![확인 화면](receipt-confirmation-1440.jpg)

## 한계/복구

실제 관리자 권한/서버 transaction·재고 상태 전이, 전체 예외 응답은 미검증. API·업무 정책 변경 없음. 복구는 이 Admin commit의 일반 revert PR. Production 조작 없음.
