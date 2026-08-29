# MVP4-UX-003 Final Visual/UI Design Specification

- 작업 ID: `MVP4-UX-003`
- 역할: UX/UI Designer
- 기준 branch: `design/ux/MVP4-UX-003`
- 기준 main SHA: `3110d50fab0347a2b82f0c1af17768ed45b6951c`
- 작성일: 2026-08-29 (Asia/Seoul)
- 연결 감사: [`MVP4-UX-003 Final Product UI/UX Audit`](./MVP4-UX-003-final-product-ui-ux-audit.md)
- 구현 대상 제안: `MVP4-FE-004 — Final Product UI/UX Polish`
- 제외: Frontend/Backend 구현, API/DB/schema 변경, 새 디자인 시스템 의존성, Production/AWS/RDS/Toss/AI Provider 실행

## 1. Purpose and authority

이 문서는 기존 감사의 P0 7개, P1 15개, P2 8개와 각 UX 판정을 변경하지 않는다. 감사에서 정의한 문제를 PawCycle다운 최종 화면으로 구현할 수 있도록 시각 언어, 레이아웃, 컴포넌트, 상호작용, 반응형 구성을 구체화하는 delta 명세다. 충돌 시 승인된 제품 계약과 기존 감사의 보존 계약이 우선한다.

핵심 목표는 다음 네 가지다.

1. 실제 운영 중인 반려동물 커머스로 보이는 신뢰도
2. 상품 탐색과 구매 판단을 빠르게 하는 상거래 위계
3. 반려동물 프로필, 재구매, 다음 배송이 연결되는 PawCycle 정체성
4. 데스크톱을 축소한 화면이 아닌 독립적인 모바일 구성

본 명세는 새 기능을 승인하지 않는다. UI는 기존 API가 제공하는 값과 서버 권위 상태만 표시하며, 없는 mutation·상품 옵션·배송 약속·자동 명령을 추론하지 않는다.

## 2. Benchmark evidence and PawCycle application

벤치마크는 2026-08-29 공식 공개 페이지의 실제 화면, 공개 정보 구조, 서비스 설명을 기준으로 했다. 접근 차단이나 앱 설치 유도 shell로 본문을 충분히 볼 수 없었던 서비스는 확인 가능한 header·검색·공개 설명만 근거로 사용했다. 시각 복제는 하지 않고 검증된 패턴을 PawCycle의 도메인 계약에 맞게 변환한다.

