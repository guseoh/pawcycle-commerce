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

13개 PNG 보드: 세 방향 비교1, 5개 화면 Desktop/Mobile10, 상태1, overlay1. 실제 제품 사진 대신 명시적 시안 슬롯 도식. 캡처 JPG 30개는 외부/Production 증거이고 보드와 분리했다. 각 파일 해상도·해시는 [artifact manifest](artifact-manifest.json)에 기록한다.

자체 시각 검토에서 unsupported glyph를 발견해 검색 label·선택 표시·minus를 읽을 수 있는 표기로 보정했다. PLP 비교 진입을 보드에 보강하고 mobile Cart Footer가 action bar에 가려지지 않도록 보드 공간을 확보했다. 이 보정은 디자인 산출물에만 적용했다.

## 미실행과 남은 위험

- 사용자 Screenshot: NOT PROVIDED, 대조 미실행.
- Production populated PDP·인증 Cart/Checkout·mutation feedback: UNVERIFIED. 상품 데이터/인증 없이 재현했다고 주장하지 않음.
- 실제 브라우저에서 새 디자인의 keyboard, drawer trap, zoom, screen reader, state transitions: NOT IMPLEMENTED / NOT TESTED. 정적 시각·명세 검토만 수행.
- frontend build·backend test·dependency install: 코드/환경 변경이 없어 미실행.
- 실제 상품 이미지·새 폰트·B editorial 콘텐츠·Cart thumbnail 보강·추가 정책/회원 기능: 별도 승인 대상.
- 현재 main push는 Production image build와 자동 배포로 연결되는 workflow가 있다. **이 문서 PR도 향후 main에 병합하면 배포 workflow를 유발할 수 있다.** 이번 작업은 task branch push와 Draft PR까지만, Ready/merge/workflow dispatch/실제 운영 변경은 하지 않는다.
- Design Approval 미승인. 문서 검증 통과는 시각 승인·FE 착수·병합 승인이 아니다.

## 기계 검증 결과

로컬 검증 PASS: UTF-8 Markdown 7개, 상대 링크/anchor 83개, JPG 캡처 30개와 PNG 시안 13개 decode·SHA-256 manifest 확인. PR body UTF-8/task artifact validator PASS.

| 조합 | 계산 대비 | 기준 |
| --- | ---: | ---: |
| primary / white | 6.95:1 | 4.5:1 |
| ink / canvas | 16.27:1 | 4.5:1 |
| secondary text / surface | 5.45:1 | 4.5:1 |
| ink / accent | 13.47:1 | 4.5:1 |
| success / soft | 5.88:1 | 4.5:1 |
| warning / soft | 6.21:1 | 4.5:1 |
| error / soft | 5.87:1 | 4.5:1 |
| control border / white | 3.63:1 | 3:1 |

위 계산은 지정 sRGB 두 색의 상대 휘도 검사다. 실제 font rasterization·사진 배경·focus clipping·모든 조합·WCAG 전체 준수를 보증하지 않는다.
