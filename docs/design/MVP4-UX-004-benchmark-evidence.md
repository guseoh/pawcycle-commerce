# MVP4-UX-004 실제 Commerce 벤치마크 증거

## 문서 지위

- 상태: `Proposed Design Contract / Draft / Pending Product Owner Approval`
- 관찰일: 2026-08-29
- 목적: 브랜드 이름이 아니라 실제 화면에서 확인한 컴포넌트·상호작용을 설계 근거로 남긴다.
- 증거 등급: 직접 화면과 조작을 확인한 `CONFIRMED`, 공식 설명만 확인한 `INDIRECT`, 인증·차단·미완료 흐름으로 확인하지 못한 `UNVERIFIED`.
- 경계: 레퍼런스의 가격·정책·혜택은 PawCycle 사실로 옮기지 않는다. 접근할 수 없었던 화면은 관행으로 추정하지 않는다.

## 직접 관찰 기록

| 출처 | 실제 경로 | 확인한 컴포넌트·상호작용 | 증거 | PawCycle 판정 |
| --- | --- | --- | --- | --- |
| Kurly | `https://www.kurly.com/main` | 로고·검색·유틸리티와 별도 카테고리/주 내비게이션, 카테고리 클릭 개폐, hero와 상품 rail, 500px 스크롤 뒤 56px 탐색행만 sticky | `CONFIRMED` | 검색 우선 헤더와 축소 sticky 행 `ADAPT`; Escape로 닫히지 않은 카테고리 메뉴 `REJECT` |
| Kurly Pet PLP | `https://www.kurly.com/categories/991` | 좌측 filter rail, 결과 수, URL 정렬, 1280px에서 249px 폭 3열 카드, 배송·배지·설명·원가·할인·현재가·리뷰가 누적된 긴 카드 | `CONFIRMED` | 정보 밀도를 보존하되 1440px 5열 `REJECT`; PawCycle 4열 상한의 근거 |
| Kurly PDP | `https://www.kurly.com/goods/1001181670` | 약 430px gallery+560px 구매 요약, 옵션 열기/선택, 수량 1에서 감소 disabled, 총액 갱신, 56px 담기 CTA, 스크롤 뒤 header 아래 anchor sticky | `CONFIRMED` | gallery/구매 요약, 옵션-수량-합계 순서, section anchor `ADAPT` |
| Kurly Cart | `https://www.kurly.com/cart` | 전체 선택 1/1, 배송 group, item 선택, 수량 stepper, 합계; item 해제 즉시 0/1·0원·선택삭제 disabled로 동기화 | `CONFIRMED` | 선택 집합과 서버 합계의 동시 가시화 `ADOPT` |
| Musinsa Search/PLP | `https://www.musinsa.com/search/goods?keyword=운동화&gf=A` | 검색 제안·결과 탭, 복수 filter group 동시 개방, Adidas 선택 즉시 URL `brand=adidas`와 결과 수 갱신, 1280px 6열 카드 | `CONFIRMED` | URL 동기화·group 독립 개방 `ADAPT`; 20px 좋아요 target과 6열 과밀 `REJECT` |
| Musinsa Wishlist/Login | `https://www.musinsa.com/like/goods` | 비로그인 empty, `로그인하고 … 할인 소식` 설명, 로그인 CTA, 로그인 URL의 복귀 context; 로그인 화면 label·비밀번호 보기·자동 로그인·복구 링크·비회원 주문 조회 | `CONFIRMED` | 사전 설명+안전한 GET 복귀, visible label과 보조 복구 링크 `ADAPT` |
| IKEA Search/PLP | `https://www.ikea.com/kr/ko/search/?q=KALLAX` | 검색어 유지/clear, 결과 status, filter chip rail, 비교 checkbox, wishlist, add, 옵션 swatch; 4개 선택 시 `4/5개 선택됨` tray와 compare URL | `CONFIRMED` | PawCycle 승인 한도 3개로 축소한 선택 tray·명시적 한도 `ADAPT`; 5개 복제 `REJECT` |
| IKEA PDP | `https://www.ikea.com/kr/ko/p/kallax-shelving-unit-white-20351884/` | 모바일 375px에서 gallery→상품명·가격·리뷰→옵션→배송/재고→수량→담기→tabs/accordion→리뷰; 위시와 sticky 담기 제공 | `CONFIRMED` | 모바일 단일 정보 순서와 하단 거래 CTA `ADAPT` |
| IKEA Cart | `https://www.ikea.com/kr/ko/shoppingcart/` | 수량 직접 입력+증감, 수량 1 감소 disabled, 삭제, 위시 저장, semantic 주문 요약 table, coupon disclosure, 결제 CTA; 375px에서 동일 의미 순서와 하단 결제 CTA | `CONFIRMED` | 수량·삭제·저장 분리와 모바일 영구 합계/CTA `ADAPT` |
| IKEA Checkout | cart의 `결제하기` | 로그인/게스트 선택 dialog 후 guest 진행, `/order/delivery/`에서 배송 active·상세/결제 `아직 제공되지 않음`, 상품/금액 disclosure, 우편번호 전 단계 | `CONFIRMED` | 단계 잠금과 현재 단계 한 개만 편집하는 progressive disclosure `ADOPT`; PawCycle은 승인된 인증 필수 정책을 유지 |
| IKEA Order/Support | `https://www.ikea.com/kr/ko/purchases/lookup/`, `/customer-service/track-manage-order/` | 주문번호+이메일/전화번호 조회, 입력 형식 hint, 로그인 시 가능한 행동 목록, 배송조회·주문관리·FAQ·문의의 분리 | `CONFIRMED` | 주문 context를 가진 조회/지원 진입과 관련 행동 분리 `ADAPT`; PawCycle에 비회원 조회 기능 추가 금지 |
| PetFriends Home | `https://m.pet-friends.co.kr/main/tab/2` | 강아지/배송지 전환, 홈·샘플·베스트·신상품·이벤트, 사료/간식/용품 tabs, 연령·용도 category, 추천 rail의 상품명·원가·할인·현재가·적립·묶음 단가·리뷰 | `CONFIRMED` | 반려동물 유형→소비 목적→상품의 탐색 계층과 묶음 단가 위치 `ADAPT`; 과도한 혜택 누적 `REJECT` |
| PetFriends PDP | `https://m.pet-friends.co.kr/product/detail/110966` | 반려 유형/브랜드/랭킹, 리뷰 수, 제목, 원가·할인·현재가, 배송지 입력, 만족도/리뷰, 추천 rail, 하단 찜·장바구니 담기 | `CONFIRMED` | 반려 적합성·배송·신뢰를 구매 전 배치하고 mobile sticky action을 분리 `ADAPT` |

