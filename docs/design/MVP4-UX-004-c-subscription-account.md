# MVP4-UX-004 C. 정기배송·계정·지원

## 적용 범위와 원칙

대상은 `/subscriptions*`, 호환 별칭 `/mvp2/subscriptions*`, `/my`, `/pets`, `/notifications`, `/addresses`, `/billing-methods`, `/login`, `/notice`, `/faq`, `/support`, `/shipping`, `/returns`다.

정기배송 UI는 서버가 제공하는 `nextDelivery`, `pendingChange`, `issue`, `availableActions`, `ETag`가 권위다. `RESCHEDULE_NEXT`와 `CHANGE_DELIVERY_CYCLE`을 하나의 “배송 변경”으로 합치지 않는다. 2/4/8주 외 주기를 만들지 않고, HELD 문제를 원시 enum으로 노출하지 않으며, 결제 재시도 기능을 암시하지 않는다.

## C1. Subscription List `/subscriptions`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | v2 구독 목록/플랜과 서버 상태를 사용하며 `/mvp2/subscriptions`가 같은 화면으로 연결된다. |
| 문제 | 모든 구독을 큰 카드로 표시하면 다음 배송과 문제 상태를 빠르게 비교하기 어렵다. HELD/active 같은 raw 상태와 가능한 행동이 분리될 수 있다. |
| 레퍼런스 | PetSmart Autoship의 관리 가능 항목은 공식 설명으로 `INDIRECT/ADAPT`; 실제 계정 UI는 미확인이다. Chewy/Petco는 `UNVERIFIED`로 사용하지 않는다. |
| 최종 IA | heading+설명+새 정기배송→`확인 필요` group→`진행 중` group→`종료` group. 각 행은 상태, 핵심 상품, 다음 배송일, 주기, 금액 요약, 허용된 다음 행동 하나를 표시한다. |
| visual hierarchy | issue가 있으면 issue title+해결 CTA가 첫 번째. 정상은 다음 배송일이 첫 번째, 주기는 보조. 종료 상태는 낮은 대비지만 읽기 가능하게 둔다. |
| 컴포넌트 | `SubscriptionGroup`, `SubscriptionRow`, `IssueBanner`, `NextDelivery`, `CycleLabel`, `PrimaryAvailableAction`, `CreateSubscriptionLink`. |
| interaction | row 전체 clickable 금지, `상세 보기` 명시. list에서 날짜/주기를 직접 편집하지 않는다. issue CTA는 서버 `availableActions`에 있을 때만 제공한다. |
| navigation | detail route. legacy 별칭으로 들어오면 기능·focus가 동일해야 하며 canonical route 전환은 history를 불필요하게 두 번 쌓지 않는다. |
| loading | group 구조를 추정하지 않는 3행 skeleton. 인증 확인 중 empty 금지. |
| empty | 구독 0건: 일반 구매와 차이를 설명하고 `/subscriptions/new` CTA. 종료만 있으면 진행 중 empty와 종료 이력을 분리한다. |
| error/retry | 목록 실패 section retry. 일부 행 상세 실패 시 해당 행 unavailable 상태와 retry. 401은 안전한 로그인 복귀. |
| success | 최신 상태 조회 시간을 강조하지 않고 서버 날짜/상태를 고객 언어로 표시한다. |
| responsive | desktop 행 grid, mobile은 날짜→상품→주기→CTA 순 compact block. issue 메시지는 잘리지 않는다. |
| accessibility | group heading, 상태 색+아이콘+텍스트, 날짜 `<time>`, action 이름에 구독 상품/대상 포함. |
| gap·impact | card 중심 목록을 상태 group+행으로 정리하고 available action formatter를 통일한다. API 변경 없음. |
| acceptance | 문제 구독을 먼저 발견하고, 다음 배송일과 주기를 혼동하지 않으며, 서버가 허용하지 않은 action이 나타나지 않는다. |

## C2. Subscription Detail `/subscriptions/[subscriptionId]`

### 최종 정보 구조

