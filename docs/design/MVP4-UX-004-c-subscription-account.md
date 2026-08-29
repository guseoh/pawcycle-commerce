# MVP4-UX-004 C. 정기배송·계정·지원

상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`

## 적용 범위와 원칙

대상은 `/subscriptions*`, 호환 별칭 `/mvp2/subscriptions*`, `/my`, `/pets`, `/notifications`, `/addresses`, `/billing-methods`, `/login`, `/notice`, `/faq`, `/support`, `/shipping`, `/returns`다.

정기배송 **목록**은 `V2SubscriptionSummary`가 제공하는 `subscriptionId`, `pet`, top-level `status`, `currentSnapshot`, `nextScheduledDate`만 권위로 사용한다. `nextDelivery`, `pendingChange`, `issue`, `availableActions`, `ETag`는 **상세** 응답에서만 사용한다. `RESCHEDULE_NEXT`, `CHANGE_DELIVERY_CYCLE`, `CHANGE_PLAN`을 하나의 “배송 변경”으로 합치지 않는다. 2/4/8주 외 주기를 만들지 않고, Schedule의 `HELD`를 top-level Subscription status처럼 표현하지 않으며, 결제 재시도 기능을 암시하지 않는다.

## C1. Subscription List `/subscriptions`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `GET /api/v2/subscriptions`는 page 형태의 `V2SubscriptionSummary`를 반환하며 `/mvp2/subscriptions`가 같은 기능으로 연결된다. Summary에는 `issue`, `availableActions`, `nextDelivery`가 없다. |
| 문제 | Detail 전용 상태를 목록에서 사용하려고 각 구독 Detail을 N번 추가 조회하면 API/성능 계약이 암묵적으로 바뀐다. `HELD`를 top-level 상태로 분류하면 실제 `ACTIVE + nextDelivery.status=HELD`를 잘못 표현한다. |
| 레퍼런스 | Petco·PetSmart·Pet Valu의 다음 날짜·skip·주기·취소 설명은 `INDIRECT/ADAPT`; 실제 계정 UI는 `UNVERIFIED`. PawCycle list 계약은 `frontend/src/lib/v2-api.ts`의 `V2SubscriptionSummary`가 권위다. |
| 최종 IA | heading+설명+새 정기배송→`진행 중(ACTIVE)`→`일시정지(PAUSED)`→`종료(CANCELED)` group. 각 행은 Pet/현재 Snapshot 요약, 현재 주기, 다음 예정일, package price, 상태, `상세 보기`만 표시한다. |
| visual hierarchy | ACTIVE는 `다음 예정일`이 1차, 주기/가격이 2차. PAUSED는 상태와 `상세 보기`가 1차. CANCELED는 낮은 대비지만 읽기 가능하게 두고 재개 가능성을 추정하지 않는다. |
| 컴포넌트 | `SubscriptionGroup`, `SubscriptionRow`, `CycleLabel`, `CreateSubscriptionLink`, `DetailLink`. List에 `IssueBanner`, `PrimaryAvailableAction`을 두지 않는다. |
| interaction | row 전체 clickable 금지, `상세 보기` 명시. list에서 날짜·주기·plan·상태 command를 직접 실행하지 않는다. issue/action을 확인하려면 Detail에 진입한다. |
| navigation | detail route. legacy 별칭으로 들어와도 기능·focus가 동일해야 하며 route 정규화가 history를 불필요하게 두 번 쌓지 않는다. |
| loading | group 구조를 임의 추정하지 않는 3행 skeleton. 인증 확인 중 empty 금지. |
| empty | 구독 0건: 일반 구매와 차이를 설명하고 `/subscriptions/new` CTA. ACTIVE가 없고 PAUSED/CANCELED만 있으면 각 group의 사실만 표시한다. |
| error/retry | 목록 실패 section retry. Summary만으로 렌더링하므로 list row별 Detail 추가 요청 실패 상태를 만들지 않는다. 401은 안전한 로그인 복귀. |
| success | 서버 `nextScheduledDate`, status, snapshot만 고객 언어로 표시한다. 서버에 없는 issue/action을 추정하지 않는다. |
| responsive | desktop 행 grid, mobile은 상태→다음 예정일→Pet/주기/가격→상세 CTA 순 compact block. |
| accessibility | group heading, 상태 색+아이콘+텍스트, 날짜 `<time>`, 상세 action 이름에 Pet 또는 subscription context 포함. |
| gap·impact | card 중심 목록을 Summary 범위의 상태 group+행으로 정리한다. API 변경 없음. |
| acceptance | 목록 하나를 렌더링하기 위해 N개의 Detail 요청을 만들지 않고, top-level `HELD`나 list-level issue/action을 표시하지 않으며, 다음 예정일과 주기를 혼동하지 않는다. |

## C2. Subscription Detail `/subscriptions/[subscriptionId]`

### 최종 정보 구조

```text
Breadcrumb / top-level 상태 / 핵심 다음 행동
Detail issue 또는 pending change summary
다음 배송: 날짜 · Schedule 상태 · 이번 배송 add-on
반복 설정: Plan · 2/4/8주 주기 · 기본 상품/수량(read-only)
배송지 문제 해결/변경 진입
활동/변경 기록
건너뛰기 · 일시정지/재개 · 취소 등 server availableActions
지원
```

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | Detail은 `nextDelivery`, `pendingChange`, `issue`, `availableActions`, ETag와 v2 command를 제공한다. Subscription shipping은 별도 `PUT /api/subscriptions/{id}/shipping-address`에 전체 `AddressRequest`를 보내며 응답 body는 없고, `V2SubscriptionDetail`에도 현재 배송지 read-back 필드는 없다. |
| 문제 | 날짜·주기·Plan 변경, pending/current, top-level Subscription status와 Schedule HELD, 배송지 저장 주소와 subscription shipping mutation을 한 form/상태로 섞으면 서버 계약을 잘못 해석한다. 특히 shipping PUT 성공 후 현재 배송지 값을 서버에서 다시 읽었다고 가장하면 실제 API를 넘어선다. |
| 레퍼런스 | PetSmart 공식 설명의 날짜·skip·quantity·cancel 관리는 `INDIRECT/ADAPT`; 실제 PawCycle command/state contract가 우선한다. 이메일/푸시·자동 대체·결제 재시도는 `UNSUPPORTED`. |
| 최종 IA | status header→issue/pending→다음 배송→반복 설정(Plan/주기)→기본 상품(read-only)→배송지 문제 해결/변경 action→history→danger actions. 날짜, 주기, Plan, 배송지는 서로 다른 편집 surface/command다. 현재 배송지 summary는 read-back API가 생기기 전 만들지 않는다. |
| visual hierarchy | Detail `issue` 또는 pendingChange가 있으면 상단 persistent banner. 정상은 다음 배송 예정일이 가장 크고 `N주마다`, Plan/가격은 분리된 label. destructive action은 맨 아래. |
| 컴포넌트 | `SubscriptionStatusHeader`, `IssueResolution`, `PendingChangeSummary`, `NextDeliveryPanel`, `RescheduleDialog`, `CycleDialog`, `ChangePlanDialog`, `NextDeliveryAddOns`, `SubscriptionItems`, `ShippingAddressDialog`, `ActivityList`, `DangerActions`. `CurrentShippingAddressSummary`처럼 서버 read-back을 전제한 component는 만들지 않는다. |
| 날짜 변경 | `RESCHEDULE_NEXT`가 `availableActions`에 있을 때만 노출한다. 현재 날짜·서버 제약→새 날짜 확인→command 한 개 제출. 다른 주기/Plan을 함께 바꾸지 않는다. |
| 주기 변경 | `CHANGE_DELIVERY_CYCLE`이 있을 때만 2/4/8주 중 현재 effective Plan이 허용하는 값만 표시한다. 추천 주기는 정보/선택 보조일 뿐 자동 command가 아니다. 사용자 확인→`deliveryCycleWeeks` body로 한 command를 제출한다. |
| Plan 변경 | `CHANGE_PLAN`이 있을 때만 현재 Pet과 호환되는 판매 중 PlanVersion을 조회한다. effective current/pending 배송 주기를 지원하는 후보만 선택 가능하다. 변경 전/후 `planName`, package price, items, delivery cycle, 적용 예정 시점을 비교한다. command body의 핵심은 `planVersionId`; legacy subscription에 pet이 없고 서버가 요구하는 경우에만 해당 소유 Pet의 `petId`를 보완한다. 기본 Plan item quantity는 사용자가 임의 편집하지 않는다. |
| Plan conflict | `ADDON_CONFLICTS_WITH_PLAN`이면 다음 배송 add-on을 먼저 제거해야 함을 설명하고 자동 제거하지 않는다. 다음 Schedule이 HELD이면 서버가 Plan/주기/날짜 등 schedule mutation을 거절할 수 있으므로 disabled command를 추정 노출하지 않고 최신 `availableActions`/issue를 다시 따른다. |
| add-on | add-on은 **다음 배송 1회**에만 적용되는 별도 command다. 수량 변경 의미는 add-on의 실제 `quantity`에만 사용하며 기본 Plan item quantity 변경으로 읽히지 않게 한다. |
| 배송지 변경 | Subscription에 `addressId`를 연결한다고 표현하지 않는다. 저장 주소를 활용하면 저장 주소 선택→필드를 local `AddressRequest` draft로 복사→사용자 확인→full `AddressRequest` submit 순서다. 저장 주소 선택만으로 자동 mutation하지 않는다. 직접 입력 역시 동일 `AddressRequest` form을 사용한다. 성공한 PUT 뒤에는 제출 성공 사실과 재조회된 Detail의 `issue/availableActions` 변화만 서버 확인 사실로 표시한다. 제출한 주소 문자열을 현재 서버 배송지 read-back 결과처럼 영구 summary에 사용하지 않는다. |
| conflict/duplicate | command dialog를 열 때 받은 ETag/최신 state를 기준으로 `If-Match`와 Idempotency-Key를 사용한다. 412/409이면 draft를 자동 재전송하지 않고 최신 current/pending/issue를 보여준 뒤 사용자가 다시 선택한다. 동일 body의 명시적 재시도에서만 동일 intention key를 유지한다. |
| navigation | list breadcrumb. 저장 주소 관리를 위해 `/addresses`로 이동할 수 있으나 복귀만으로 Subscription shipping을 자동 변경하지 않는다. `/billing-methods`는 issue 해결을 위한 provider 상태 확인 route일 수 있으나 결제 재시도를 자동 실행하지 않는다. |
| loading | header/핵심 날짜 skeleton, actions는 Detail의 `availableActions` 로드 전 숨김. Plan 후보, add-on, history는 독립 상태로 처리한다. |
| empty | add-on 없음 `이번 배송에 추가한 상품 없음`; history 없음 축소; Plan 후보 없음은 별도 empty. 현재 배송지 read-back 데이터가 없다는 사실을 `배송지 없음`으로 오해해 empty state를 만들지 않는다. 핵심 subscription 없음은 404/list CTA. |
| error/retry | core 실패 page retry. action 실패 dialog 내 retry. `ACTIVE + nextDelivery.status=HELD`는 top-level status를 `HELD`로 바꾸지 않고 issue/배송 회차 보류를 고객 문장으로 설명한다. |
| success | command 성공 뒤 최신 Detail/ETag를 권위로 갱신한다. pendingChange가 있으면 `현재`/`적용 예정`과 적용일을 함께 표시한다. shipping 성공은 `배송지 변경 요청이 반영되었습니다`처럼 mutation 성공을 알리고 Detail을 재조회해 issue/availableActions가 바뀌었는지 확인하되, 현재 배송지 값을 서버가 반환했다고 표현하지 않는다. |
| responsive | D8 SSOT: 1024px 이상 main 8+summary 4, 1023px 이하 single. 날짜·주기·Plan의 짧은 선택은 desktop 480–560px dialog, mobile에서는 내용 복잡도에 따라 full-screen. 배송지처럼 field가 많은 편집은 767px 이하 full-screen dialog. |
| accessibility | dialog title/description, radio/fieldset, 날짜 input label/constraint/error, Plan 비교 heading, pending `status`, issue `alert`, destructive confirm focus trap. |
| gap·impact | 날짜/주기/Plan/shipping을 실제 command/API 단위로 분리하고 ETag conflict·pending·issue 표현을 고정한다. API 변경 없음. |
| acceptance | 날짜 변경이 주기/Plan을 바꾸지 않고, Plan 변경이 기본 item quantity 편집으로 변질되지 않으며, 저장 주소 선택만으로 subscription shipping이 자동 mutation되지 않고, shipping 성공 후 서버에 없는 current address를 read-back 값처럼 표시하지 않으며, stale submit이 자동 재실행되지 않는다. |

## C3. New Subscription `/subscriptions/new`

현재 create request의 입력은 **`petId`, `planVersionId`, `deliveryCycleWeeks` 세 값뿐**이다. 첫 배송일, 배송지, 결제수단, product/SKU, Plan item quantity는 create 입력이 아니다.

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `v2Api.pets`, Pet 호환 `plans.list/detail`, `v2Api.subscriptions.create({petId, planVersionId, deliveryCycleWeeks})`, order subscription option context를 이용한다. legacy alias도 같은 화면으로 연결된다. |
| 문제 | 주문 상품·수량·첫 배송일·배송지·결제수단을 create form에 넣으면 현재 API보다 넓은 제품 기능을 가장한다. 추천/prefill을 자동 command로 적용하면 사용자 선택을 건너뛴다. |
| 레퍼런스 | PetSmart/Petco의 반복 배송 설명은 `INDIRECT/ADAPT`; 실제 생성 화면은 `UNVERIFIED`. PawCycle create request가 최우선 권위다. |
| 최종 IA | 진입 context 확인→Pet 선택/필요 시 등록→해당 Pet과 호환되는 판매 중 Plan 조회→Plan 선택·상세 재검증→서버가 허용한 배송 주기 선택→생성 summary→idempotent create→생성된 Detail. |
| visual hierarchy | Pet/Plan/주기 선택이 순서대로 1차다. 생성 전 summary는 Pet, Plan name, package price, Plan items(read-only), 선택 주기를 보여준다. **다음 배송일은 생성 전에 추정하지 않고 생성 성공 후 서버 Detail에서 처음 확정 표시한다.** |
| 컴포넌트 | `PetSelector`, `CompatiblePlanSelector`, `PlanSummary`, `CycleSelector`, `CreateSummary`, `CreateButton`. `ItemQuantity`, `DeliveryDate`, `AddressSummary`, `BillingSummary`를 create 입력으로 사용하지 않는다. |
| detailed interaction | 일반 신규 진입에서는 cycle을 자동 선택해 command하지 않는다. order context의 `petId/planVersionId/deliveryCycleWeeks`가 있으면 prefill 후보로만 사용하고 현재 판매·Pet 호환·allowed cycle을 다시 검증한다. prefill된 선택은 화면에 명확히 보이며 사용자가 생성 전 확인할 수 있다. productId/skuId context가 있어도 create body에 직접 넣지 않는다. |
| navigation | Recommended Default 주 진입점은 주문 상세의 `정기배송으로 다시 받기`. cancel은 source order로, direct 진입은 list로. success는 생성된 Detail이다. 이 주 진입점은 전체 Design Approval에서 PO가 확정한다. |
| loading | Pet/Plan 각각 skeleton. 선택 Pet 검증 전 Plan, Plan 검증 전 cycle/create를 활성화하지 않는다. |
| empty | Pet 없음→Pet 등록, 호환 판매 Plan 없음→현재 생성 불가 설명+상품/목록 이동. 주소 없음/billing 없음은 create form empty가 아니다. |
| error/retry | Pet/Plan stale·판매 종료·호환 실패를 분리. create 실패는 선택을 유지하고 inline error. 동일 intention 재시도는 idempotency 계약을 유지하되 다른 body에 key를 재사용하지 않는다. |
| success | 서버가 반환한 subscription ID, 실제 next delivery, 현재/적용 snapshot, 주기, 금액을 확인한 뒤 Detail로 이동한다. redirect만으로 생성 성공을 추정하지 않는다. |
| responsive | D8 SSOT: 1024px 이상 8열 progressive form+4열 summary, 1023px 이하 단일 열. mobile은 완료 summary+현재 section을 유지하고 CTA 52px. |
| accessibility | section heading, native radio/select, Plan item list, error summary, 금액/선택 변경 live region, submit pending. |
| gap·impact | 현재 create API 세 필드와 실제 조회 흐름에 맞춰 progressive sections를 정리한다. 새 API 없음. |
| acceptance | create request가 세 필드를 넘지 않고, 사용자가 Pet·Plan·주기를 생성 전에 확인하며, 첫 배송일·배송지·결제수단·Plan item quantity를 입력하라고 요구하지 않는다. |

## C4. My `/my`

현재 `/my`는 `OrderSummary[]`, `V2SubscriptionSummary[]`, Cart, Notification, reorder timing을 조합한다. Subscription Detail을 각 항목마다 추가 조회하지 않는다.

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 주문 수/최근 주문, ACTIVE 구독 수와 `nextScheduledDate`, Cart 수량, unread notification 수, reorder timing과 관리 route links를 제공한다. |
| 문제 | Summary API에 없는 subscription issue/availableActions를 dashboard에 보여주려 하면 N개의 Detail 요청이나 새 API가 필요하다. 모든 기능을 같은 card로 반복하면 우선순위도 약해진다. |
| 레퍼런스 | 직접 검증한 외부 My 화면 없음 `UNVERIFIED`; `frontend/src/app/my/page.tsx`, `commerce-final-api.ts`, `v2-api.ts`의 실제 summary data를 기준으로 한다. |
| 최종 IA | 인사/계정→Commerce summary(주문 수·ACTIVE 구독 수·Cart 수량·unread 알림)→가장 가까운 다음 정기배송→최근 주문→재구매 timing(있을 때)→계정 관리 링크→지원. Detail-only `확인 필요한 issue` section은 만들지 않는다. |
| visual hierarchy | 다음 배송/최근 주문이 1차, count summary가 2차, 관리 링크가 3차. 중첩 카드 대신 section+행. |
| 컴포넌트 | `AccountHeader`, `CommerceSummary`, `NextSubscriptionSummary`, `RecentOrder`, `ReorderTiming`, `AccountNavList`, `SupportEntry`. `AttentionList`는 Summary 기반으로 만들 수 없으므로 제외한다. |
| interaction | summary는 상세 route로 이동, destructive action 없음. retry는 section별. 로그아웃은 명시적 button과 진행 상태. |
| navigation | `/orders`, `/subscriptions`, `/pets`, `/notifications`, `/addresses`, `/billing-methods`, 지원 routes. |
| loading | shell/계정명 유지, 각 summary skeleton. |
| empty | 주문/ACTIVE 구독 없음은 각각 짧은 다음 행동. 둘 다 없더라도 계정 관리 링크 유지. |
| error/retry | 한 section 실패가 dashboard 전체 실패가 되지 않는다. auth failure는 login. Detail N+1로 issue 복구를 시도하지 않는다. |
| success | 다음 배송은 ACTIVE Summary의 `nextScheduledDate` 중 가장 가까운 서버 날짜만 사용한다. timing hint가 없으면 추정하지 않는다. |
| responsive | D8 SSOT: 1024px 이상 핵심 summary 8/4 조합, 1023px 이하 single list. 관리 link는 전 범위 44px 행. |
| accessibility | `h1` 하나, section headings, link purpose 독립 이해, count/status live update 남용 금지. |
| gap·impact | 실제 summary data 기준으로 dashboard 우선순위와 독립 async boundary를 정리한다. |
| acceptance | 한 API 실패로 나머지 계정 기능이 사라지지 않고, dashboard를 위해 subscription Detail N+1을 만들거나 issue를 추정하지 않는다. |

## C5. Pets `/pets`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | Pet list/create와 `name`, `breed`, `weightKg` patch를 제공한다. `petType`은 생성 후 불변이며 delete API는 없다. |
| 문제 | 사진·생일·삭제 같은 미지원 기능을 암시하거나 petType을 editable select로 보이면 제품/API 규칙을 위반한다. |
| 레퍼런스 | 외부 pet profile UI 직접 증거 없음 `UNVERIFIED`; `frontend/src/lib/v2-api.ts` Pet 계약만 사용한다. |
| 최종 IA | 프로필 목록→추가 form. 편집 시 이름/품종/체중만 입력, petType은 read-only 사실+변경 불가 도움말. 삭제 action 없음. |
| visual hierarchy | Pet 이름과 유형 1차, 품종/체중과 추천에 쓰이는 제한된 정보 설명 2차. 사진 placeholder/avatar upload 금지. |
| 컴포넌트 | `PetProfileList`, `PetProfileRow`, `PetForm`, `PetTypeReadOnly`, `WeightField`. `DeleteConfirm` 없음. |
| interaction | create와 edit 분리. weight numeric constraint와 단위 표시. 저장 pending 중 중복 방지. petType 변경/삭제 시도 UI 없음. |
| navigation | `/my` breadcrumb, 추천 진입은 저장 성공 후 홈/상품 route 선택. |
| loading | list skeleton, form option loading. |
| empty | `등록한 반려동물이 없어요`+현재 지원 정보 설명+추가 CTA. |
| error/retry | field error inline, 목록 실패 retry, 저장 실패 입력 유지. conflict 시 최신 프로필 다시 로드. |
| success | 저장된 필드 summary와 추천에 반영될 수 있다는 제한적 설명. 즉시 추천 보장 금지. |
| responsive | 760px form, mobile labels above fields, numeric keyboard hint. |
| accessibility | visible labels, unit는 label에 포함, read-only는 disabled가 아닌 읽기 가능한 text, 오류 field 연결. |
| gap·impact | immutable type과 지원 patch 필드에 맞춰 form state 정리. |
| acceptance | edit에서 petType을 바꾸거나 Pet을 삭제할 수 없고, 사진/생일이 노출되지 않으며, 저장 실패 후 입력을 잃지 않는다. |

## C6. Notifications `/notifications`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 인앱 알림 목록, 개별 `read`, `PATCH /api/notifications/read-all`을 제공한다. 외부 채널은 범위가 아니다. |
| 문제 | 알림 전체가 동일 강조이면 문제 해결이 필요한 알림과 정보 알림을 구분하기 어렵다. readAll이 실제 있는데 조건부 후보처럼 남기거나 email/push 설정을 기대하게 하면 실제 범위와 어긋난다. |
| 레퍼런스 | PetSmart의 email/push reminder는 `INDIRECT/UNSUPPORTED`; PawCycle은 인앱만 `ADOPT`. |
| 최종 IA | heading+읽지 않음 count+`모두 읽음`→오늘/이전 group→알림 행. 설정 section은 지원 API가 없으므로 만들지 않는다. |
| visual hierarchy | unread dot+굵은 제목, issue severity icon+텍스트, 날짜. 광고성 promo rail 금지. |
| 컴포넌트 | `NotificationGroups`, `NotificationRow`, `UnreadIndicator`, `MarkReadAction`, `MarkAllReadAction`. |
| interaction | 행 링크가 있을 때만 목적지로 이동한다. 개별/전체 읽음 모두 서버 성공 후 상태를 반영한다. `unreadCount=0`이면 `모두 읽음`을 disabled하고 `읽지 않은 알림이 없음`을 접근 가능하게 제공한다. pending 중 중복 action 금지. readAll 성공 뒤 목록/count를 재확인한다. |
| navigation | 주문/구독/상품의 안전한 내부 route. 외부 임의 URL 금지. |
| loading | 6행 skeleton. count/action도 loading state를 공유한다. |
| empty | `새 알림이 없어요`; email/push 안내 없음. |
| error/retry | 목록/개별/전체 읽음 오류 분리. 실패한 mutation은 기존 unread 상태를 유지하고 retry. |
| success | 읽음 count 업데이트를 polite live로 한 번 알림. |
| responsive | 행 layout 단일 열, timestamp wrap. action 44px target. |
| accessibility | unread를 색만으로 표시하지 않고 숨김 텍스트, `<time>`, link purpose, `모두 읽음`의 disabled 이유. |
| gap·impact | severity/route mapping, item/all pending 정리. |
| acceptance | 인앱 범위를 넘어서는 채널 설정이 없고, 개별/전체 읽음 처리 실패가 성공처럼 보이지 않는다. |

## C7. Addresses `/addresses`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 주소 목록·추가·수정·삭제·기본값을 인증 API로 관리한다. Checkout에서 안전한 GET 복귀 context를 사용할 수 있다. Subscription shipping은 이 addressId를 직접 연결하는 API가 아니다. |
| 문제 | 기본/선택 상태, 삭제 영향, Checkout 복귀, Subscription shipping이 섞이면 주소 저장과 실제 거래 mutation이 자동 연결될 수 있다. |
| 레퍼런스 | 외부 계정 주소 UI는 `UNVERIFIED`; 현재 API로만 설계한다. |
| 최종 IA | heading+추가→기본 주소→기타 주소 행. desktop create/edit는 **560px modal**, 767px 이하는 full-screen dialog. inline form은 사용하지 않는다. |
| visual hierarchy | 수령인/주소, 기본 badge, edit. 삭제는 secondary destructive. Checkout context이면 `이 배송지 사용`을 별도 표시한다. Subscription에서 온 경우 저장 완료만으로 shipping이 변경됐다고 말하지 않는다. |
| 컴포넌트 | `AddressList`, `AddressRow`, `DefaultBadge`, `AddressForm`, `SetDefault`, `DeleteConfirm`, `ReturnContextBar`. |
| interaction | 기본 변경은 서버 성공 후 반영. 삭제는 대상/영향 confirm 뒤 성공 후 제거. Checkout returnTo 복귀 후 자동 submit 금지. Subscription에서 저장 주소를 활용할 때는 Detail에서 `AddressRequest` draft로 다시 확인한 뒤 별도 shipping mutation을 수행한다. |
| navigation | `/my`; Checkout/Subscription의 안전한 GET returnTo. |
| loading | 목록 skeleton, form submit item busy. |
| empty | 첫 주소 추가 CTA. Checkout context에서는 필수 이유를 명시. |
| error/retry | field error/서버 실패 inline. 삭제·기본 변경 실패 시 기존 상태 유지. |
| success | 저장된 정규화 주소를 행에 표시, toast+focus 복귀. 거래 mutation까지 자동 완료했다고 표현하지 않는다. |
| responsive | desktop 행, mobile stacked; 긴 주소 wrap. form CTA 52px. |
| accessibility | 주소는 `<address>`로 묶고 dialog focus, 기본 상태 text, autocomplete 속성 제공. |
| gap·impact | return context와 item mutation state, 560px dialog contract 정렬. |
| acceptance | 기본/선택 주소를 구분하고 변경 실패 시 기존 값이 유지되며, Checkout/Subscription으로 복귀한 뒤 사용자가 거래 변경을 다시 확인한다. |

## C8. Billing Methods `/billing-methods`

현재 API는 여러 결제수단 CRUD가 아니라 Toss billing provider의 **`configured / registered` 상태 조회와 `prepare` command**만 제공한다.

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `GET /api/payment-methods/toss/billing`의 `{provider, configured, registered}`와 `POST /api/payment-methods/toss/billing/prepare`의 prepare token을 사용한다. |
| 문제 | 결제수단 목록·기본값·삭제·카드 끝자리 등 실제 응답에 없는 CRUD/display를 만들면 저장 결제수단 모델이 존재한다고 오해하게 된다. prepare 성공도 최종 등록 완료와 다르다. |
| 레퍼런스 | 외부 account billing UI `UNVERIFIED`; Toss provider 상태와 PawCycle 서버 응답만 권위다. |
| 최종 IA | heading+보안 설명→provider 상태(`설정되지 않음`/`등록 필요`/`등록됨`)→configured+not registered일 때 `등록 준비` action→prepare 결과 설명. method list/default/delete UI 없음. |
| visual hierarchy | provider의 configured/registered 사실과 다음 가능한 행동이 1차. prepareToken·전체 카드번호·토큰·원시 provider error는 표시하지 않는다. |
| 컴포넌트 | `BillingProviderStatus`, `PrepareBillingAction`, `ProviderBoundary`, `PreparationResult`. `BillingMethodList`, `MaskedMethodRow`, `SetDefault`, `RemoveConfirm` 없음. |
| interaction | status 조회 후 configured=false면 준비 CTA를 제공하지 않고 환경/지원 안내. configured=true+registered=false면 prepare를 1회 실행하고 pending 중 중복 차단. prepare success는 `등록 준비가 완료됨`으로만 표현하고 최종 등록 성공으로 오표현하지 않는다. registered=true면 현재 등록 상태를 read-only로 표시한다. |
| navigation | `/my`. Subscription issue에서 이동했더라도 복귀 후 결제/구독 command를 자동 실행하지 않는다. Checkout 진행 필수 route로 사용하지 않는다. |
| loading | provider status skeleton, prepare action busy. |
| empty | 별도 `결제수단 목록 없음` 개념 대신 provider 상태로 설명한다. |
| error/retry | status/prepare 네트워크·서버 오류를 구분하고 민감 원문 미노출. 알 수 없음은 `상태 다시 확인`. |
| success | 서버가 `registered=true`를 반환했을 때만 등록됨으로 표시한다. prepare success만으로 brand/끝자리/default를 만들지 않는다. |
| responsive | 최대 760px 단일 열, action 44/52px. provider boundary overflow 금지. |
| accessibility | provider/상태를 텍스트로 제공하고 pending/status 변화 announce, 오류 focus. |
| gap·impact | provider 상태와 prepare command에 맞춰 UI 범위를 축소한다. |
| acceptance | 여러 method/default/delete를 만들지 않고, prepare와 registered를 구분하며, 저장 결제수단 없음 때문에 Checkout을 차단하지 않는다. |

## C9. Login `/login`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `LoginForm`, `sanitizeReturnTo`, auth context/CSRF lifecycle이 있다. |
| 문제 | 인증 실패·세션 만료·일반 validation, returnTo 거절을 구분하지 않으면 신뢰와 복구가 약해진다. |
| 레퍼런스 | Musinsa 로그인 화면의 visible account/password labels, password reveal, 자동 로그인, 계정 복구, 비회원 주문 조회와 복귀 context는 `CONFIRMED/ADAPT`; PawCycle 보안 계약이 우선한다. |
| 최종 IA | logo/heading→로그인 form→보안/지원. 이전 작업 설명은 `계속하려면 로그인`처럼 context가 안전할 때만. |
| visual hierarchy | form과 submit 1차, 오류 inline+summary, 지원 3차. |
| 컴포넌트 | `LoginForm`, `PasswordField`, `ErrorSummary`, `ReturnContextNotice`, `SupportLink`. |
| interaction | submit pending 중 중복 방지. password reveal은 pressed 상태. 성공 후 sanitize된 same-origin GET만 이동. POST/form draft 자동 재실행 금지. |
| navigation | 거절된 returnTo는 `/my`로 이동한다. browser back은 비밀번호를 복원하지 않는다. |
| loading | 세션 확인 중 form skeleton/disabled. 이미 로그인된 경우 안전한 target으로 replace. |
| empty | 필수 field validation. account recovery 기능이 없으면 가짜 link를 만들지 않는다. |
| error/retry | credential 오류는 구체 정보 노출 없이 form 유지. 네트워크는 retry. CSRF/session 오류는 새 토큰 확보 후 사용자가 다시 submit. |
| success | destination 이동 전 간단한 status; toast 때문에 지연하지 않는다. |
| responsive | 최대 480px form, 16px 입력 font, 52px CTA. |
| accessibility | autocomplete, visible labels, 오류 연결, reveal 이름 변경, focus summary→field link. |
| gap·impact | context notice, error taxonomy, focus, pending 정렬. |
| acceptance | 외부/위험 returnTo가 거절되고, 비밀번호·POST draft가 복구 저장되지 않으며, 중복 로그인 submit이 없다. |

## C10. Notice·FAQ·Support·Shipping·Returns

대상 라우트: `/notice`, `/faq`, `/support`, `/shipping`, `/returns`.

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 공통 trust pages 컴포넌트로 정적 신뢰 콘텐츠를 제공한다. |
| 문제 | 모든 페이지가 동일 card stack이면 문서 계층과 현재 위치가 약하고, 지원 기능이 없는 채널을 암시할 수 있다. |
| 레퍼런스 | IKEA의 고객서비스→배송조회/주문관리→FAQ/문의 구조와 주문 lookup form은 `CONFIRMED/ADAPT`; PetSmart 학습 문서는 `INDIRECT`. 채널·정책 내용 자체는 복제하지 않는다. |
| 최종 IA | 1024px 이상 breadcrumb+240px side local nav+760px article, 1023px 이하 breadcrumb+상단 disclosure nav→`h1`+실제 최근 갱신 정보→본문 heading→관련 문서→지원 진입이다. FAQ만 disclosure를 사용한다. |
| visual hierarchy | 읽기 폭 최대 760px, local nav 240px. 정책 핵심 summary가 첫 section. 큰 promo hero 없음. |
| 컴포넌트 | `TrustLayout`, `LocalNav`, `Article`, `FAQDisclosure`, `RelatedLinks`, `SupportEntry`. |
| interaction | FAQ native button disclosure, 한 번에 여러 항목 열기 가능. URL hash로 heading/FAQ 직접 연결. 검색 기능은 API가 없으면 제공하지 않는다. |
| navigation | 다섯 route 간 local nav. Order/Subscription contextual support link는 PO 승인 시 해당 detail의 안전한 context만 사용한다. 없는 chat/phone hours를 만들지 않는다. |
| loading | 정적 content면 skeleton 불필요. remote content일 때 article skeleton과 error boundary. |
| empty | notice 0건. 검색 미지원이면 search input 자체 없음. FAQ 없음은 support CTA. |
| error/retry | content 실패 retry, route 404는 trust index/support. 정책 내용을 추정해 fallback하지 않는다. |
| success | 현재 page 표시, hash 이동 시 heading focus. |
| responsive | D8 SSOT: 1024px 이상 local nav+article, 1023px 이하 상단 disclosure nav. 본문은 16px/1.7, 표는 767px 이하 label/value 행 card로 reflow한다. |
| accessibility | 하나의 `main`, heading 순서, disclosure `aria-expanded`, 링크 텍스트 독립 의미, 정책 표 caption/header. |
| gap·impact | trust page layout과 local nav, hash focus, context link를 통일한다. 콘텐츠 정책 자체 변경 없음. |
| acceptance | 200% 확대에서 본문을 가로 스크롤 없이 읽고, FAQ를 키보드로 열며, 미지원 지원 채널을 약속하지 않는다. |

## 정기배송 상태 언어 사전

| 서버 사실 | 고객 표시 | 다음 행동 규칙 |
| --- | --- | --- |
| Subscription `ACTIVE` | 진행 중 | Detail의 nextDelivery/availableActions가 있을 때만 해당 정보·행동 표시 |
| Subscription `PAUSED` | 일시정지됨 | Detail에서 `RESUME`이 허용될 때만 재개 CTA |
| Subscription `CANCELED` | 종료됨 | 이력만, 재개를 추정하지 않음 |
| `ACTIVE` + `nextDelivery.status=HELD` | 진행 중 · 다음 배송 확인 필요 | top-level status를 HELD로 바꾸지 않고 Detail issue와 허용 해결 route만 표시 |
| `pendingChange` 존재 | 변경 적용 예정 | current와 pending, 적용일 함께 표시 |
| unknown/new enum | 상태 확인 필요 | raw enum 금지, 새 명령 금지, retry/support |

`HELD`는 Schedule/nextDelivery status이며 top-level Subscription status가 아니다. `ENDED`라는 top-level enum을 만들지 않는다.

## C 번들 검증 시나리오

- List ACTIVE/PAUSED/CANCELED, Summary-only 렌더링, Detail N+1 없음.
- Detail `ACTIVE + nextDelivery HELD`, pendingChange, action 없음, ETag conflict, double submit.
- `CHANGE_PLAN` 후보/주기 호환/add-on conflict, 날짜 변경과 주기 변경을 연속 수행, 다음 배송 add-on의 1회성 문구.
- 신규 생성 Pet/Plan/cycle 세 입력, prefill 재검증, create 후 서버 next delivery 확인.
- Subscription shipping 저장 주소 draft 복사→full AddressRequest 확인→submit→issue/availableActions 재확인; current address read-back은 하지 않음.
- Pet 0/1/N, immutable petType, delete UI 없음, invalid weight, 저장 실패.
- Notification unread/개별 read/readAll 실패, Address 기본 변경/삭제 실패, Billing configured/registered/prepare 상태.
- Login unsafe returnTo, session expiry, trust route hash/keyboard/200% zoom.

외부 벤치마크 관찰일: 2026-08-29. 공식 설명 확인 URL: `https://www.petsmart.com/learning-center/autoship/how-petsmart-autoship-works`.