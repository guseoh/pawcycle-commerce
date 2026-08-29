# MVP4-UX-003 Final Product UI/UX Audit

- 작업 ID: `MVP4-UX-003`
- 역할: UX/UI Designer
- 기준 branch: `design/ux/MVP4-UX-003`
- 기준 main SHA: `3110d50fab0347a2b82f0c1af17768ed45b6951c`
- 감사일: 2026-08-29 (Asia/Seoul)
- 감사 방식: 현재 구현 소스 확인 + disposable 로컬 환경의 실제 Browser 감사 + 공식 서비스 벤치마크
- Browser viewport: Desktop `1440×900`, Mobile `375×812`, minimum width `320×700`
- 제외: Frontend/Backend 구현, API/DB/schema 변경, 새 기능·의존성, Production/AWS/RDS/Toss/AI Provider 실행

## 1. Executive Summary

PawCycle은 warm utility commerce 방향, 서버 권위 상태, 반복 구매와 정기배송의 연결, 인증 보호 화면, 반응형 기초가 이미 안정적이다. 실제 Browser에서 320px까지 문서 단위 가로 overflow가 없었고, 주요 버튼은 대부분 44px 이상이며 focus indicator와 skip link도 제공된다. 주문 재담기, 주문→정기배송 prefill, 주기 제안, one-time add-on, HELD 조치, 알림 routing, USER 거부와 ADMIN readback도 기능 구조가 이해 가능했다.

그러나 현재 화면은 “완성된 상거래 서비스”와 “검증용 제품 데모” 사이에 남아 있다. 가장 큰 원인은 고객 화면에 노출되는 구현 언어(`서버`, `Provider`, `SKU`, raw enum), fixture 표식, 비교 화면의 구매 가능 상태 불일치, HELD의 목록 우선순위 부족, 결제 단계의 모호한 CTA, 인증 없는 추천의 오류 노출이다. 이 7개는 Product Complete 전에 해소해야 한다.

감사 결과는 **P0 7개 / P1 15개 / P2 8개, 총 30개**다. 다음 구현은 단일 `MVP4-FE-004 — Final Product UI/UX Polish`로 묶고 **P0 전부 + 전환·신뢰·모바일 조작에 직접 영향을 주는 P1 10개**를 우선한다. 기본 전제는 Backend/API/DB 변경 없음이다. 비교 상태 불일치가 표시 계층이 아닌 API 응답 자체의 불일치로 판명될 때만 Backend handoff를 별도로 연다.

## 2. Current Product Assessment

### 현재 강점

- warm cream + green palette가 pet commerce에 적합하고 과도하게 유아적이지 않다.
- 페이지 shell, 4/3/2/1 상품 grid, 2단 checkout, 상세 sticky purchase action이 상거래 기본 흐름을 만든다.
- 1440, 375, 320px에서 문서 전체 가로 overflow가 발생하지 않았다.
- server-authoritative `availableActions`, 금액, 재고, 주기 선택지를 임의 추론하지 않는 구조가 화면에도 반영되어 있다.
- loading, empty, error, success 상태와 재시도 경로가 대부분 존재한다.
- focus-visible, skip link, semantic heading/region/list/table 사용이 양호하다.

### 현재 완성도 판단

| 축 | 판단 | 근거 |
| --- | --- | --- |
| Visual system | 양호 | warm utility 방향과 밀도 개선이 반영됨 |
| Commerce trust | 보완 필요 | 상태 불일치, fixture/구현 문구, 이미지 의미 불일치 |
| Repeat commerce | 강점 | 주기·다음 배송·add-on·HELD·reorder 연결이 한 제품 안에 존재 |
| Mobile | 기능적으로 안정 | overflow 없음, 그러나 필터·비교·긴 관리 화면의 조작 비용이 큼 |
| Accessibility | 기본기 양호 | focus/semantic 구조 양호, accessible name·motion·일부 target 개선 필요 |
| Final-product feel | 보완 필요 | demo/provider/server/raw enum 흔적이 고객 경험을 끊음 |

## 3. External Benchmark Findings

2026-08-29 기준 공식 공개 페이지를 검토했다. 화면을 복제하지 않고 정보 우선순위와 상태 표현 패턴만 참고한다.