| Reference | 확인한 visual pattern | PawCycle 적합성 | PawCycle 적용 |
| --- | --- | --- | --- |
| [PetFriends](https://m.pet-friends.co.kr/main/tab/2) | 강한 pet identity, 검색 우선 진입, 친근한 색면과 반려동물 중심 이미지 | 정체성에는 적합하나 앱 설치 중심 shell과 높은 채도는 부적합 | DOG/CAT 정적 identity와 종별 진입은 강화하되 구매 정보 영역은 차분한 cream/green으로 유지 |
| [Chewy](https://www.chewy.com/) / [Autoship](https://www.chewy.com/b/autoship-save-15682) | 상품 탐색과 Autoship을 별도 기능이 아닌 일상 구매 흐름으로 노출 | PawCycle의 반복 구매 정체성과 직접 일치 | 홈, PDP, My, Subscription에서 다음 구매/배송 시점을 동일한 언어로 연결 |
| [Petco Autoship](https://www.petco.com/shop/en/petcostore/autoship) | 반복 주문의 절약, 다음 주문, 변경 가능성을 먼저 설명 | 계약을 고객 결과로 번역하는 데 적합 | 주기·다음 날짜·예상 금액·가능 행동을 technical status보다 먼저 표시 |
| [PetSmart](https://www.petsmart.com/) / [AutoShip](https://www.petsmart.com/featured-shops/auto-ship) | 반려동물 사진 hero, category discovery, PDP의 gallery/buy box 분리, 반복 구매 benefit band | pet commerce의 기본 위계로 적합 | 홈 hero와 PDP 7:5 구성, Subscription benefit/next delivery card에 적용 |
| [OliveYoung](https://www.oliveyoung.co.kr/store/main/main.do?oy=0) | 탐색형 GNB, 캠페인 hero, 밀도 높은 merchandising에도 명확한 가격 위계 | 목록 밀도와 프로모션 위계 참고에 적합 | 상품명보다 가격·할인·평점 스캔이 빠른 card rhythm 적용 |
| [Kurly](https://www.kurly.com/main) | restrained palette, 넓은 검색, 흰 surface와 선명한 식품 이미지, 일관된 card grid | Warm Utility의 절제된 상거래 톤에 적합 | 배경은 따뜻하게, 구매 판단 surface는 밝게 유지하고 이미지 비중 확대 |
| [Coupang](https://www.coupang.com/) | category/search dominance, 재구매의 top-level 접근, 높은 정보 밀도 | 정보 우선순위에는 적합하나 전체 시각 밀도는 과함 | 로그인 사용자의 홈과 My에서 `다시 구매`를 첫 화면 행동으로 배치 |
| [Musinsa](https://www.musinsa.com/main/musinsa/recommend) | 강한 검색, 얕은 tab 구조, 타이포 위계, editorial whitespace | 제목/탭 위계와 캠페인 구획에 적합 | 큰 section heading과 얕은 tab, 장식보다 상품 이미지 중심의 rhythm 적용 |

벤치마크의 공통 결론은 “더 많은 장식”이 아니라 이미지, 가격, 상태, 다음 행동의 위계를 선명하게 만드는 것이다. PawCycle은 고채도 장난감 느낌이나 과도한 rounded/pill UI를 피하고, 따뜻한 배경 위에 정확한 상거래 정보를 배치한다.

## 3. Visual north star — Warm Utility Commerce

### 3.1 Visual statement

PawCycle의 최종 톤은 **Warm Utility Commerce**다. 따뜻한 ivory canvas와 자연스러운 forest green을 기본으로 하며, 반려동물 사진과 반복 배송을 나타내는 clay accent가 감정적 온도를 더한다. 표면은 조용하고, 상품과 다음 행동은 선명하다.

- **Warm**: cream canvas, 자연광 반려동물 사진, 둥글지만 과장되지 않은 모서리, 다정하고 결과 중심인 문구
- **Utility**: 가격·재고·배송일·상태·가능 행동이 장식보다 우선하고, 긴 관리 화면은 명확한 구역으로 나뉨
- **Commerce**: 이미지 품질, 가격 위계, CTA 대비, 신뢰 정보, sticky purchase action이 모든 핵심 화면에서 일관됨
- **PawCycle signature**: pet profile과 `다음 배송`을 홈·상품·주문·구독·My 전반에서 연결하되 자동 command는 보내지 않음

### 3.2 Avoid

- 모든 card와 chip을 큰 pill로 만드는 방식
- pet identity를 발바닥 emoji와 장식 아이콘으로만 표현하는 방식
- gradient, glassmorphism, 강한 drop shadow, 자동 재생 carousel
- 고객 화면에 `Provider`, `서버`, `SKU`, raw enum, fixture 이름을 노출하는 방식
- 가격·다음 배송·오류보다 마케팅 문구를 먼저 보이게 하는 구성
- mobile에서 desktop section을 그대로 한 열로 쌓는 구성

## 4. Foundation specification

### 4.1 Color tokens

현재 구현의 cream/green 방향을 보존하고 중복된 `globals.css`와 `shopping.css` 값은 하나의 semantic alias로 통합한다. 아래 값은 새 dependency 없이 CSS custom property로 구현한다.

| Token | Value | Usage |
| --- | --- | --- |
| `--color-canvas` | `#F7F4EC` | 전체 페이지 배경 |
| `--color-surface` | `#FFFDF8` | 기본 card, form, section |
| `--color-surface-raised` | `#FFFFFF` | modal, sticky buy bar, dropdown |
| `--color-surface-soft` | `#F3EFE3` | 구획, inactive control, skeleton base |
| `--color-ink` | `#1C2922` | 제목·본문; surface 대비 `14.87:1` |
| `--color-ink-muted` | `#65736B` | 보조 정보; surface 대비 `4.90:1` |
| `--color-line` | `#D6D9CF` | 기본 border와 divider |
| `--color-brand` | `#1F6A50` | primary CTA, active state; white 대비 `6.49:1` |
| `--color-brand-hover` | `#185741` | hover; white 대비 `8.47:1` |
| `--color-brand-pressed` | `#124532` | pressed/strong heading; white 대비 `10.92:1` |
| `--color-repeat` | `#9D4F37` | 구독·재구매 핵심 표식; surface 대비 `5.73:1` |
| `--color-repeat-soft` | `#FFF0E8` | next-delivery/Autoship accent surface |
| `--color-success-soft` | `#E7F3EC` | 성공 안내 배경 |
| `--color-warning` | `#7A4B00` | 경고 본문 |
| `--color-warning-soft` | `#FFF4D6` | 경고 안내 배경; 조합 대비 `6.76:1` |
| `--color-danger` | `#A73D32` | 오류·파괴 행동; surface 대비 `6.15:1` |
| `--color-danger-soft` | `#FFF0ED` | 오류 안내 배경 |
| `--color-focus` | `#2C7D5E` | 3px focus ring, 2px surface offset |

색만으로 상태를 구분하지 않는다. 성공·경고·오류에는 아이콘, 제목, 짧은 다음 행동을 함께 제공한다. `repeat`는 구독의 식별색이지 모든 CTA의 대체 primary가 아니다.

현재 `body`에 남아 있는 radial/linear gradient는 `MVP4-FE-004` Foundation에서 제거하고 `--color-canvas`의 단색 warm canvas를 기본으로 한다. 구획 차이는 surface, border, whitespace로 만들며 새 decorative gradient를 추가하지 않는다.

### 4.2 Typography

새 webfont 의존성을 추가하지 않는다. 실제로 로드가 보장되는 system stack을 사용하고 한글 fallback을 명시한다.

```css
font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI",
  "Apple SD Gothic Neo", "Noto Sans KR", Arial, sans-serif;
```

`Pretendard`는 현재 저장소에서 로드하거나 self-host하지 않으므로 존재한다고 가정하지 않는다. 향후 별도 승인으로 실제 font asset/`next/font` 적용이 결정되기 전까지 `MVP4-FE-004`는 새 webfont dependency를 추가하지 않는다.

| Role | Desktop | Mobile | Weight / line-height | Rule |
| --- | --- | --- | --- | --- |
| Display | `48px` | `34px` | 750 / 1.12 | Home hero 한 곳, 2줄 이하 |
| Page title | `36px` | `28px` | 750 / 1.2 | 화면당 하나 |
| Section title | `26px` | `22px` | 700 / 1.25 | section 시작 |
| Card title | `17px` | `16px` | 650 / 1.4 | 상품명은 2줄 clamp |
| Body | `16px` | `15px` | 450 / 1.6 | 설명·form helper |
| Small | `14px` | `13px` | 500 / 1.5 | brand·meta·rating |
| Label | `13px` | `12px` | 700 / 1.35 | badge·eyebrow, uppercase 최소화 |
| Price large | `30px` | `26px` | 750 / 1.15 | PDP/summary 총액 |
| Price card | `20px` | `18px` | 750 / 1.2 | card 현재가 |

본문은 최소 15px, control label은 최소 14px를 원칙으로 한다. 가격의 할인율은 `repeat` 또는 `danger` 색만으로 구분하지 않고 `%`와 현재가/정가 구조로 읽히게 한다. 영문 letter spacing은 heading `-0.02em`, 한글은 기본값을 유지한다.

### 4.3 Spacing and grid

- Base spacing: `4, 8, 12, 16, 20, 24, 32, 40, 48, 64, 80px`
- Desktop content max-width: `1240px`; page gutter `32px`
- Tablet gutter: `24px`; mobile gutter: `16px`; 320px에서 `12px`
- Desktop grid: 12 columns, gutter `24px`
- Section vertical gap: desktop `80px`, tablet `64px`, mobile `48px`
- Section 내부 title→content: desktop `24px`, mobile `16px`
- Card grid: desktop 4 columns/`24px`, tablet 3 columns/`20px`, mobile 2 columns/`12px`
- Form fields: 같은 group `16px`, group 간 `24px`, major step 간 `32px`
- 긴 관리 화면은 최대 읽기 폭 `760px`; summary rail이 있으면 `minmax(0, 8fr) / minmax(280px, 4fr)`

320px에서도 기본 상품 grid는 2열을 유지하되 이름 2줄, badge 1줄, action 1개만 노출한다. 200% text zoom 또는 container 폭이 card당 136px 미만이면 1열로 전환한다.

### 4.4 Shape and elevation

| Element | Radius | Elevation |
| --- | --- | --- |
| Input/button | `8px` | border only |
| Product/utility card | `12px` | `0 1px 2px rgba(18,69,50,.05)` |
| Feature/hero card | `16px` | `0 8px 24px rgba(28,41,34,.08)` |
| Dropdown/popover | `12px` | `0 12px 32px rgba(28,41,34,.14)` |
| Modal | `16px` | `0 24px 64px rgba(28,41,34,.20)` |
| Mobile bottom sheet | top `20px` | modal elevation |
| Badge/status | `999px` | none; pill은 짧은 상태에만 사용 |

정보 card는 border-first다. 같은 페이지의 모든 section에 shadow를 주지 않는다. 배경색, divider, whitespace로 먼저 구분하고 현재 행동이 있는 raised surface에만 elevation을 쓴다.

### 4.5 Image system

- Product list/card media: `1:1`, `object-fit: contain`, `8%` 내부 safe area, `#F3EFE3` 배경
- PDP primary gallery: `1:1`, desktop 최소 `560px`, mobile viewport 폭; thumbnail `72×72px`
- Home editorial hero: desktop `4:3` 또는 `3:2`, mobile `4:3`, `object-fit: cover`
- Pet profile identity: `1:1` DOG/CAT repository-owned static avatar/illustration을 사용한다. 현재 Pet 계약에는 photo field/upload가 없으므로 사용자 portrait를 가정하지 않는다.
- Subscription benefit/editorial: `3:2`; product packshot과 lifestyle photo를 한 카드에서 섞지 않음
- 이미지가 없거나 의미가 맞지 않으면 임의 반려동물 stock photo 대신 neutral product silhouette + `이미지 준비 중`을 사용
- `next/image`의 실제 표시 크기에 맞는 `sizes`를 제공하고 LCP hero만 우선 로드
- alt는 상품명 반복이 아니라 이미지가 구매 판단에 주는 정보만 기록하며, 장식 이미지는 빈 alt

Visual asset source 우선순위는 다음과 같이 잠근다.

1. 기존 repository-owned asset
2. 저장소에 추가되는 PawCycle original/static asset
3. 라이선스와 출처를 명확히 확인한 curated asset을 저장소에 보관해 사용하는 방식

외부 쇼핑몰 이미지 복사, 외부 서비스 image hotlink, 권리·출처가 불명확한 stock asset, 실제 상품과 무관한 pet photo를 product image 대체물로 사용하는 방식은 금지한다. 현재 canonical catalog가 제공하는 image URL은 기존 계약에 따라 사용할 수 있지만 상품 의미와 일치해야 한다. Home hero/lifestyle visual도 동일한 source 원칙을 따른다. Admin image upload 기능은 이번 MVP4 범위에 추가하지 않는다.

현재 4:3 상품 override와 1:1 catalog rule의 충돌은 1:1로 통합한다. 이 변경은 이미지 의미 불일치(P1)를 숨기지 않으며, fixture/product asset 정합성은 별도로 수정해야 한다.

## 5. Motion and feedback specification

### 5.1 Shared motion tokens

| Token | Value | Usage |
| --- | --- | --- |
| `--motion-instant` | `80ms` | press feedback |
| `--motion-fast` | `140ms` | color, border, icon state |
| `--motion-standard` | `220ms` | drawer, accordion, toast enter |
| `--motion-slow` | `320ms` | image hover, large layout reveal |
| `--ease-enter` | `cubic-bezier(.2,.8,.2,1)` | entering/moving into place |
| `--ease-exit` | `cubic-bezier(.4,0,1,1)` | exit |
| `--ease-standard` | `cubic-bezier(.2,0,0,1)` | state transition |

### 5.2 Component effects

| Location | Trigger | Visual change | Duration/easing | Reduced motion |
| --- | --- | --- | --- | --- |
| Primary button | hover/press | hover color; press `scale(.98)` | `140ms` / `80ms`, standard | color only, no scale |
| Product card | pointer hover | card `translateY(-2px)`, subtle shadow; image `scale(1.025)` | `180ms` / `320ms`, enter | no translate/scale |
| Wishlist | activate | outline→filled icon, soft background, accessible status | `180ms`, enter | immediate color/icon |
| Compare | select | border/background/check icon | `160ms`, standard | immediate state |
| Header | scroll past 16px | surface opacity and bottom shadow | `160ms`, standard | immediate |
| Tabs | select | underline/indicator 이동 | `160ms`, enter | immediate indicator |
| Filter drawer | open/close | backdrop fade, panel slide 24px | `180/240ms`, enter/exit | fade only, no slide |
| Mobile bottom sheet | open/close | backdrop fade, sheet 32px rise | `220ms`, enter/exit | fade only |
| Accordion | toggle | content reveal + chevron rotate | `200ms`, standard | immediate, no rotate |
| Sticky buy bar | threshold enter | fade + 12px rise | `180ms`, enter | immediate |
| Toast | response | fade + 8px rise; 4초 유지 | `200/140ms` | fade only |
| Skeleton | loading | low-contrast shimmer `1.4s` | linear infinite | static surface |
| Success/error | response | localized icon/surface change | `180ms`, standard | immediate; shake 금지 |

`prefers-reduced-motion: reduce`에서는 animation iteration을 1회로 제한하고 transition은 사실상 즉시 적용한다. 자동 carousel, parallax, scroll-jacking, 의미 없는 stagger reveal은 사용하지 않는다. Toast는 focus를 빼앗지 않으며 오류는 해당 field와 page-level summary 양쪽에서 텍스트로 알려준다.

## 6. Shared component specification

### 6.1 Header, navigation, footer

**Desktop header**는 2단으로 구성한다. 상단 `68px`에는 logo, 최대 `560px` 검색, wishlist/cart/notification/My를 두며 **이 상단 68px만 sticky**다. 하단 `44px`에는 상품, DOG, CAT, 카테고리, 정기배송, 주문을 두되 initial page flow에서만 보이는 **non-sticky secondary row**로 두고 스크롤 시 자연스럽게 사라진다. 따라서 스크롤 중 지속적으로 차지하는 header 높이는 `68px`로 유지한다. 검색 field는 category selector 없이 단일 명확한 input으로 유지하고 `검색어를 입력하세요`보다 상품·카테고리를 예시로 든다.

**Mobile header**는 `56px`에 menu, logo, search, cart를 배치한다. 알림과 My는 bottom navigation에 포함한다. menu button의 accessible name은 `메뉴 열기/닫기` 하나만 제공해 현재 중복된 `메뉴메뉴`를 제거한다. 검색 activation 시 header 아래 full-width search layer를 열고 추천 category와 현재 구현이 이미 보유한 recent-product context만 사용할 수 있다. **최근 검색어 저장/표시 기능은 새로 추가하지 않으며 raw query를 persistence하지 않는다.**

**Mobile bottom navigation**은 `<=640px`에서 Home, Shop, Subscription, Orders, My 5개를 제공한다. active route는 icon+label+top indicator로 표시하고 최소 `48px` 높이, safe-area inset을 포함한다. cart count와 알림 unread는 기존 서버 값이 있을 때만 badge로 표시한다.

Footer는 고객지원, 배송/반품, 개인정보/약관과 PawCycle 설명을 기본 구조로 구성한다. 사업자명·사업자번호·주소 등 사업자 정보는 승인된 authoritative source가 있을 때만 표시하고, 값이 없으면 해당 영역을 생략한다. 포트폴리오 화면을 채우기 위해 가상의 사업자 정보를 만들지 않는다. 고객지원 문구에서는 내부 구현·provider 표현을 제거한다.

### 6.2 Buttons and controls

- 모든 action target 최소 `44×44px`; mobile primary CTA는 `48px` 높이
- Primary는 화면의 한 decision group에서 하나만 사용
- Secondary는 white/brand border, tertiary는 text/icon, destructive는 danger outline을 기본으로 함
- 비활성은 opacity만 낮추지 말고 surface/ink/border를 함께 변경하며 이유를 helper text로 제공
- icon-only button은 tooltip과 accessible name을 제공
- loading button은 폭을 유지하고 label 옆 spinner를 표시; 중복 제출을 막되 성공/실패를 별도 안내
- toggle/segmented control은 선택 상태를 check 또는 indicator로 함께 표현

### 6.3 Product card

Card 높이를 강제로 같게 만들기보다 body 구조를 고정한다.

1. `1:1` media: 좌상단 subscription/benefit badge, 우상단 wishlist `44px`
2. brand/판매자 `13px muted`
3. 상품명 `16px semibold`, 최대 2줄
4. pet/category meta 또는 rating/review 한 줄
5. 가격: 할인율 → 현재가 → 정가 순서, 현재가 가장 강하게
6. 재고/배송/구독 가능성 중 구매 판단에 필요한 한 줄
7. 하단 compare checkbox + `상품 보기` action

Quick add는 필수 옵션이 없고 기존 cart mutation 계약이 확인된 상품에만 허용한다. 그 외에는 `상품 보기`를 유지해 임의 옵션·수량을 만들지 않는다. 품절은 image 위 반투명 overlay가 아니라 명확한 `품절` badge와 disabled action으로 표시한다.

### 6.4 Form, status, overlay, table

- Label은 input 위에 고정하고 placeholder를 label로 쓰지 않음
- 필수 표식은 `필수` 텍스트 또는 `*`와 범례를 제공
- error는 input 아래 1줄 + form 상단 summary; 입력값을 지우지 않음
- 성공은 form 안에서 변경된 결과와 다음 행동을 보여줌
- Filter는 desktop sidebar, mobile bottom sheet; 적용 개수와 `초기화`를 sticky footer에 표시
- Modal은 focus trap, Esc/닫기, 닫은 뒤 trigger로 focus return
- destructive modal은 대상, 영향, 복구 가능 여부, 명시적 동사를 제공
- Toast는 보조 피드백이고 주문·구독·주소 변경 같은 중요 결과는 화면 안에도 남김
- 복잡한 비교 table은 desktop sticky first column, mobile product switcher + 가로 scroll을 사용하며 heading association을 유지

### 6.5 Loading, empty, error

- Skeleton은 최종 layout과 같은 크기로 렌더링해 CLS를 줄임
- Empty는 상태 원인, 추천하는 다음 행동 하나, 보조 링크 하나를 제공
- Error는 고객 언어의 제목, 보존된 입력/상태, 재시도, 대체 경로를 제공
- 인증이 필요한 추천은 anonymous error로 보이지 않고 일반 추천 또는 로그인 benefit card로 대체
- 페이지 오류가 아닌 부분 오류는 해당 section만 대체해 나머지 구매 흐름을 유지

## 7. Screen-by-screen final specification

구현·QA에서 화면 수를 임의 해석하지 않도록 현재 route를 다음 25개 screen family로 고정한다. 같은 section에 묶인 화면도 별도 entry와 별도 상태를 가진다.

| No. | Screen | Route / scope | Detailed section |
| ---: | --- | --- | --- |
| 1 | Home | `/` | 7.1 |
| 2 | Product list | `/products` | 7.2 |
| 3 | Search/filter result | `/products` query/filter state | 7.2 |
| 4 | Product detail | `/products/[productId]` | 7.3 |
| 5 | Compare | `/compare` | 7.4 |
| 6 | Wishlist | `/wishlist` | 7.17 |
| 7 | Cart | `/cart` | 7.5 |
| 8 | Checkout | `/checkout` | 7.6 |
| 9 | Checkout success/fail | `/checkout/success`, `/checkout/fail` | 7.17 |
| 10 | Order list | `/orders` | 7.7 |
| 11 | Order detail | `/orders/[orderId]` | 7.7 |
| 12 | Subscription list | `/subscriptions` | 7.8 |
| 13 | Subscription detail | `/subscriptions/[subscriptionId]` | 7.9 |
| 14 | Subscription create | `/subscriptions/new` | 7.10 |
| 15 | My dashboard | `/my` | 7.11 |
| 16 | Pet list | `/pets` list state | 7.12 |
| 17 | Pet create/edit | `/pets` form state | 7.12 |
| 18 | Notifications | `/notifications` | 7.13 |
| 19 | Addresses | `/addresses` | 7.14 |
| 20 | Billing methods | `/billing-methods` | 7.14 |
| 21 | Login/auth-required | `/login`, protected-route state | 7.17 |
| 22 | Support information | `/notice`, `/faq`, `/support`, `/shipping`, `/returns` | 7.17 |
| 23 | Admin catalog list | `/admin/catalog` | 7.15 |
| 24 | Admin product detail | `/admin/catalog/products/[productId]` | 7.15 |
| 25 | Admin operations | `/admin/operations` | 7.15 |

### 7.1 Home — signature screen

**Desktop composition**

1. Header 아래 `24px` 후 `min-height 460px` hero, 7:5 split
2. 좌측: eyebrow `반려동물의 다음 필요한 순간`, 48px heading, 2줄 설명, primary `상품 둘러보기`, secondary `내 반려동물 등록`
3. 우측: 자연광 pet+lifestyle image. 상품 packshot 1~2개만 작게 겹치고 장식 badge는 최대 2개
4. 로그인 사용자: hero 바로 아래 `OO의 다음 루틴` 8:4 band — **재구매 후보 2~3개 + 다음 배송 요약**. Personalized 상품은 이 band에 중복 배치하지 않는다.
5. 비로그인 사용자: DOG/CAT/생활 단계 category discovery 4개
6. `지금 많이 찾는 상품` 4열 grid
7. 로그인 사용자는 `내 반려동물에게 맞는 추천`을 **단일 dedicated personalized section**으로 제공하고, 비로그인 사용자는 로그인 benefit card로 대체한다.
8. `정기배송으로 덜 잊는 일상` 3:2 visual + 세 가지 benefit
9. 신뢰 정보: 배송, 변경 가능성, 고객지원; 내부 provider 명칭 금지

**Mobile composition**

- hero는 image 위 text overlay가 아니라 text→image 순서의 독립 card, heading 34px, primary CTA 1개 full-width
- pet selector/등록은 hero 아래 DOG/CAT static avatar 또는 text chip 기반 horizontal controls로 구성하며 사용자 pet photo를 가정하지 않는다. carousel 자동 회전 금지
- 로그인 사용자는 personalized grid보다 `다음 배송` compact card와 재구매 context를 먼저 노출
- 상품은 2열, section당 4개 + `전체 보기`; 횡스크롤은 pet/category chips에만 사용
- Subscription benefit는 icon 3열이 아닌 vertical checklist + visual 하나
- bottom navigation 공간을 확보해 마지막 콘텐츠에 `96px` padding-bottom

### 7.2 Product list and search/filter

Desktop은 좌측 `240px` filter와 우측 결과 grid를 사용한다. 결과 header에 query/category title, result count, active filter chips, sort를 한 줄로 묶는다. raw query가 interaction attribution에 저장되지 않는 계약은 유지하며 UI copy에서도 query 저장을 암시하지 않는다.

Mobile은 title/result count→sort/filter sticky bar→active chips→2열 cards 순서다. Filter는 bottom sheet, sort는 짧은 single-select sheet로 분리한다. 적용 후 scroll 위치를 결과 시작점으로 복원하고 `필터 3개 적용됨`을 screen reader에 알린다. 결과 없음은 선택한 filter 요약과 `필터 초기화`를 제공한다.

### 7.3 Product detail — signature screen

Desktop은 `7:5` grid다. 좌측은 thumbnail rail + `1:1` gallery, 우측 buy box는 viewport top `96px`에서 sticky다.

Buy box 정보 순서:

1. brand, 상품명, rating/review link
2. discount/price/compare-at
3. 배송·재고·정기배송 가능 상태
4. pet fit 또는 category metadata
5. 일반 구매용 SKU option 선택과 quantity. 현재 Product Detail/cart 계약이 제공하는 값만 사용한다.
6. primary `장바구니에 담기`, wishlist/compare secondary
7. 선택한 SKU가 subscribable이면 별도 secondary entry `정기배송으로 받아보기`를 제공하고 기존 `/subscriptions/new?productId=...&skuId=...` 흐름으로 이동한다. **PDP 안에서 delivery cycle을 선택하거나 subscription command를 실행하지 않는다.**
8. 배송·변경·취소 신뢰 정보

아래 content는 상품 정보→Related→Complementary→Review Summary fallback→review 순서로 두되 fallback은 빈 카드가 아니라 일반 상품/리뷰 안내로 자연스럽게 대체한다. Mobile은 gallery→핵심 정보→purchase controls 순서이며 bottom sticky bar에 가격과 일반 구매 primary action을 둔다. Sticky bar는 화면 내 primary CTA가 보일 때 숨긴다. 정기배송 entry는 동일한 secondary navigation 성격을 유지한다.

### 7.4 Product compare

선택 상품 **2~3개**를 상단 고정 card로 표시하고 제거/교체 action을 제공한다. Desktop table의 행 순서는 가격, 구매 가능, 구독 가능, 평점, 반려동물/카테고리, 핵심 속성이다. 구매 가능 상태는 detail/card와 같은 source를 사용하고 불일치는 P0 결함으로 취급한다.

Mobile은 상품 이름 tab 또는 horizontal product header와 sticky attribute label을 사용한다. 비교 대상이 2개 미만이면 빈 table 대신 비교 방법과 `상품 찾기`를 보여준다.

### 7.5 Cart

Desktop은 items 8 columns + summary 4 columns sticky, mobile은 items→summary 순서다. Item은 image, product name/option, quantity, unit price, subtotal, current stock/purchasable state, remove를 명확히 분리한다. **현재 Cart API에 없는 subscription 여부나 정기배송 가능 상태를 Cart가 임의 추론해 표시하지 않는다.** 가격 변경/재고 문제는 item 바로 아래 warning으로 표시하고 전체 결제 CTA의 disabled 이유를 summary에도 제공한다.

Cart의 primary는 `주문서로 이동`, secondary는 `쇼핑 계속하기`다. 정기배송 전환은 별도의 승인된 지원 계약이 생기기 전까지 Cart 시각 정보나 action으로 추가하지 않는다.

### 7.6 Checkout

Desktop은 좌측 `760px` step form, 우측 `360px` sticky order summary다. Mobile은 상품 요약을 collapsed disclosure로 먼저 보여준 뒤 배송지→혜택→최종 금액 순서로 진행한다.

- Step heading은 `1 배송지`, `2 쿠폰/혜택`, `3 주문 확인`
- 주소 선택과 새 주소는 같은 layer에서 구분
- 최종 CTA는 금액과 행위를 포함해 `48,000원 주문하기`
- 실제 결제 provider를 실행하지 않는 흐름에서는 `결제`라고 오인시키지 않고 현재 승인된 주문 생성 의미를 정확히 표현
- 오류 시 입력/선택을 유지하고 실패 step으로 focus 이동
- 주문 성공은 order number, 다음 행동, 정기배송 prefill entry를 제공

### 7.7 Orders and order detail

목록은 상태 tab, 기간 filter, 주문 card로 구성한다. Card 첫 줄에 주문일/번호, 다음 줄에 대표 상품, 총액, 고객 언어 상태, 가능한 action을 둔다. `다시 담기`는 원 주문과 현재 재고/가격이 다를 수 있음을 결과 화면에서 알려준다.

상세는 progress summary→상품/금액→배송지→가능 action→정기배송 prefill 순서다. Raw enum과 내부 snapshot 문구를 고객 상태로 번역한다.

### 7.8 Subscription list — signature screen

상단에 page title와 `다음 배송` aggregate를 둔다. Subscription card는 pet/profile, plan name, 다음 배송일, 예상 금액, 상태, 최대 두 개 available action을 표시한다. HELD/recoverable issue가 있으면 정상 card보다 먼저 `조치 필요` group에 배치하되 서버의 `availableActions`만 렌더링한다.

Mobile은 status tab→issue group→active list→paused/cancelled disclosure 순서다. 새 구독 CTA는 empty state 또는 page header에만 두고 각 card에서 반복하지 않는다.

### 7.9 Subscription detail — signature screen

현재 긴 section-card 나열을 다음 zone으로 재구성한다.

1. Breadcrumb + plan/pet identity + status
2. recoverable issue가 있으면 즉시 `조치 필요` banner; 제공된 availableActions만 표시
3. `다음 배송` hero card: 날짜, 예상 금액, 기본 상품, one-time add-on, 배송 조정 action
4. Desktop 우측 summary: cycle, 주소, payment summary, status
5. pending change가 있으면 effective date와 before→after를 별도 card로 표시
6. 관리 accordion: 일정, 주기, plan, 일시정지/재개
7. danger zone: 취소만 분리

Clay `repeat` accent는 다음 배송 날짜/label에만 사용한다. Cycle Suggestion은 `추천 주기`와 근거를 보여주되 **선택 전 자동 command를 보내지 않는다**. SCHEDULED add-on은 `이번 배송에만 추가됨`을 item에 붙이고 SET/REMOVE 결과를 next delivery total에서 확인시킨다. Mobile bottom sticky에는 현재 가능한 대표 action 하나만 두고, 나머지는 overflow menu가 아니라 해당 zone 안에 명시한다.

### 7.10 Subscription create

Progressive 3-step form을 사용한다: `상품 확인` → `반려동물·주기` → `배송·최종 확인`. Order prefill로 진입하면 source order와 prefilled items를 상단 summary에서 명시한다. 변경 가능한 field와 고정된 값을 시각적으로 구분하고, server가 제공하지 않은 주기를 만들지 않는다.

Mobile에서는 step당 하나의 decision group만 보이고 sticky footer에 `이전`/`다음`을 둔다. 최종 CTA 전에는 시작일, 주기, 예상 금액, 자동 발생 여부와 변경 가능성을 다시 보여준다.

### 7.11 My

상단은 greeting보다 `다음 필요한 일`을 먼저 보여준다. Desktop 8:4 구성으로 좌측 `다시 구매`와 `추천`, 우측 `다음 배송`/pet profile summary를 둔다. 아래에는 orders, subscriptions, pets, addresses, billing, notifications를 icon+label management grid로 제공한다.

Mobile은 다음 배송→다시 구매→pet profile→관리 list 순서다. 모든 관리 목적지를 같은 크기의 card grid로 만들지 않고 빈도가 높은 행동만 visual card, 나머지는 divider list로 낮춘다.

### 7.12 Pets

목록은 DOG/CAT repository-owned static avatar, name, petType, breed, weight summary와 edit를 표시한다. 현재 계약에는 pet photo와 birth가 없으므로 둘을 UI 필드나 표시 데이터로 가정하지 않는다.

Create form은 `name → petType → breed → weight` 순서로 구성한다. Edit form은 `name → petType(read-only) → breed → weight` 순서로 구성해 immutable petType 계약을 명확히 드러낸다. `breed`와 `weightKg`의 null clear는 명확한 `값 지우기` 또는 빈 field 저장으로 표현한다. Invalid weight는 입력값을 보존하고 허용 형식을 field 바로 아래 안내한다. 성공 후 변경된 profile summary와 추천 연결을 보여준다.

### 7.13 Notifications

읽지 않음/전체 tab, 날짜 group, notification row를 사용한다. Delivery Reminder는 title과 날짜, subscription identity, `구독 보기` CTA를 제공하고 정확한 `subscriptionId` route로 이동한다. 전체 row click에만 의존하지 않고 명시적 link를 둔다.

### 7.14 Addresses and billing

Address card는 recipient, masked contact, address, 기본 badge, edit/delete를 표시한다. Billing은 실제 provider를 실행하지 않는 범위에서 저장된 표시 가능 정보만 노출하고, 미지원 상태를 내부 provider 설명으로 채우지 않는다. Delete는 사용 중인 구독/주문 영향이 API로 확인될 때만 안내하며 UI가 임의 추론하지 않는다.

### 7.15 Admin

Admin은 고객 visual system을 공유하되 정보 밀도를 한 단계 높인다. Desktop은 filter/search→table→detail drawer, mobile은 list→detail route로 구성한다. USER 접근 거부는 빈 admin shell이 아니라 고객 영역으로 돌아가는 명확한 403 상태를 제공한다. ADMIN readback은 저장 후 변경 field와 서버 응답을 화면 안에서 확인할 수 있어야 한다.

### 7.16 Global empty, error, loading

각 화면은 다음 최소 상태를 구현한다.

| State | Required visual/content |
| --- | --- |
| Loading | 최종 layout과 같은 skeleton, status text, 반복 action 비활성 |
| Empty | 무엇이 비었는지, 왜 유용한지, primary next action |
| Partial error | 해당 section만 error surface, retry, 나머지 content 유지 |
| Page error | 고객 언어 제목, retry/back, support path |
| Auth required | 로그인 benefit + 로그인 action; 추천 failure처럼 노출 금지 |
| Success | 변경 결과, effective timing, 다음 행동; toast-only 금지 |

### 7.17 Wishlist, login, checkout outcome, support

**Wishlist**는 product list와 같은 card 위계를 사용하되 선택 해제, compare, 상품 보기만 제공한다. 품절/구독 불가 등 현재 상태를 즉시 갱신하고 비어 있으면 `상품 둘러보기`를 primary로 둔다. Wishlist에서 cart quick add는 PDP와 동일한 option 계약을 충족할 때만 허용한다.

**Login/auth-required**는 centered card만 띄운 빈 화면이 아니라 좌측 PawCycle benefit와 우측 form의 5:7 desktop split을 사용한다. Mobile은 logo→짧은 benefit→form 순서다. 오류는 credential 존재 여부나 서버 내부 원인을 노출하지 않고 field/form 수준으로 전달한다. 로그인 성공 후에는 원래 보호 route와 안전한 interaction context로 돌아간다.

**Checkout success/fail**은 결과 icon만 크게 보여주는 화면을 피한다. 성공은 주문 번호·요약·배송 다음 단계·주문 상세·정기배송 prefill을, 실패는 주문이 생성되었는지 여부·보존된 cart·재시도/문의 경로를 표시한다. Provider명, raw error, 내부 승인 상태는 노출하지 않는다.

**Notice/FAQ/Support/Shipping/Returns**는 공통 help shell을 사용한다. Desktop은 좌측 topic navigation과 우측 최대 760px article, mobile은 topic selector와 accordion이다. 제목→요약→상세→문의 경로 순으로 구성하고 `서버`, `Provider`, `별도 승인 범위` 같은 구현 문구를 고객 결과·기간·필요 행동으로 바꾼다.

## 8. Before → After blueprints

### 8.1 Home

```text
BEFORE                              AFTER DESKTOP
[Header: many equal links]          [Logo | Search------------- | utilities]
[Hero text + several CTAs]          [Products DOG CAT Category Subscription]
[Pet links]                         [7 hero copy/CTA | 5 pet lifestyle image]
[Routine card]                      [Pet routine/reorder | Next delivery]
[Many similar preview sections]     [Popular 4-up products]
[Trust cards]                       [Personalized / login benefit]
                                     [Repeat-commerce story] [Trust]

AFTER MOBILE
[Menu Logo Search Cart]
[Hero copy]
[Primary CTA]
[4:3 pet image]
[DOG/CAT identity chips →]
[Next delivery / register pet]
[2-up products]
[Home Shop Subscription Orders My]
```

### 8.2 Product list/card

```text
BEFORE                              AFTER
[Title] [Sort]
[Filters] [mixed-height cards]       [Title + count] [Sort]
          [image meaning varies]     [Active filter chips]
          [meta/name/price mixed]    [240 filter | 4-up 1:1 cards]
                                      [badge]       [wishlist]
                                      [1:1 product image]
                                      brand
                                      product name (2 lines)
                                      rating / delivery
                                      discount  current price  compare-at
                                      [compare] [상품 보기]
```

### 8.3 Product detail

```text
BEFORE                              AFTER DESKTOP
[single image] [detail data]         [thumbs | 1:1 gallery] [sticky buy box]
               [purchase controls]                         brand / title / rating
[related/fallback sections]                                price
                                                            delivery / stock
                                                            option / quantity
                                                            [장바구니에 담기]
                                                            [정기배송으로 받아보기]
                                                              only when eligible
                                     [Product info]
                                     [Related] [Complementary] [Review fallback]
```

### 8.4 Checkout

```text
BEFORE                              AFTER
[Items] [1 address]                 [1 배송지----------------] [Order summary]
        [2 coupon]                  [2 쿠폰/혜택-------------] [items]
        [3 price]                   [3 주문 확인-------------] [totals]
        [주문 생성]                                             [48,000원 주문하기]

MOBILE: [Collapsed items] → [Address] → [Benefit] → [Totals] → [Sticky exact CTA]
```

### 8.5 Subscription detail

```text
BEFORE: overview → next delivery → pending → many management cards → issue

AFTER DESKTOP
[Plan + pet + status]
[ACTION REQUIRED banner, only when present----------------]
[Next delivery / items / add-ons----------------] [Cycle/address/total]
[Pending change: before → effective date → after----------]
[Schedule accordion]
[Cycle accordion]   [Plan accordion]
[Danger zone: cancel]

AFTER MOBILE
[Identity]
[Action required]
[Next delivery date + total]
[Items / one-time add-ons]
[Pending change]
[Management accordions]
[Danger zone]
[Sticky server-authoritative primary action]
```

### 8.6 My

```text
BEFORE                              AFTER
[Greeting/snapshot]                 [Next needed action]
[equal management cards]            [Reorder products------] [Next delivery]
                                     [Personalized----------] [Pet profile]
                                     [Orders | Subscription | Pets]
                                     [Addresses | Billing | Notifications]
```

### 8.7 Mobile navigation

```text
BEFORE                              AFTER
[Logo + menu + utilities]            [Menu | Logo | Search | Cart]
[large dropdown with all routes]     [contextual page content]
[content]                            [Home | Shop | Subscription | Orders | My]
```

## 9. Responsive criteria

| Width | Composition |
| --- | --- |
| `>=1280px` | 1240px max canvas, 12-column, 4-up product, 7:5 PDP/hero |
| `1024–1279px` | 24px gutter, 3-up product where necessary, 7:5 grids preserved if rails fit |
| `768–1023px` | 2-up/3-up based on container, sidebars become drawers, sticky summary remains if width >=900px |
| `641–767px` | mobile header begins, 2-up product, bottom sheets, no desktop hover dependency |
| `320–640px` | 12–16px gutter, 2-up product or zoom-safe 1-up, bottom navigation, sticky primary action |

Breakpoint만으로 판단하지 않고 content/container 폭으로 card와 form을 전환한다. 가로 scroll은 chips, gallery thumbnails, compare table처럼 의도된 영역에만 허용한다. `320×700`, `375×812`, `768×1024`, `1440×900`에서 문서 전체 overflow, sticky overlap, keyboard focus 가림을 확인한다.

## 10. Accessibility acceptance criteria

- WCAG 2.2 AA: normal text `4.5:1`, large text/UI boundary `3:1`
- keyboard로 header, filter, product action, modal, subscription action을 논리 순서대로 사용 가능
- `:focus-visible` 3px ring + 2px offset; sticky header/footer가 focused element를 가리지 않음
- target 최소 `44×44px`, 인접 target 간 최소 8px
- heading은 page title 하나와 순차 hierarchy를 유지
- card 전체 click과 내부 action의 nested interactive element 금지
- icon-only control은 accessible name, toggle은 state, loading은 busy 상태 제공
- status와 validation은 색·animation만으로 전달하지 않음
- 동적 결과는 적절한 live region으로 짧게 알리고 중요한 결과는 화면에 유지
- `prefers-reduced-motion`, 200% text resize, browser zoom 400%, forced-colors에서 핵심 흐름 유지
- `메뉴메뉴` 같은 accessible name 중복, raw enum 낭독, table heading 누락을 제거

## 11. Implementation priority and sequence

### Phase 1 — Foundation

- 중복 color/radius/elevation token을 semantic alias로 통합하고 기존 body gradient를 flat warm canvas로 교체
- 실제 로드 가능한 system typography stack, spacing, focus, motion/reduced-motion 확정
- button/input/status/skeleton/empty/error primitive 정리
- header accessible name과 68px sticky + 44px non-sticky desktop navigation, mobile navigation shell 수정
- 제품 이미지 ratio/fallback과 visual asset source/provenance 규칙 통합

### Phase 2 — Core Commerce

- Product list/filter/card
- Product detail gallery/buy box/sticky mobile CTA
- Compare availability consistency 표시
- Cart/Checkout/Order 상태·CTA·고객 문구
- 이 단계에서 기존 감사 P0 전체를 우선 해소

### Phase 3 — PawCycle Signature

- Home pet routine/personalization
- Reorder and order→subscription prefill entry
- Subscription list issue priority
- Subscription detail next-delivery zone, add-on, pending change, availableActions
- My의 next delivery/reorder/pet 연결

### Phase 4 — Supporting

- Pets, Notifications, Addresses/Billing, Admin
- 모든 route의 loading/empty/error/success 정합성
- mobile filter, compare, long management form 조작 개선

### Phase 5 — Post-deploy observation

- Production 실행 승인 후에만 Core Web Vitals, 실제 검색→PDP→checkout 이탈, subscription action 실패율을 관측
- 관측 결과 없이 animation, density, personalization 위치를 임의 최적화하지 않음
- 실제 provider/Production/AWS/RDS 실행은 별도 Platform/SRE·사용자 승인 범위

## 12. Frontend implementation map

이 표는 경로 탐색을 돕는 설계 handoff이며 파일명을 새 architecture로 강제하지 않는다.

| Area | Current implementation clue | Intended delta |
| --- | --- | --- |
| Foundation | `frontend/src/app/globals.css`, `frontend/src/styles/shopping.css` | semantic token 통합, body gradient 제거, system font stack, ratio/radius, reduced-motion, asset source 규칙 적용 |
| Header | `frontend/src/components/app-header.tsx` | 68px sticky primary + 44px non-sticky secondary desktop, 독립 mobile header/bottom nav, accessible name 수정 |
| Home | `frontend/src/app/page.tsx` | 7:5 hero, auth-aware routine/reorder 우선, personalized 단일 section, section rhythm |
| Product card | `frontend/src/components/catalog-product-card.tsx` | 1:1 media, 고정 hierarchy, state/action 정리 |
| Product routes | product list/detail/compare route components | sidebar/sheet filter, sticky buy box, current subscription entry 보존, consistent availability |
| Checkout | checkout page component | semantic 3-step labels, exact final CTA, sticky summary |
| Subscription detail | `frontend/src/components/mvp2-subscription-detail.tsx` | issue-first conditional zone, next-delivery hierarchy, management accordions |
| My/support | My/pet/order/notification/address/billing/admin routes | frequency-based hierarchy, DOG/CAT static pet identity, customer-language states |

새 icon/font/animation/design-system dependency는 추가하지 않는다. 기존 CSS와 inline SVG 또는 현재 icon primitive를 재사용한다. `availableActions`, interaction attribution, raw query 비저장, next-delivery totals, prefill, role authorization은 표시 계층이 재계산하거나 대체하지 않는다.

## 13. Visual implementation acceptance checklist

### Foundation

- [ ] 최종 computed style에서 동일 semantic token의 값이 하나다.
- [ ] 기존 body radial/linear gradient가 제거되고 flat warm canvas와 명시적 surface만 사용된다.
- [ ] 실제 로드되지 않는 Pretendard 등 font를 전제로 하지 않고 system stack으로 일관되게 표시된다.
- [ ] 모든 본문·CTA·상태 조합이 AA 대비를 만족한다.
- [ ] reduced-motion에서 transform/continuous shimmer가 제거된다.
- [ ] product image는 의미가 맞고 fallback이 fixture/debug처럼 보이지 않는다.
- [ ] 새 visual asset은 repository-owned/original 또는 명확한 라이선스·출처를 가진 자산이며 외부 commerce image hotlink/copy가 없다.

### Core commerce

- [ ] 상품 card에서 이미지→상품명→가격→상태→행동을 3초 안에 스캔할 수 있다.
- [ ] PDP desktop buy box와 mobile sticky CTA가 중복 노출되지 않는다.
- [ ] PDP의 정기배송 진입은 기존 `/subscriptions/new` flow를 사용하고 PDP 안에서 cycle/subscription command를 만들지 않는다.
- [ ] Compare는 서로 다른 2~3개 상품만 다루며 Compare/Card/PDP의 availability가 일치한다.
- [ ] Cart는 API에 없는 subscription 상태를 추론하지 않는다.
- [ ] Checkout final CTA가 금액과 실제 행위를 설명한다.
- [ ] 고객 화면에서 server/provider/raw enum/fixture 문구가 보이지 않는다.

### PawCycle signature

- [ ] 로그인 홈의 첫 두 section 안에 pet context와 다음 배송/재구매가 있고 personalized 상품 section은 중복되지 않는다.
- [ ] Pet UI는 현재 계약의 name/petType/breed/weight만 사용하고 pet photo/birth를 가정하지 않는다.
- [ ] Subscription detail에서 issue와 next delivery를 첫 viewport에서 이해할 수 있다.
- [ ] Cycle Suggestion은 사용자 선택 전 command를 보내지 않는다.
- [ ] SCHEDULED add-on SET/REMOVE 결과와 one-time 성격이 명확하다.
- [ ] HELD action은 server-authoritative `availableActions`만 사용한다.

### Responsive/accessibility

- [ ] 1440, 768, 375, 320px에서 document-level overflow가 없다.
- [ ] bottom navigation/sticky CTA가 마지막 content와 focus를 가리지 않는다.
- [ ] keyboard, screen reader name/state, 200% text, 400% zoom, reduced motion을 검증한다.
- [ ] desktop hover 없이 mobile에서 동일한 구매·관리 행동을 완료할 수 있다.

## 14. Decisions preserved and not authorized

- Personalized Recommendation과 interaction attribution 계약 유지
- Search/Filter interaction context와 raw query 비저장 유지; 최근 검색어 persistence 기능 추가 없음
- Related/Complementary/Review Summary fallback 유지
- Pet create/edit/null clear/invalid weight 처리와 immutable petType 계약 유지; birth/photo 기능 추가 없음
- Reorder Timing과 Order→Subscription prefill 유지
- PDP의 기존 일반 구매 + `/subscriptions/new` 정기배송 entry 흐름 유지
- Cart 계약에 없는 subscription 상태 추론 금지
- Cycle Suggestion의 no-auto-command 유지
- SCHEDULED add-on SET/REMOVE와 one-time 적용 유지
- recoverable HELD의 server-authoritative availableActions 유지
- Delivery Reminder의 subscriptionId routing 유지
- USER admin 거부와 ADMIN readback 유지
- Admin image upload 기능 추가 없음
- 사업자 정보는 authoritative 값이 있을 때만 표시하며 임의 데이터 생성 금지

본 문서는 추천 알고리즘, 가격 계산, 배송 약속, 상태 전이, role 정책, API/DB schema를 변경하거나 Product Complete를 선언하지 않는다. 구현 후 독립 Browser QA와 CI를 통과한 뒤 Tech Lead가 병합 준비도를 판단한다.

## 15. Handoff

Frontend는 Phase 1→4 순서로 구현하되 기존 감사의 P0/P1 우선순위를 변경하지 않는다. 디자인을 구현하기 위해 API에 없는 값이 필요하거나 Compare availability가 응답 자체에서 불일치하면 임의 표시를 만들지 않고 Backend/Product handoff를 연다. QA는 desktop과 mobile의 authenticated 실제 흐름, motion preference, accessible name, state post-verification을 함께 검증한다.

`MVP4 Product Complete`는 이 문서 작성으로 선언하지 않는다.