## 공식 설명 기반 정기배송 근거

| 출처 | 확인한 공식 설명 | 증거 | 적용 경계 |
| --- | --- | --- | --- |
| [Petco Autoship FAQ](https://www.petco.com/content/petco/PetcoStore/en_US/pet-services/help/autoship.html) | PDP에서 주기 선택, 다음 주문일·건너뛰기·주기·수량 관리, 주소·결제 변경, 취소 | `INDIRECT` | PawCycle이 이미 지원하는 날짜·주기·수량·건너뛰기·취소만 사용. 실제 계정 UI 형태는 추정하지 않음 |
| [PetSmart AutoShip Help](https://www.petsmart.com/help/your-order-H0003d.html) / [Terms](https://www.petsmart.com/help/auto-ship-HOO14.html) | Manage Order, skip, 주기 dropdown, 재시작·취소·주소·결제 관리와 변경 마감 설명 | `INDIRECT` | 날짜 변경과 반복 주기를 별도 action으로 유지. 24시간 같은 정책 수치는 복제하지 않음 |
| [Pet Valu Autoship Help](https://prb-support.freshdesk.com/support/solutions/articles/25000027718-how-do-i-make-changes-to-my-autoship-subscription-) | 날짜·skip·주기·결제·취소 변경과 시작 후 배송지 제한 설명 | `INDIRECT` | 제한 조건을 서버 `availableActions`와 issue 설명으로만 표시 |

## 핵심 화면별 Primary / Secondary 근거

| PawCycle 화면 | Primary | Secondary | 채택한 단위 | 미확인·제외 |
| --- | --- | --- | --- | --- |
| Header/Search | Kurly Home `CONFIRMED` | Musinsa Search `CONFIRMED` | 검색 중심 full header→축소 sticky, submit 후 URL/결과 | Kurly의 Escape 미지원 메뉴 |
| Home | PetFriends Home `CONFIRMED` | Kurly Home `CONFIRMED` | 펫 유형·생활 목적 category, 한 개 hero, 상품 rail | 혜택 rail 반복·자동재생 |
| PLP | Kurly Pet PLP `CONFIRMED` | Musinsa/IKEA Search `CONFIRMED` | filter hierarchy, URL 적용, 결과 status, 제품 행동 | 5·6열 과밀 |
| Product Card | PetFriends Home `CONFIRMED` | Kurly Pet/IKEA Search `CONFIRMED` | 반려 적합성, 가격·할인·묶음 단가·리뷰·44px 행동 순서 | 배지·혜택 중복, 20px action |
| PDP | Kurly PDP `CONFIRMED` | PetFriends/IKEA PDP `CONFIRMED` | gallery/구매 요약, 배송·적합성, 상세 anchor, mobile sticky | PDP 정기배송 생성 control |
| Compare | IKEA Search `CONFIRMED` | PawCycle 승인 비교 계약 | 선택 tray·한도·URL product IDs | 외부 실제 비교표의 모바일 전환은 `UNVERIFIED` |
| Wishlist | Musinsa Wishlist `CONFIRMED` | IKEA PLP `CONFIRMED` | anonymous 설명/로그인, card-level save | 로그인 후 실제 목록 관리 UI는 `UNVERIFIED` |
| Cart | Kurly Cart `CONFIRMED` | IKEA Cart `CONFIRMED` | 선택 집합·수량·합계, 쿠폰 disclosure, mobile CTA | 외부 가격 충돌 UI는 `UNVERIFIED` |
| Checkout | IKEA Checkout `CONFIRMED` | PawCycle/Toss 현행 계약 | 단계 잠금, 영구 summary, pending/error, server confirm | 주소 이후 실제 IKEA 결제는 개인정보 미입력으로 `UNVERIFIED` |
| Checkout Result | PawCycle server-authoritative 계약 | Toss redirect contract | 확인 중→확정/실패/unknown 분리 | 외부 실제 완료 화면 `UNVERIFIED` |
| Order List | IKEA Order Lookup `CONFIRMED` | PawCycle order API | 날짜/번호·상태·상품·총액·상세 행동의 행 구조 | 로그인 계정 목록 UI `UNVERIFIED` |
| Order Detail | IKEA Order Lookup `CONFIRMED` | PawCycle available actions | context·status·관리·지원 분리 | 로그인 계정 주문 상세 UI `UNVERIFIED` |
| Reorder | IKEA Cart `CONFIRMED` | PawCycle quick-reorder partial result | 다시 담긴 item과 제외 item을 분리하고 cart로 이동 | 외부 부분 성공 UI `UNVERIFIED` |
| Subscription List | Petco `INDIRECT` | PetSmart `INDIRECT` | issue→진행 중→종료 group, 다음 배송과 허용 행동 | 실제 계정 UI `UNVERIFIED` |
| Subscription Detail | Petco `INDIRECT` | PetSmart/Pet Valu `INDIRECT` | 다음 배송·주기·수량·건너뛰기·취소를 별도 action | 실제 계정 UI `UNVERIFIED` |
| New Subscription | Petco `INDIRECT` | PetSmart `INDIRECT` | source 확인→주기→첫 배송→배송/결제→확인 | 외부 생성 UI `UNVERIFIED`; PDP 직접 생성 제외 |
| My | IKEA Order Lookup `CONFIRMED` | PawCycle route/API | issue·다음 배송·최근 주문·관리 링크의 우선순위 | 인증 후 dashboard `UNVERIFIED` |
| Pets | PetFriends Home `CONFIRMED` | PawCycle 승인 pet fields | pet type context와 이름·유형·품종·체중만 | 외부 profile 관리 UI `UNVERIFIED` |
| Notifications | Musinsa header/wishlist `CONFIRMED` | PawCycle in-app API | issue/정보 행과 unread 상태, 목적지 link | 외부 notification center `UNVERIFIED`; email/push 제외 |
| Addresses | IKEA Checkout `CONFIRMED` | PawCycle address API | checkout context 복귀, 기본/기타 주소, full-screen mobile edit | 인증 후 주소 관리 UI `UNVERIFIED` |
| Billing | IKEA Checkout `CONFIRMED` | Petco official help `INDIRECT` | 호출 화면 복귀, 기본 결제수단, provider cancel | 인증 후 결제 관리 UI `UNVERIFIED` |
| Login | Musinsa Login `CONFIRMED` | IKEA login return link `CONFIRMED` | visible labels, reveal, recovery, 안전한 GET 복귀 | 소셜 로그인 방식 복제 제외 |
| Notice/FAQ/Support | IKEA Support `CONFIRMED` | PetSmart Help `INDIRECT` | local navigation, FAQ disclosure, 주문 context 지원 | 채팅·새 지원 채널 |
| Mobile Header/PLP/PDP/Cart/Checkout | IKEA 375px `CONFIRMED` | PetFriends mobile-first `CONFIRMED` | 56+48 header, chip filter, 2열 card, 단일 PDP, bottom transaction CTA, 단계 잠금 | desktop 축소만 한 반응형 `REJECT` |

## 증거 일관성 판정

- Chewy·Olive Young·Petco 실제 UI가 차단되거나 열리지 않은 지점은 즉시 Kurly·Musinsa·IKEA·PetFriends의 접근 가능한 실제 commerce로 대체했다.
- `CONFIRMED`는 관찰한 화면·상태만 뜻한다. 계정 안쪽 화면, 실제 결제 완료, 실제 Autoship 관리 UI는 `INDIRECT` 또는 `UNVERIFIED`를 유지한다.
- 벤치마크 결과가 기존 도메인·서버 권위와 충돌하면 기존 승인 계약이 우선한다.