```text
Breadcrumb / 상태 / 핵심 다음 행동
Issue 또는 pending change summary
다음 배송: 날짜 · 이번 배송 추가상품 · 배송지 · 결제수단
반복 설정: 2/4/8주 주기 · 기본 상품/수량
활동/변경 기록
건너뛰기 · 일시정지/재개 · 취소 등 서버 허용 행동
지원
```

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | detail과 v2 command, ETag/If-Match, idempotency, 다음 배송 추가상품, 주소/결제 업데이트를 사용한다. |
| 문제 | 날짜 변경과 주기 변경, pending change와 current value, HELD issue와 일반 상태가 하나의 form에 섞일 수 있다. stale conflict는 사용자의 변경을 잃게 만들 수 있다. |
| 레퍼런스 | PetSmart 공식 문서의 날짜·skip·quantity·cancel 관리와 사전 변경 제한은 `INDIRECT/ADAPT`. 이메일/푸시, 대체품은 `UNSUPPORTED`. |
| 최종 IA | status header→issue/pending→다음 배송 section→반복 설정→상품/수량→history→danger actions. 다음 배송과 반복 설정은 별도 heading·편집 dialog를 갖는다. |
| visual hierarchy | issue/pending이 있으면 상단 persistent banner. 정상은 `다음 배송 9월 12일`이 가장 크고 `4주마다`는 별도 label. destructive action은 맨 아래. |
| 컴포넌트 | `SubscriptionStatusHeader`, `IssueResolution`, `PendingChangeSummary`, `NextDeliveryPanel`, `RescheduleDialog`, `CycleDialog`, `NextDeliveryAddOns`, `SubscriptionItems`, `ActivityList`, `DangerActions`. |
| detailed interaction | 날짜 편집은 현재 날짜·허용 범위→새 날짜 확인→`RESCHEDULE_NEXT`. 주기 편집은 2/4/8주 radio→영향 설명→`CHANGE_DELIVERY_CYCLE`. 두 dialog는 동시 열리지 않는다. add-on은 `다음 배송에만` 문구를 CTA와 summary에 반복한다. |
| conflict/duplicate | 열 때 받은 ETag를 제출. 412/409이면 dialog 입력을 자동 재전송하지 않고 최신 current/pending/issue를 보여준 뒤 `최신 상태에서 다시 변경`. 동일 click idempotency key 유지. |
| navigation | list breadcrumb, 주소·결제수단 관리 route는 subscription detail returnTo. 안전한 GET만 복귀하며 편집 dialog는 자동 복원·제출하지 않는다. |
| loading | header와 핵심 날짜 skeleton, actions는 권한 로드 전 숨김. section별 lazy load 가능. |
| empty | add-on 없음은 `이번 배송에 추가한 상품 없음`; history 없음은 축소. 핵심 subscription 없음은 404/list CTA. |
| error/retry | core 실패 page retry. action 실패 dialog 내 retry. HELD는 issue mapping(결제수단/주소/상품 등 실제 서버 원인)과 지원 가능한 해결 행동; 결제 재시도 버튼은 만들지 않는다. |
| success | 저장 toast+영구 summary. current 값과 pending 값이 다르면 `현재`/`적용 예정`과 적용 시점을 같이 표시한다. |
| responsive | ≥900 main 8+summary 4 가능, <900 single. action panel은 mobile bottom sheet가 아니라 field가 많은 경우 full-screen dialog. sticky CTA는 열린 편집에만 하나. |
| accessibility | dialog title/description, radio fieldset, 날짜 입력 label/constraint/error, pending `status`, issue `alert`, destructive confirm focus trap. |
| gap·impact | 현재 통합 변경 UI를 날짜/주기 command 별로 분리하고 ETag conflict·pending·issue 표현을 정식화한다. API 변경 없음. |
| acceptance | 날짜 변경이 주기를 바꾸지 않고 주기 변경이 선택한 다음 날짜를 덮지 않으며, stale submit과 중복 submit이 자동 재실행되지 않는다. |

