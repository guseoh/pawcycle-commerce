# 검증과 제한

작업 MVP4-UX-005 / 일반 / 저장소 변경. 디자인 문서와 정적 보드 검증이며 Frontend 기능 QA가 아니다.

## 검증 범위

- origin fetch 후 main `bec817d` 확인, 최신 main에서 별도 task worktree 생성. 기존 FE branch와 모든 사용자 파일 보존.
- 경로별 AGENTS: root 적용. tracked docs/design 전용 AGENTS 없음. UX role·Lean Harness 확인.
- Production/Reference 직접 브라우저 관찰 결과·URL·캡처: [감사](production-audit.md), [Benchmark](commerce-benchmark.md).
- 추가 파일은 docs/design/MVP4-UX-005 아래. frontend/backend/API/DB/infra/workflow 기존 파일 변경 없음.
- 기존 design 문서 삭제/승인변경 없음. 새 시각 조항만 SUPERSEDED 후보, 기능 계약 유지.
- UTF-8 Markdown·상대 파일 링크/heading anchor·이미지 해석 가능 여부·캡처 SHA-256은 최소 로컬 검사로 확인한다.
- PR 본문은 저장소 UTF-8 및 task artifact validator, diff는 `git diff --cached --check`로 확인한다.

## 시각 확인

R0 PNG13개와 원래 JPG30개는 보존했다. R1 PNG21개를 추가했다: Home/PLP/PDP/Cart/Checkout/Login Desktop·Mobile12개, Order Detail/Subscription New/Detail6개, Identity1개, 상태2개. 현재 시각 검토본은 R1이며 기존 box 보드만으로 승인 요청하지 않는다. R1 실제 Commerce JPG5개를 추가해 캡처 총35개다. 가상 패키지 생성 원본1개를 별도 assets에 보존했다. 총70개 이미지의 해상도·SHA-256은 [artifact manifest](artifact-manifest.json)에 기록한다.

시각 검토에서 chevron/minus 미지원 glyph, mobile Footer 영역 부족을 발견해 vector chevron·읽히는 minus·페이지 높이로 보정했다. Login compact shell, mobile Cart 진입을 유지했다. 구독 nextDelivery의 effective 주기를 pending과 일치시키고, PlanVersion에 없는 사진/상품명과 Schedule에 없는 order link는 제거했다. 원본 R0 이미지는 다시 렌더하거나 덮어쓰지 않았다. 이 보정은 디자인 산출물에만 적용했다.

재현: `render-high-fidelity.py`는 Python/Pillow로 정적 PNG만 생성한다. 새 Frontend/HTML/runtime 서버가 아니다. 이미 제공된 bundled Python/Pillow와 Windows Malgun Gothic을 사용했으며 설치·의존성 변경 없음. imagegen 원본은 별도 저장했고 실제 카탈로그에 넣지 않았다.

## 미실행과 남은 위험

- 사용자 Screenshot: NOT PROVIDED. 직접 Production 재캡처로 핵심 대조가 충족되었으므로 R1에서 Design Approval blocker에서 제거. 재첨부를 요구하지 않음.
- Production populated PDP·인증 Cart/Checkout·mutation feedback: UNVERIFIED. 상품 데이터/인증 없이 재현했다고 주장하지 않음.
- 실제 브라우저에서 새 디자인의 keyboard, drawer trap, zoom, screen reader, state transitions: NOT IMPLEMENTED / NOT TESTED. 정적 시각·명세 검토만 수행.
- frontend build·backend test·dependency install: 코드/환경 변경이 없어 미실행.
- 실제 상품 이미지·브랜드 서명·새 폰트·B editorial 콘텐츠·Cart thumbnail 보강·추가 정책/회원 기능: 별도 승인 대상. R1의 생성 imagery는 가상 예시, PB 출시 결정 아님. C bottom dock는 기존 MVP4 PO 결정과 충돌하여 C 선택 시 별도 결정 변경 필요.
- R1 외부 benchmark는 실제 Login/Cart/Checkout 화면까지 확인했으나 로그인 성공/최종 결제/배송비 확정/각 mobile 폭/keyboard 전수검증은 미실행. 외부 비회원 조사 Cart1개 담기 외 개인정보 입력·결제 제출 없음.
- 현재 main push는 Production image build와 자동 배포로 연결되는 workflow가 있다. **이 문서 PR도 향후 main에 병합하면 배포 workflow를 유발할 수 있다.** 이번 작업은 task branch push와 Draft PR까지만, Ready/merge/workflow dispatch/실제 운영 변경은 하지 않는다.
- Design Approval 미승인. 문서 검증 통과는 시각 승인·FE 착수·병합 승인이 아니다.

## 기계 검증 결과

R1 로컬 검증 PASS: UTF-8 Markdown9개, 상대 링크/anchor136개, 이미지70개(관찰35/시안34/생성원본1) decode·확장자 일치·SHA-256 manifest 확인. renderer Python syntax, PR body UTF-8/task artifact validator PASS. 새 제품 코드 없음.

| 조합 | 계산 대비 | 기준 |
| --- | ---: | ---: |
| brand / white | 11.52:1 | 4.5:1 |
| ink / white | 16.40:1 | 4.5:1 |
| muted / surface | 5.28:1 | 4.5:1 |
| brand / apricot | 6.62:1 | 4.5:1 |
| error / soft | 5.87:1 | 4.5:1 |
| sale / soft | 5.53:1 | 4.5:1 |
| control border / white | 3.89:1 | 3:1 |

위 계산은 지정 sRGB 두 색의 상대 휘도 검사다. 실제 font rasterization·사진 배경·focus clipping·모든 조합·WCAG 전체 준수를 보증하지 않는다.