| 서비스 | 확인한 패턴 | PawCycle 적용점 |
| --- | --- | --- |
| [Chewy Autoship](https://www.chewy.com/b/autoship-save-15682) | 다음 주문, 주기, skip, order now, 일정 변경, one-time add-on을 고객 언어로 분리 | 다음 배송 카드에 날짜·금액·변경 마감·핵심 action을 먼저 제시하고 관리 기능을 그 아래로 분리 |
| [Petco Autoship](https://www.petco.com/shop/en/petcostore/autoship) | next date, skip, 빈도·수량 조정, add-on을 한 upcoming delivery 단위로 관리 | PawCycle의 SCHEDULED add-on 계약은 유지하되 “이번 배송만” 범위를 시각적으로 강화 |
| [PetSmart Autoship](https://www.petsmart.com/learning-center/autoship/how-petsmart-autoship-works) | 사전 알림, 날짜·수량·skip·cancel, 품절 시 대안 선택을 명확한 통제감으로 설명 | HELD를 내부 상태가 아니라 “배송 전 확인이 필요한 일”로 목록부터 강조 |
| [Amazon Business Subscribe & Save](https://business.amazon.com/en/blog/subscribe-and-save) | 다음 배송 전 가격 알림, skip/cancel/update, reminder, backup option | 가격 변동·결제 실패·재고 이슈를 다음 배송 카드에 선제적으로 노출 |
| [올리브영 온라인몰](https://www.oliveyoung.co.kr/store/main/main.do?oy=0) | 상품 카드에 가격·할인·리뷰·배송 badge와 찜/장바구니 action을 고밀도로 제공 | 카드 안의 구매 판단 정보와 action을 한 덩어리로 묶고 이미지 의미 정확도를 높임 |
| [쿠팡 장바구니](https://checkout.coupang.com/) / [상품 목록 예시](https://www.coupang.com/np/campaigns/13135/components/195510) | 빈 장바구니 회복, 배송 약속, 단위 가격, ranking 설명 등 구매 판단 정보를 즉시 제공 | PawCycle 카드·장바구니에 단위 정보와 배송/재고 메시지를 고객 언어로 간결화 |

공통점은 자동 구매를 숨기지 않고 다음 발생 시점, 변경 가능 범위, 실패 시 다음 행동을 먼저 보여준다는 점이다. PawCycle은 계약과 기능은 이미 갖췄지만, 고객 표현과 우선순위가 아직 구현 개념 중심이다.

## 4. What Already Works

- Header는 상품·DOG·CAT·카테고리·정기배송·주문을 primary로, 찜·장바구니·알림·내 정보를 utility로 분리한다.
- 홈은 상품 탐색, 반복 구매 가치, 추천, 정책 링크를 모두 제공한다.
- 상품 목록은 검색, pet type, category, brand, price, subscription, purchasable, sort, compare를 제공한다.
- 상세는 옵션/SKU 조합을 서버 결과로 선택하며 재고, 가격, subscription 가능 여부를 함께 갱신한다.
- 비교는 2~3개 제한, 표 caption, row/column header를 갖는다.
- 장바구니 수량은 draft와 “수량 적용”을 분리해 accidental mutation을 막는다.
- Checkout은 주소·쿠폰·서버 확정 금액을 한 화면에서 검토한다.
- 주문 상세은 상태·상품·금액·after-sales·배송지·reorder를 구조화한다.
- 주문→정기배송은 현재 판매 플랜, pet, 서버 허용 주기를 사용한다.
- 정기배송은 다음 배송, package snapshot, one-time add-on, server-authoritative action을 보존한다.
- cycle suggestion은 command를 자동 전송하지 않고 선택값만 채운다.
- HELD는 add-on 제거 가능 여부와 availableActions를 서버 기준으로 제한한다.
- 알림은 `subscriptionId`가 있을 때 구독 상세로 routing한다.
- USER는 관리자 API에서 거부되고, session 갱신 후 ADMIN catalog/operations readback이 동작한다.
- Browser console warning/error는 감사 종료 시 0건이었다.

## 5. Global UI System

### 유지할 방향

- max content width `1180~1240px`, cream base, deep green action, 8/10/12px 중심 radius.
- primary action은 한 영역에 하나, destructive action은 별도 구획.
- 44px 운영 target을 PawCycle 권장값으로 계속 사용한다. WCAG 2.2 AA 최소 target은 24×24 CSS px 또는 spacing 예외이며, 44×44는 enhanced 기준이다. [W3C Target Size (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html), [W3C WCAG 2.2](https://www.w3.org/TR/WCAG22/)
- 현재 `:focus-visible` 3px outline과 semantic HTML은 유지한다.

### 개선 원칙

1. 고객 화면에는 구현·계약·provider 이름이 아니라 결과·시점·다음 행동을 쓴다.
2. 동일 사실은 한 표현으로 번역한다. raw enum은 never-render 목록으로 관리한다.
3. 상태가 위험할수록 목록에서 먼저 보인다. 상세에 들어가야만 알 수 있게 하지 않는다.
4. 긴 관리 화면은 “다가오는 배송 → 필요한 조치 → 일반 변경 → 해지” 순으로 progressive disclosure한다.
5. 상품 이미지는 이름/카테고리와 의미가 맞아야 하며, placeholder는 일관된 중립 자산으로 제한한다.
6. 모든 async mutation은 loading → success/error → recovery를 가까운 위치에서 보여준다.
7. `prefers-reduced-motion`에서 pulse/transition을 제거한다.

## 6. Home

홈은 시각적으로 안정적이지만 인증 모바일 기준 전체 높이가 약 `9,556px`이고, 새 상품·꾸준한 상품·인기·트렌딩·맞춤 추천이 연속되어 가치 차이가 흐려진다. 실제 fixture에서는 인기와 트렌딩이 동일한 4개 상품을 반복했다. personalized가 페이지 후반에 있어 로그인 가치가 늦게 드러난다.

권장 순서:

1. compact hero + primary 상품 탐색
2. authenticated personalized / anonymous pet discovery
3. replenishment 또는 “꾸준히 필요한 것들” 한 섹션
4. category/brand discovery
5. popular 또는 trending 중 데이터 차이가 있는 한 섹션
6. subscription value + trust links

Hero의 3개 CTA는 Desktop에서 허용되지만 Mobile에서는 primary 1개 + secondary text link 2개로 위계를 줄인다. `MY ROUTINE` 카드는 로그인 여부에 따라 “내 다음 배송” 또는 “정기배송 알아보기”로 실제 상태를 반영한다.

## 7. Product Discovery / Detail / Compare

- 목록의 filtering 기능은 충분하다. Mobile에서는 필터가 inline으로 전체를 밀어내고 적용 버튼이 fold 밖으로 사라지므로 bottom sheet 또는 full-height drawer가 적합하다.
- 카드의 이미지와 상품 의미가 맞지 않는 사례가 반복됐다. 예: 스카프 카드에 개 얼굴, 고양이 칼라 카드에 사료 그릇 이미지. 이는 visual polish보다 product trust 문제다.
- 카드의 “비교하기”는 42px이며 카드 본문과 분리되어 있다. 44px 이상으로 올리고 카드 action 영역 안에 통합한다.
- 상품 상세은 Desktop 약 `5,581px`, 320px 약 `6,686px`로 길고, 배송/반품/정기배송 정보가 요약과 본문에 반복된다. 구매 결정 영역, 핵심 상세, 신뢰 정보, 리뷰/Q&A, 추천 순으로 접는다.
- public detail의 complementary 추천이 `인증이 필요합니다` 오류로 끝난다. 로그인 없이 가능한 fallback 또는 조용한 unavailable 상태가 필요하다.
- 320px 비교는 table wrapper 덕분에 문서 overflow는 없지만 첫 상품만 보이고 horizontal scroll 안내가 없다. 첫 열 sticky, “좌우로 밀어 비교” 안내, 상단 product switcher 또는 snap point가 필요하다.
- 동일 상품이 목록/상세에서는 구매 가능·정기배송 가능인데 비교에서는 구매 불가·정기배송 불가로 나타났다. 표시 전 API 사실 일치 여부를 검증하고 불일치 시 비교 셀을 숨기지 말고 오류 상태로 fail closed한다.

## 8. Cart / Checkout / Payment

- Cart의 explicit “수량 적용”과 unavailable blocking은 유지한다.
- Cart의 이미지가 실제 product image 대신 `PawCycle` placeholder로 보였다. 가능한 경우 snapshot thumbnail을 사용하고, 없으면 동일 비율의 중립 placeholder를 쓴다.
- Checkout Desktop 2단 구조는 명확하다. Mobile에서도 320px overflow 없이 순차 흐름이 유지됐다.
- “주문 생성”은 시스템 operation처럼 들리고 이후 Toss 단계가 남아 있는지 알 수 없다. CTA는 현재 단계의 실제 효과를 반영해야 한다. 예: `19,900원 주문 준비` → 주문 생성 후 provider가 준비되면 `결제하기`, provider 미연결이면 구매가 완료되지 않았음을 즉시 명시한다.
- 성공/실패 페이지는 order 상태, charge 여부, 다음 행동을 첫 문장에 제공한다. query 누락 상태는 현재처럼 fail closed한다.
- 공지·결제수단 페이지의 `Toss Browser Provider Client`, `authKey`, “별도 승인 범위”는 고객 화면에서 제거한다.

## 9. Repeat Commerce / Subscription

- 목록 첫 row의 HELD subscription이 “이용 중 / 예정 없음”으로만 보여 조치 필요성이 묻힌다. `배송 보류 · 확인 필요`를 상태 badge와 row-level action으로 올린다.
- 상세의 `SCHEDULED`, `HELD`는 `배송 예정`, `배송 보류`로 번역한다. raw value는 analytics/debug에만 남긴다.
- 다음 배송 카드는 날짜, 예상 금액, 구성, issue, cutoff/action 순으로 보여준다.
- add-on은 “이번 배송만” badge를 search/result/selected/add/remove feedback에 반복해 plan 변경과 혼동을 막는다. `SKU` label은 `옵션`으로 바꾼다.
- cycle suggestion의 median 설명은 정확하지만 기술적이다. `최근에는 약 2주마다 구매했어요. 다음 배송 주기를 2주로 바꿔볼까요?`처럼 행동 결과를 먼저 쓴다. 제안 적용은 selection만 바꾸고 별도 저장 CTA를 유지한다.
- 구독 관리의 skip/pause/date/cycle/plan/cancel을 한 long form에 모두 노출하지 않는다. `다가오는 배송`, `배송 일정`, `상품 구성`, `구독 상태` accordion/section으로 나누고 해지는 맨 아래 danger zone에 둔다.
- order→subscription은 플랜마다 pet/cycle form을 반복하지 말고 플랜 선택 후 공통 pet/cycle summary에서 prefill을 확인한다.
- 신규 구독 화면은 existing pet 선택과 inline 새 pet 등록 form이 동시에 보여 단계가 혼재한다. `등록된 반려동물 선택`과 `새 반려동물 등록`을 분기한다.

## 10. My / Pet / Order / Notification / Address / Billing

- My는 snapshot과 management hub가 유용하다. 다만 HELD, unread, 결제수단 문제 같은 urgency를 숫자보다 먼저 보여주는 “지금 확인할 일” 영역이 필요하다.
- `MANAGEMENT` eyebrow가 연속 두 번 반복된다. `쇼핑 관리`와 `계정 설정`으로 명명한다.
- Reorder timing은 최근 구매/예상 시점/상품 보기가 간결하다. 계산 근거는 숨기고 “예상”임을 유지한다.
- 주문 상세은 주문/결제/배송 상태를 반복한다. 현재 단계 timeline 하나와 예외 action으로 압축한다. `배송지 정보 없음`은 단순 누락인지 historical snapshot 부재인지 고객이 이해할 수 있는 안내와 지원 경로를 제공한다.
- Pet/Address는 조회와 편집 label이 명확하다. 하지만 create form이 항상 열려 있어 기존 항목 관리와 경쟁한다. `추가` 버튼 뒤 progressive disclosure를 사용하고, 삭제는 대상·영향을 확인하는 dialog를 사용한다.
- Notification reminder는 `2026-08-31T00:00:00.000Z`가 그대로 보였다. KST 기준 `2026. 8. 31.`로 표시하고 생성 시각과 예정일을 시각적으로 분리한다.
- Billing은 등록 여부와 provider 준비 여부를 분리한 점은 좋다. 고객 CTA는 “등록 준비”가 아니라 가능한 실제 결과를 말하고, 미지원 상태에서는 disabled CTA보다 지원 예정/대체 행동을 안내한다.

## 11. Admin

- USER는 API에서 거부됐지만 admin shell, tab, 재시도 버튼이 함께 보였다. 동일한 access-denied page로 통일하고 admin data/action shell은 렌더링하지 않는다.
- ADMIN session 갱신 후 catalog 101개와 operations empty readback은 정상 동작했다.
- Desktop의 list + editor split은 효율적이다. Mobile에서는 101개 list와 create form이 세로로 이어져 작업 context를 잃기 쉽다. list → detail/editor route 또는 drawer로 분리한다.
- ADMIN에게 admin entry가 일반 header/My에서 발견되지 않는다. 역할이 확인된 경우에만 My의 별도 “관리자 도구” link를 제공한다.
- `Facet`, `SKU`, `PUBLIC`은 admin에서는 허용하되 첫 사용 시 짧은 설명과 위험 action confirmation을 제공한다.

## 12. Responsive / Accessibility

### 확인 결과

- `1440×900`, `375×812`, `320×700`에서 document-level horizontal overflow 없음.
- 주요 form controls 42~47px, 주요 buttons 44px 이상. 상품 카드 compare button은 42px로 PawCycle 권장값보다 작음.
- skip link, `:focus-visible`, landmark, heading, table scope/caption, aria-live/status/error가 존재.
- 320px product detail의 sticky purchase action은 긴 상세에서 회복 경로를 제공.

### 개선

- Mobile menu accessible name이 `메뉴메뉴`로 계산된다. sr-only와 visible label 중 하나만 이름에 포함하고 open 시 `메뉴 닫기`로 명확히 바꾼다.
- compare table은 keyboard/touch horizontal scroll 안내와 sticky row header를 제공한다.
- inline filter를 focus-trapped drawer로 바꾸는 경우 닫기, Escape, trigger focus return을 보장한다.
- pulse animation과 transition에 `prefers-reduced-motion: reduce`를 제공한다.
- 200% text resize와 320px에서 sticky header/CTA가 content를 가리지 않는지 회귀 테스트한다.
- 색 대비는 WCAG 2.2 AA normal text 4.5:1 기준으로 token 단위 검증한다. [W3C Contrast](https://www.w3.org/WAI/WCAG22/Techniques/general/G18)

## 13. P0/P1/P2 Matrix

| ID / 화면 | 우선순위 | 문제 | 사용자 영향 | Reference pattern | PawCycle 제안 | 구현 영향 | 보존 계약 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UX-P0-01 / Notice·Support·Shipping·Checkout·Subscription | P0 | `서버`, `Provider`, `별도 승인 범위`, `snapshot` 등 구현 문구 노출 | 실제 서비스가 아닌 내부 데모로 인식 | Chewy/PetSmart의 결과·시점 중심 설명 | 고객 결과·다음 행동 중심 copy로 전면 교체 | FE copy/constants | 서버 권위 사실은 유지, 기술 명칭만 숨김 |
| UX-P0-02 / Subscription·Add-on·Plan | P0 | `SCHEDULED`, `HELD`, `SKU`, `[QA OPS-031]` raw label 노출 | 상태 이해 실패, fixture 신뢰 저하 | Petco/Chewy의 plain-language status | never-render map + `옵션`, `배송 예정/보류`, internal label fallback | FE formatter/filter | raw value·stable ID·API payload 불변 |
| UX-P0-03 / Notifications | P0 | 예정일 full ISO 문자열 노출 | 시간대·날짜 오해 | 사전 알림의 local date 강조 | KST date formatter와 예정일/수신일 분리 | FE formatter/test | `subscriptionId` routing 유지 |
| UX-P0-04 / Compare | P0 | 목록/상세와 비교의 purchasable/subscription 사실 불일치 | 구매 결정 오류 | 커머스 비교의 동일 source-of-truth | compare response 사실 검증, 불일치 시 row error/fail closed | 우선 FE; API 응답 불일치면 BE handoff | 비교 API와 selected product IDs 유지 |
| UX-P0-05 / Subscription list | P0 | HELD가 `이용 중·예정 없음`으로만 표시 | 필요한 조치를 놓침 | PetSmart OOS notification | `배송 보류·확인 필요` badge, issue summary, detail CTA | FE list presentation | subscription status와 schedule status 분리 유지 |
| UX-P0-06 / Checkout·Billing | P0 | `주문 생성`과 provider 미지원 경계가 모호 | 결제 완료 여부 오해 | 결제 단계별 명확한 CTA | 금액 포함 단계 CTA, 주문 생성 후 charge 여부/다음 단계 명시 | FE state/copy | server checkout/confirm 순서·idempotency 유지 |
| UX-P0-07 / Public product detail | P0 | complementary recommendation이 인증 오류로 노출 | 탐색 단절, 장애 인식 | public recommendation fallback | anonymous-safe fallback 또는 section graceful unavailable | FE error/fallback; 필요 시 API handoff | AI unavailable 정상 상태, provider detail 비노출 |
| UX-P1-01 / Home | P1 | 추천/상품 섹션 반복, personalized가 후반 | 탐색 피로, 로그인 가치 약화 | concise retail home hierarchy | personalized/replenishment 우선, duplicate feed 축소 | FE composition | recommendation attribution/request 유지 |
| UX-P1-02 / Product cards·Cart | P1 | 상품명과 이미지 의미 불일치, cart generic placeholder | 상품 식별·신뢰 저하 | 국내몰의 image-led card | 의미 일치 image mapping, snapshot thumbnail, 중립 fallback | FE/content fixture | image URL nullable 계약 유지 |
| UX-P1-03 / Mobile filters | P1 | inline 확장으로 apply/reset이 fold 밖 | 필터 완료 비용 증가 | mobile full-height filter drawer | drawer + active filter count + sticky apply/reset | FE layout/state | URL filter serialization·raw query 비저장 유지 |
| UX-P1-04 / Mobile compare | P1 | horizontal scroll affordance 없음 | 2·3번째 상품 발견 어려움 | sticky comparison axis | sticky first column, swipe hint, snap/scroll cue | FE CSS/semantics | 2~3 unique product 제한 유지 |
| UX-P1-05 / Product card compare | P1 | compare action 42px, card와 분리 | touch miss, action 맥락 약화 | 44px in-card utility action | 44px 이상, card action row 통합 | FE CSS/component | compare selection maximum 3 유지 |
| UX-P1-06 / Product detail | P1 | 설명·배송·반품·정기배송 반복, 매우 긴 page | 구매 결정 전 피로 | product buy box → details → trust | 중복 제거, anchored/accordion detail, sticky buy 유지 | FE composition | selected SKU authoritative 유지 |
| UX-P1-07 / Mobile header | P1 | accessible name `메뉴메뉴`, sticky header 높이 큼 | screen reader 혼란, content viewport 축소 | compact mobile commerce header | 단일 accessible label, search collapse option | FE header/a11y | primary/utility navigation 유지 |
| UX-P1-08 / Order detail | P1 | 상태 반복, `배송지 정보 없음` recovery 부재 | 현재 단계·지원 행동 판단 지연 | order timeline + exception | 하나의 status timeline, missing snapshot 안내/지원 link | FE presentation | order/payment/delivery facts 분리 유지 |
| UX-P1-09 / Order→Subscription | P1 | 플랜마다 pet/cycle form 반복 | 비교와 입력 부담 | select plan then shared configuration | plan cards → 공통 pet/cycle → final prefill summary | FE state/layout | 현재 판매 plan·allowed cycles만 사용 |
| UX-P1-10 / Subscription detail | P1 | skip/pause/date/cycle/plan/cancel 동시 노출 | destructive action 혼동, 긴 스크롤 | upcoming shipment first | delivery/action grouping, progressive disclosure, danger zone | FE composition | `availableActions`만 렌더링 |
| UX-P1-11 / Cycle suggestion | P1 | median/allowed weeks 중심 기술 문구 | 추천 의미 이해 어려움 | benefit-first recommendation | 최근 사용 패턴→추천 결과→별도 저장 순 | FE copy | 제안 적용은 command 전송 금지 |
| UX-P1-12 / My | P1 | urgent issue보다 숫자 snapshot 우선, eyebrow 반복 | HELD/unread 대응 지연 | account action center | `지금 확인할 일` 우선, 그룹명 구체화 | FE composition | aggregate API 값 유지 |
| UX-P1-13 / Pet·Address·Subscription create | P1 | 조회와 create/edit form이 동시에 경쟁 | 잘못된 폼 편집, 화면 길이 증가 | progressive disclosure management | add/edit drawer 또는 explicit mode, delete confirm | FE interaction | null clear·validation·CSRF 유지 |
| UX-P1-14 / Admin access·discovery | P1 | USER에게 admin shell 노출, ADMIN entry 없음 | 거부 화면 혼란, 관리자 진입 불가 | role-gated tool entry | uniform 403 page, ADMIN-only My link | FE auth/presentation | backend authorization 최종 권위 유지 |
| UX-P1-15 / Motion·async | P1 | pulse에 reduced-motion 대응 없음, 일부 long fetch가 dot 하나 | motion 민감·layout 예측 어려움 | reduced motion + structural skeleton | media query, section-shaped skeleton, local retry | FE CSS/state | request semantics 불변 |
| UX-P2-01 / Global copy | P2 | English eyebrow/aria label 혼용 | 편집 완성도 저하 | consistent editorial system | Korean-first eyebrow glossary, aria label 언어 통일 | FE copy | semantic labels 유지 |
| UX-P2-02 / Global icons | P2 | `→`, `↗`, `×`, text icon 혼용 | 브랜드·조작성 일관성 저하 | one icon set | 기존 dependency 없이 inline SVG set 정리 | FE assets | accessible name 유지 |
| UX-P2-03 / Product price info | P2 | unit price·saving amount 정보 약함 | 반복 구매 비용 비교 어려움 | unit economics near price | 가능 데이터만 단위 가격/절감액 표시 | FE presentation; API 추가는 별도 결정 | client price 계산 금지 |
| UX-P2-04 / Home category·brand | P2 | category internal scroll, brand text grid 밀도 | 탐색 구조 파악 지연 | curated top categories | top 6 + 전체 보기, brand mark consistency | FE composition | category/brand API 유지 |
| UX-P2-05 / Wishlist empty | P2 | 상품 목록 link만 제공 | recovery personalization 약함 | empty state with relevant discovery | pet category/recent route 1~2개 제공 | FE links | 추천 API 추가 없음 |
| UX-P2-06 / Payment result | P2 | 성공/실패 recovery가 최소 | 안심·지원 정보 부족 | charge/order/retry triage | charge 여부, order link, support link, retry 조건 | FE copy/layout | confirm result authority 유지 |
| UX-P2-07 / Admin terminology | P2 | Facet/SKU/PUBLIC 설명 없음 | 신규 운영자 학습 비용 | contextual admin help | inline glossary/tooltips | FE admin copy | admin domain terms·IDs 유지 |
| UX-P2-08 / Editorial details | P2 | 날짜·상태·영문 casing·punctuation 편차 | 세부 완성도 저하 | shared formatter/content lint | date/status/casing copy standard | FE utilities/tests | timezone Asia/Seoul 기준 |

## 14. MVP4-FE-004 Recommended Scope

### 작업

`MVP4-FE-004 — Final Product UI/UX Polish`

### 반드시 포함

- P0 7개 전부.
- 아래 P1 10개를 1차 구현:
  - Home 우선순위/중복 축소 (`P1-01`)
  - 상품 이미지·cart thumbnail fallback (`P1-02`)
  - Mobile filter drawer (`P1-03`)
  - Mobile compare affordance (`P1-04`)
  - card compare target (`P1-05`)
  - product detail 중복 축소 (`P1-06`)
  - mobile header accessible name (`P1-07`)
  - order→subscription 공통 configuration (`P1-09`)
  - subscription management grouping (`P1-10`)
  - ADMIN access/discovery (`P1-14`)

### 기본 구현 경계

- Frontend-only: pages/components/CSS/copy/formatter/tests.
- 새 dependency 없음. 현재 Next.js/React/CSS 구조 재사용.
- API payload, DB schema, backend state transition, idempotency, CSRF, authorization 변경 없음.
- 비교의 사실 불일치가 frontend mapping이 아니라 API 응답에서 재현될 때만 bug/handoff를 만들고 FE에서 값을 임의 보정하지 않는다.
- Product Complete 선언은 FE-004 구현·독립 QA·CI 이후 Tech Lead가 판단한다.

### 최소 인수 기준

1. 고객 화면에 raw enum, `SKU`, fixture/demo/provider/승인 범위 문구가 없다.
2. 목록·상세·비교의 purchasable/subscription 사실이 일치한다.
3. HELD가 구독 목록에서 조치 필요로 식별된다.
4. Checkout CTA와 결과 화면이 charge/order/provider 상태를 오해 없이 설명한다.
5. public recommendation unavailable이 오류처럼 보이지 않는다.
6. 375/320px filter·compare·subscription·checkout·admin에서 overflow와 hidden primary action이 없다.
7. keyboard focus, Escape/focus return, reduced motion, 44px PawCycle target을 회귀 테스트한다.
8. server-authoritative price/status/actions와 cycle suggestion no-command 계약을 보존한다.

## 15. Deferred P2

`P2-01`~`P2-08`은 FE-004 1차 범위에서 제외한다. P0/P1 구현 중 동일 파일을 수정하더라도 opportunistic 확장을 하지 않는다. 단, formatter나 icon primitive처럼 P0 구현에 필수인 공통 기반은 최소 범위로 만들 수 있다.

P2는 다음 시점에 재평가한다.

- 실제 catalog content/image 운영 기준 확정 후 editorial pass
- 결제 Provider 실제 연결 승인 후 payment result polish
- 운영자 onboarding 요구가 생긴 뒤 admin glossary
- 추천 데이터 차별화가 확보된 뒤 home merchandising 고도화

## 16. Open Product Decisions

| ID | 결정 필요 사항 | 선택지 | 기본 권고 |
| --- | --- | --- | --- |
| PD-UX-003-01 | Checkout 첫 CTA가 order 생성인지 payment 시작인지 | `주문 준비` 후 별도 결제 / 단일 `결제하기` | 현재 2단 계약을 유지하되 CTA와 완료 여부를 명시 |
| PD-UX-003-02 | 상품 이미지의 최종 운영 source와 승인 기준 | catalog owner upload / curated mapping / neutral placeholder | 의미 불일치 이미지는 노출 금지, neutral fallback 허용 |
| PD-UX-003-03 | Home popular와 trending을 둘 다 유지할지 | 둘 다 / 차이가 없으면 하나만 | 실제 결과 차이가 없으면 하나만 노출 |
| PD-UX-003-04 | ADMIN 진입 위치 | My의 role-gated link / 별도 URL만 | role-gated `관리자 도구` link |
| PD-UX-003-05 | HELD 대체 상품 선택을 이번 polish에 포함할지 | 현재 remove/support만 / 대체 선택 추가 | 새 기능이므로 FE-004 제외, 현재 availableActions만 표현 |

결정 전에도 P0의 copy translation, status priority, formatter, fallback, compare consistency investigation은 진행할 수 있다.

## 17. Reviewed Routes / Browser / External References

### 실제 Browser에서 검토한 route

- Global: Header, category menu state, search, Footer, login, authenticated utility badges
- Home: `/`
- Discovery: `/products`, filter open, product search/sort state
- Detail/Compare: `/products/1`, `/compare?productId=1&productId=2&productId=3`
- Cart/Checkout/Payment: `/cart`, `/checkout`, `/checkout/success`, `/checkout/fail`
- Orders: `/orders`, `/orders/3`, `/orders/6`, reorder mutation feedback, order→subscription prefill
- Subscription: `/subscriptions`, `/subscriptions/1`, `/subscriptions/2`, `/subscriptions/new`, pet/plan selection, cycle suggestion, add-on search/option state, HELD action state
- My/Account: `/my`, `/pets` view/edit, `/wishlist`, `/notifications`, `/addresses` view/edit, `/billing-methods`
- Support: `/shipping`, `/returns`, `/faq`, `/notice`, `/support`
- Admin: `/admin/catalog`, `/admin/operations` as USER and refreshed ADMIN session

### Browser/환경 사실

- disposable Compose project: `pawcycle-mvp4-final-qa`
- disposable volume: `pawcycle-mvp4-final-qa-mysql-data`
- local URL: `http://localhost:8083`
- actual login session 사용, credential 값 미출력
- Desktop `1440×900`, Mobile `375×812`, minimum `320×700`
- fixture prepare/verify 후 시작, Browser console warning/error 0건
- Production/AWS/RDS/Toss/AI Provider 미실행

### External references

- [Chewy Autoship](https://www.chewy.com/b/autoship-save-15682)
- [Petco Autoship](https://www.petco.com/shop/en/petcostore/autoship)
- [PetSmart Autoship](https://www.petsmart.com/learning-center/autoship/how-petsmart-autoship-works)
- [Amazon Business Subscribe & Save](https://business.amazon.com/en/blog/subscribe-and-save)
- [올리브영 온라인몰](https://www.oliveyoung.co.kr/store/main/main.do?oy=0)
- [쿠팡 장바구니](https://checkout.coupang.com/)
- [쿠팡 상품 목록 예시](https://www.coupang.com/np/campaigns/13135/components/195510)
- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)
- [WCAG 2.2 Target Size (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html)
- [WCAG Focus Visible Technique](https://www.w3.org/WAI/WCAG22/Techniques/general/G195)

본 문서는 설계·감사 산출물이며 제품 코드 구현이나 Product Complete 선언이 아니다.