## C3. New Subscription `/subscriptions/new`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | v2 plans, order subscription options 또는 승인된 진입 context를 이용해 생성한다. legacy alias도 현행 화면으로 연결된다. |
| 문제 | 일반 구매와 구독, 추천 주기와 선택 주기, 주문 상품과 신규 상품을 혼합하면 사용자가 무엇을 반복 결제하는지 오해한다. |
| 레퍼런스 | PetSmart의 PDP에서 주기 선택 후 반복 주문이라는 설명은 `INDIRECT/ADAPT`; PawCycle PDP 직접 생성은 `UNSUPPORTED`. |
| 최종 IA | source 상품/주문 확인→구독 상품·수량→2/4/8주 선택→첫/다음 배송일→배송지→결제수단→최종 summary/생성. 단계는 한 페이지 section 또는 좁은 step flow지만 URL로 미완료 민감 데이터를 저장하지 않는다. |
| visual hierarchy | `첫 배송`과 `이후 N주마다`를 한 summary 문장으로 반복. 추천은 `추천` badge와 근거가 있을 때만, 선택은 항상 사용자 action. |
| 컴포넌트 | `SubscriptionSource`, `ItemQuantity`, `CycleSelector`, `DeliveryDate`, `AddressSummary`, `BillingSummary`, `CreateSummary`, `CreateButton`. |
| detailed interaction | 추천 주기를 preselect하지 않거나 preselect 시에도 사용자가 확인해야 한다. 생성 submit은 idempotent. address/billing 이동 시 폼을 자동 submit하지 않으며 복귀 후 다시 최종 확인한다. |
| navigation | 주 진입점은 PO 미결. cancel은 source order/PDP 또는 list로 복귀. success는 detail. |
| loading | plan/options skeleton. 필수 source 검증 전 form disabled. |
| empty | eligible item 없음, address 없음, billing 없음을 각각 설명하고 해당 route CTA. |
| error/retry | source stale/품절은 최신 가능 상품 표시와 source 복귀. 생성 실패는 입력 유지+inline error, 서버 확인 없는 재시도 금지. |
| success | 생성된 subscription ID와 첫/다음 배송일, 주기, 금액을 서버 응답으로 확인 후 detail CTA. |
| responsive | 최대 760px form, desktop 우측 summary 가능. mobile은 section accordion을 기본으로 쓰지 않고 전체 흐름을 유지한다. CTA 52px. |
| accessibility | step/section heading, radio native control, error summary, 금액·날짜 변경 live region, submit pending. |
| gap·impact | source context, explicit confirmation, empty prerequisites, create result를 정렬한다. 새 PDP API 없음. |
| acceptance | 사용자가 첫 배송과 반복 주기를 제출 전 말로 설명할 수 있고, 추천이 자동 명령으로 실행되지 않으며, 복귀 후 중복 생성되지 않는다. |

## C4. My `/my`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 주문 요약, 재주문 timing, v2 구독, 계정 route links를 조합한다. |
| 문제 | 모든 계정 기능이 dashboard card로 반복되면 중요한 문제와 단순 링크가 같은 비중을 가진다. |
| 레퍼런스 | 직접 검증한 외부 My 화면 없음 `UNVERIFIED`; PawCycle 실제 route와 상태를 기준으로 한다. |
| 최종 IA | 인사/계정→확인 필요한 issue→다음 배송→최근 주문→계정 관리 링크 목록→지원. 추천 상품 rail은 홈과 중복하지 않는다. |
| visual hierarchy | issue/다음 배송 1차, 최근 주문 2차, 관리 링크 3차. 중첩 카드 대신 section+행. |
| 컴포넌트 | `AccountHeader`, `AttentionList`, `NextSubscriptionSummary`, `RecentOrder`, `AccountNavList`, `SupportEntry`. |
| interaction | summary는 상세 route로 이동, destructive action 없음. retry는 section별. 로그아웃은 명시적 button과 진행 상태. |
| navigation | `/orders`, `/subscriptions`, `/pets`, `/notifications`, `/addresses`, `/billing-methods`, 지원 routes. |
| loading | shell/계정명 유지, 각 summary skeleton. |
| empty | 주문/구독 없음은 각각 짧은 다음 행동. 둘 다 없더라도 계정 관리 링크 유지. |
| error/retry | 한 section 실패가 dashboard 전체 실패가 되지 않음. auth failure는 login. |
| success | issue count와 다음 배송은 서버 사실만 표시. timing hint가 없으면 추정하지 않는다. |
| responsive | desktop 8/4 또는 2열 summary, mobile single list. 관리 links 44px 행. |
| accessibility | `h1` 하나, section headings, link purpose 독립 이해, issue live update 남용 금지. |
| gap·impact | dashboard 정보 우선순위와 독립 async boundary 정리. |
| acceptance | 사용자가 첫 화면에서 확인할 문제와 다음 배송을 찾고, 한 API 실패로 나머지 계정 기능이 사라지지 않는다. |

