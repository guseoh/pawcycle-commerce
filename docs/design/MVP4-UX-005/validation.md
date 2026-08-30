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

R0 PNG13개와 원래 JPG30개는 보존했다. R1 PNG21개를 추가했다: Home/PLP/PDP/Cart/Checkout/Login Desktop·Mobile12개, Order Detail/Subscription New/Detail6개, Identity1개, 상태2개. 현재 시각 검토본은 R1이며 기존 box 보드만으로 승인 요청하지 않는다. R1 실제 Commerce JPG5개를 추가해 캡처 총35개다. 가상 패키지 생성 원본1개를 별도 assets에 보존했다. 기존 raster artifact 총70개의 해상도·SHA-256은 [artifact manifest](artifact-manifest.json)에 기록한다.

최종 승인 전 보정에서 UTF-8 text vector SVG2개를 추가했다: [multi-brand catalog stress](visuals/r1-catalog-stress.svg), [orbit small-size](visuals/r1-small-mark.svg). 두 파일은 실제 상품 이미지가 아니라 구조/광학 크기를 검토하는 벡터 보드이므로 기존 raster SHA-256 manifest의 70개 집계와 분리한다. 따라서 현재 디자인 시각 artifact는 **raster70 + vector2 = 72개**다.

시각 검토에서 chevron/minus 미지원 glyph, mobile Footer 영역 부족을 발견해 vector chevron·읽히는 minus·페이지 높이로 보정했다. Login compact shell, mobile Cart 진입을 유지했다. 구독 nextDelivery의 effective 주기를 pending과 일치시키고, PlanVersion에 없는 사진/상품명과 Schedule에 없는 order link는 제거했다. 원본 R0 이미지는 다시 렌더하거나 덮어쓰지 않았다. 이 보정은 디자인 산출물에만 적용했다.

재현: `render-high-fidelity.py`는 Python/Pillow로 R1 raster 정적 PNG만 생성한다. 새 Frontend/HTML/runtime 서버가 아니다. 이미 제공된 bundled Python/Pillow와 Windows Malgun Gothic을 사용했으며 설치·의존성 변경 없음. imagegen 원본은 별도 저장했고 실제 카탈로그에 넣지 않았다. 최종 보정 SVG2개는 GitHub UTF-8 text asset으로 작성했으며 renderer나 Frontend dependency를 추가하지 않는다.

## 최종 승인 전 보정 검증

- [R1 final check](review-r1-final-check.md)에 PB imagery 의존 위험과 orbit small-size 판정을 명시했다.
- heterogeneous board는 빨강/파랑/초록/노랑/검정, 세로 pouch/원통/bottle/box, 긴 brand/name, `image=null`, 구매 불가 상태를 섞어 R1의 neutral image stage와 정보 위계를 확인하도록 구성했다.
- orbit board는 32/24/20/16px를 분리해 20px를 최소 UI mark로 제안하고 16px orbit 사용을 금지했다. 별도 simplified favicon 승인 전 기존 favicon을 유지한다.
- `interaction-responsive.md`의 Login Header를 R1 compact 계약(모바일64/desktop88)으로 정정하고, 옛 `5개 대표 화면` 문구를 핵심6개+주문/구독3개+최종 보드 범위로 갱신했다.
- `screen-redesign.md`는 R0 역사 기록으로 축약하고 `ARCHIVED R0 STRUCTURE ONLY — DO NOT IMPLEMENT DIMENSIONS`를 명시해 Header80/56 등 stale 수치를 구현 계약에서 제거했다.
- 위 최종 보정은 GitHub branch 직접 수정으로 수행했다. 기존 로컬 `git diff --check`/task artifact validator를 이 추가 변경에 대해 다시 실행했다고 주장하지 않는다. 대신 각 UTF-8 파일의 GitHub round-trip과 SVG XML well-formedness를 확인하고, PR 상태/HEAD/변경 경로를 다시 확인한다.

## 미실행과 남은 위험

- 사용자 Screenshot: NOT PROVIDED. 직접 Production 재캡처로 핵심 대조가 충족되었으므로 R1에서 Design Approval blocker에서 제거. 재첨부를 요구하지 않음.
- Production populated PDP·인증 Cart/Checkout·mutation feedback: UNVERIFIED. 상품 데이터/인증 없이 재현했다고 주장하지 않음.
- 실제 브라우저에서 새 디자인의 keyboard, drawer trap, zoom, screen reader, state transitions: NOT IMPLEMENTED / NOT TESTED. 정적 시각·명세 검토만 수행.
- frontend build·backend test·dependency install: 코드/환경 변경이 없어 미실행.
- 실제 상품 이미지·브랜드 서명·새 폰트·B editorial 콘텐츠·Cart thumbnail 보강·추가 정책/회원 기능: 별도 승인 대상. R1의 생성 imagery는 가상 예시, PB 출시 결정 아님. C bottom dock는 기존 MVP4 PO 결정과 충돌하여 C 선택 시 별도 결정 변경 필요.
- R1 외부 benchmark는 실제 Login/Cart/Checkout 화면까지 확인했으나 로그인 성공/최종 결제/배송비 확정/각 mobile 폭/keyboard 전수검증은 미실행. 외부 비회원 조사 Cart1개 담기 외 개인정보 입력·결제 제출 없음.
- orbit mark는 20px 최소 사용을 제안했지만 실제 브라우저 rasterization·고DPI·favicon rendering은 FE 구현 후 확인해야 한다. 16px orbit favicon은 현재 승인하지 않는다.
- heterogeneous stress board는 가상 데이터 기반 정적 검토다. 실제 multi-brand catalog fixture, 투명 PNG, 긴 혼합문자 상품명, 200% zoom에서의 최종 PASS는 구현 후 검증한다.
- 현재 main push는 Production image build와 자동 배포로 연결되는 workflow가 있다. **이 문서 PR도 향후 main에 병합하면 배포 workflow를 유발할 수 있다.** merge/workflow dispatch/실제 운영 변경은 수행하지 않았다.
- **Design Approval은 완료됐다.** 현재 GitHub PR은 Ready 상태(`draft=false`)지만 merge와 Frontend 구현, Production 실행은 별도 Gate로 남는다.
- CI 메타데이터 보정: PR 본문 필수 `검증`/`위험과 복구` 계약을 복구했다. 이전 Repository Validation 실패 job 재실행은 최초 pull_request 이벤트의 옛 PR 본문을 재사용해 같은 실패가 반복됐으므로, 이 문서 정합성 수정 커밋으로 새 HEAD의 Repository Validation을 트리거한다.

## 기계 검증 결과

R1 기존 로컬 검증 PASS: UTF-8 Markdown9개, 상대 링크/anchor136개, raster 이미지70개(관찰35/시안34/생성원본1) decode·확장자 일치·SHA-256 manifest 확인. renderer Python syntax, PR body UTF-8/task artifact validator PASS. 새 제품 코드 없음.

최종 승인 전 보정은 repository-side 실행 없이 문서/SVG만 변경했다. 따라서 기존 PASS를 새 변경 전체의 재실행 결과로 확대하지 않는다. 보정 후 검증은 GitHub 파일 조회, 변경 경로 확인, PR/merge 상태 확인, SVG XML well-formedness 확인으로 제한한다.

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