## C5. Pets `/pets`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 프로필 이름, petType, breed, weight 생성·수정. petType은 생성 후 불변이다. |
| 문제 | 사진·생일 같은 미지원 필드를 암시하거나 petType을 editable select로 보이면 제품 규칙을 위반한다. 추천 활용 설명이 과장될 수 있다. |
| 레퍼런스 | 외부 pet profile UI 직접 증거 없음 `UNVERIFIED`; 승인 도메인만 사용한다. |
| 최종 IA | 프로필 목록→추가 form. 편집 시 이름/품종/체중만 입력, petType은 read-only 사실+변경 불가 도움말. |
| visual hierarchy | 펫 이름과 유형 1차, 추천에 쓰이는 제한된 정보 설명 2차. 사진 placeholder나 avatar upload 금지. |
| 컴포넌트 | `PetProfileList`, `PetProfileRow`, `PetForm`, `PetTypeReadOnly`, `WeightField`, `DeleteConfirm`(실제 API가 있을 때만). |
| interaction | create와 edit 분리. weight numeric constraint와 단위 표시. 저장 pending 중 중복 방지. petType 변경 시도 UI 없음. |
| navigation | `/my` breadcrumb, 추천 진입은 저장 성공 후 홈/상품 route 선택. |
| loading | list skeleton, form option loading. |
| empty | `등록한 반려동물이 없어요`+현재 지원 정보 설명+추가 CTA. |
| error/retry | field error inline, 목록 실패 retry, 저장 실패 입력 유지. conflict 시 최신 프로필 다시 로드. |
| success | 저장된 필드 summary와 추천에 반영될 수 있다는 제한적 설명. 즉시 추천 보장 금지. |
| responsive | 760px form, mobile labels above fields, numeric keyboard hint. |
| accessibility | visible labels, unit는 label에 포함, read-only는 disabled가 아닌 읽기 가능한 text, 오류 field 연결. |
| gap·impact | immutable type 표현과 미지원 필드 제거, form state 정리. |
| acceptance | edit에서 petType을 바꿀 수 없고, 사진/생일이 노출되지 않으며, 저장 실패 후 입력을 잃지 않는다. |

## C6. Notifications `/notifications`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 인앱 알림 목록·읽음 상태를 제공한다. 외부 채널은 범위가 아니다. |
| 문제 | 알림 전체가 동일 강조이면 문제 해결이 필요한 알림과 정보 알림을 구분하기 어렵다. email/push 설정을 기대하게 할 수 있다. |
| 레퍼런스 | PetSmart의 email/push reminder는 `INDIRECT/UNSUPPORTED`; PawCycle은 인앱만 `ADOPT`. |
| 최종 IA | heading+읽지 않음 count→오늘/이전 group→알림 행. 설정 section은 지원 API가 없으면 만들지 않는다. |
| visual hierarchy | unread dot+굵은 제목, issue severity icon+텍스트, 날짜. 광고성 promo rail 금지. |
| 컴포넌트 | `NotificationGroups`, `NotificationRow`, `UnreadIndicator`, `MarkReadAction`. |
| interaction | 행 링크가 있을 때만 목적지 이동. 읽음 처리는 서버 성공 후 또는 복구 가능한 optimistic일 때. 전체 읽음은 API가 있을 때만. |
| navigation | 주문/구독/상품의 안전한 내부 route. 외부 임의 URL 금지. |
| loading | 6행 skeleton. |
| empty | `새 알림이 없어요`; email/push 안내 없음. |
| error/retry | 목록/개별 읽음 오류 분리. 실패한 행은 unread 상태 유지. |
| success | 읽음 count 업데이트를 polite live로 한 번 알림. |
| responsive | 행 layout 단일 열, timestamp wrap. 44px target. |
| accessibility | unread를 색만으로 표시하지 않고 숨김 텍스트, `<time>`, 링크 목적 명확화. |
| gap·impact | severity/route mapping과 item pending 정리. |
| acceptance | 인앱 범위를 넘어서는 채널 설정이 없고, 읽음 처리 실패가 성공처럼 보이지 않는다. |

## C7. Addresses `/addresses`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 주소 목록·추가·수정·기본값을 인증 API로 관리한다. checkout/subscription에서 복귀할 수 있다. |
| 문제 | 주소 원문, 기본/선택 상태, 삭제 영향이 섞이고, checkout에서 돌아왔을 때 자동 선택·제출될 위험이 있다. |
| 레퍼런스 | 외부 계정 주소 UI는 `UNVERIFIED`; 현재 API로만 설계한다. |
| 최종 IA | heading+추가→기본 주소→기타 주소 행. 편집은 최대 760px modal/full-screen dialog 또는 별도 inline form 한 패턴만 사용한다. |
| visual hierarchy | 수령인/주소, 기본 badge, edit. 삭제는 secondary destructive. checkout context이면 `이 배송지 사용`을 별도 표시한다. |
| 컴포넌트 | `AddressList`, `AddressRow`, `DefaultBadge`, `AddressForm`, `SetDefault`, `DeleteConfirm`, `ReturnContextBar`. |
| interaction | 기본 변경 서버 확인 후. 삭제 전 기본/사용 중 영향은 API가 확정한 범위만 설명. returnTo 복귀 후 checkout submit 자동 실행 금지. |
| navigation | `/my`; checkout/subscription의 안전한 GET returnTo. |
| loading | 목록 skeleton, form submit item busy. |
| empty | 첫 주소 추가 CTA. checkout context에서는 필수 이유를 명시. |
| error/retry | field error/중복/서버 실패 inline. 삭제·기본 변경 실패 시 기존 상태 유지. |
| success | 저장된 정규화 주소를 행에 표시, toast+focus 복귀. |
| responsive | desktop 행, mobile stacked; 긴 주소 wrap. form CTA 52px. |
| accessibility | address는 `<address>` 또는 의미 있는 grouping, dialog focus, 기본 상태 text, autocomplete 속성. |
| gap·impact | return context와 item mutation state, dialog contract 정렬. |
| acceptance | 기본/선택 주소를 구분하고, 변경 실패 시 기존 기본값이 유지되며, checkout으로 복귀해 사용자가 다시 확인한다. |

## C8. Billing Methods `/billing-methods`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | 결제수단 목록·등록·기본값과 Toss billing 흐름을 사용한다. 민감 원문을 저장소/UI에 노출하지 않는다. |
| 문제 | 등록 진행, 외부 승인, 실패/취소, 이미 등록됨, 구독에 사용 중인 수단의 영향을 사용자가 이해하기 어렵다. |
| 레퍼런스 | 외부 account billing UI `UNVERIFIED`; Toss와 서버 응답만 권위다. |
| 최종 IA | heading+보안 설명+등록→기본 수단→기타 수단. 표시값은 brand/마스킹된 끝자리/상태만. |
| visual hierarchy | 기본 badge와 사용 가능 상태 1차. 전체 번호·토큰·원시 provider error 금지. |
| 컴포넌트 | `BillingMethodList`, `MaskedMethodRow`, `RegisterBillingAction`, `SetDefault`, `RemoveConfirm`, `ProviderBoundary`. |
| interaction | 등록은 1회 external flow. refresh/back에서 자동 재시작 금지. 기본 변경 서버 확인 후. 삭제 영향은 서버가 제공할 때만 구독 count를 설명한다. |
| navigation | checkout/subscription returnTo는 GET만. provider 취소는 이 화면 또는 호출 화면으로 안전 복귀. |
| loading | provider/목록 독립. register CTA 중복 방지. |
| empty | `등록된 결제수단이 없어요`+등록 CTA; 결제 재시도 CTA로 표현하지 않는다. |
| error/retry | provider 취소, 네트워크, 서버 거절을 구분하되 민감 원인 미노출. 알 수 없음은 `등록 결과 확인` 우선. |
| success | 마스킹된 새 수단과 기본 여부를 서버 응답 후 표시. |
| responsive | 760px 목록/form, mobile 행 stack. provider widget overflow 금지. |
| accessibility | 카드 brand만으로 구분하지 않고 끝자리/상태 text, 외부 iframe title, 오류 focus. |
| gap·impact | provider 상태와 safe display, return context 통일. |
| acceptance | 민감 값이 노출되지 않고, 등록 결과 미확정 상태에서 중복 등록을 유도하지 않으며, 복귀 후 자동 결제가 없다. |

## C9. Login `/login`

| 필수 항목 | 최종 계약 |
| --- | --- |
| 현재 PawCycle | `LoginForm`, `sanitizeReturnTo`, auth context/CSRF lifecycle이 있다. |
| 문제 | 인증 실패·세션 만료·일반 validation, returnTo 거절을 구분하지 않으면 신뢰와 복구가 약해진다. |
| 레퍼런스 | 외부 로그인 벤치마크 사용 없음 `UNVERIFIED`; 보안 계약을 우선한다. |
| 최종 IA | logo/heading→로그인 form→보안/지원. 이전 작업 설명은 `계속하려면 로그인`처럼 context가 안전할 때만. |
| visual hierarchy | form과 submit 1차, 오류 inline+summary, 지원 3차. |
| 컴포넌트 | `LoginForm`, `PasswordField`, `ErrorSummary`, `ReturnContextNotice`, `SupportLink`. |
| interaction | submit pending 중 중복 방지. password reveal은 pressed 상태. 성공 후 sanitize된 same-origin GET만 이동. POST/form draft 자동 재실행 금지. |
| navigation | 거절된 returnTo는 `/my` 또는 `/`. browser back은 비밀번호를 복원하지 않는다. |
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
| 레퍼런스 | PetSmart 학습 문서의 article navigation과 FAQ 구조는 `CONFIRMED/ADAPT`; 채널·정책 내용 자체는 복제하지 않는다. |
| 최종 IA | 공통 breadcrumb/사이드 또는 상단 local nav→`h1`+최근 갱신 정보(실제 값이 있을 때)→본문 heading→관련 문서→지원 진입. FAQ만 disclosure 사용. |
| visual hierarchy | 읽기 폭 최대 760px, local nav 240px. 정책 핵심 summary가 첫 section. 큰 promo hero 없음. |
| 컴포넌트 | `TrustLayout`, `LocalNav`, `Article`, `FAQDisclosure`, `RelatedLinks`, `SupportEntry`. |
| interaction | FAQ native button disclosure, 한 번에 여러 항목 열기 가능. URL hash로 heading/FAQ 직접 연결. 검색 기능은 API가 없으면 제공하지 않는다. |
| navigation | 다섯 route 간 local nav, 주문/구독 상세의 context support link. 없는 chat/phone hours를 만들지 않는다. |
| loading | 정적 content면 skeleton 불필요. remote content일 때 article skeleton과 error boundary. |
| empty | notice 0건과 search 0건을 구분; search 미지원이면 input 없음. FAQ 없음은 support CTA. |
| error/retry | content 실패 retry, route 404는 trust index/support. 정책 내용을 추정해 fallback하지 않는다. |
| success | 현재 page 표시, hash 이동 시 heading focus. |
| responsive | ≥900 local nav+article, <900 local nav select/disclosure. 본문 16px/1.7, 표는 reflow 또는 행 카드. |
| accessibility | 하나의 `main`, heading 순서, disclosure `aria-expanded`, 링크 텍스트 독립 의미, 정책 표 caption/header. |
| gap·impact | trust page layout과 local nav, hash focus, context link를 통일한다. 콘텐츠 정책 자체 변경 없음. |
| acceptance | 200% 확대에서 본문을 가로 스크롤 없이 읽고, FAQ를 키보드로 열며, 미지원 지원 채널을 약속하지 않는다. |

## 정기배송 상태 언어 사전

| 서버 의미 | 고객 표시 | 다음 행동 규칙 |
| --- | --- | --- |
| ACTIVE | 진행 중 | nextDelivery와 availableActions 표시 |
| PAUSED | 일시정지됨 | 재개가 허용될 때만 CTA |
| HELD | 확인 필요 | issue 원인을 사용자 문장으로, 허용된 해결 route만 |
| CANCELED/ENDED | 종료됨 | 이력만, 재개를 추정하지 않음 |
| pendingChange 존재 | 변경 적용 예정 | current와 pending, 적용일을 함께 표시 |
| unknown/new enum | 상태 확인 필요 | raw enum 금지, 새 명령 금지, retry/support |

실제 enum 명칭은 API 계약의 최신 값을 매핑한다. 이 표는 새로운 상태를 정의하지 않는다.

## C 번들 검증 시나리오

- 구독 정상/HELD/PAUSED/종료, pending change, action 없음, ETag conflict, double submit.
- 다음 날짜 변경과 주기 변경을 연속 수행, 다음 배송 add-on의 1회성 문구.
- 펫 0/1/N, immutable petType, invalid weight, 저장 실패.
- 알림 unread/read 실패, 주소 기본 변경/삭제 실패, 결제수단 등록 결과 미확정.
- login unsafe returnTo, session expiry, trust route hash/keyboard/200% zoom.

외부 벤치마크 관찰일: 2026-08-29. 공식 설명 확인 URL: `https://www.petsmart.com/learning-center/autoship/how-petsmart-autoship-works`.
