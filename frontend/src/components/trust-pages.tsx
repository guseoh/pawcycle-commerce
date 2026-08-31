import Link from "next/link";

const trustSections = {
  shipping: {
    eyebrow: "배송 정책",
    title: "배송 안내",
    intro: "주문을 안심하고 기다릴 수 있도록 배송 기준을 한곳에 정리했어요.",
    sections: [
      ["배송 시작", "주문 확인이 완료되면 배송 준비를 시작합니다. 배송 상태는 주문 상세와 알림에서 확인할 수 있어요."],
      ["배송지 확인", "주문하기 전에 선택한 배송지를 다시 확인합니다. 주문이 완료된 뒤에는 주문 상세에서 실제 배송지를 확인할 수 있어요."],
      ["배송 지연", "재고 또는 배송 일정에 변동이 생기면 주문 상세와 알림에서 최신 내용을 확인할 수 있습니다."],
    ],
  },
  returns: {
    eyebrow: "교환·반품 정책",
    title: "교환·반품 안내",
    intro: "상품 상태와 주문 진행 단계에 따라 이용할 수 있는 절차가 달라질 수 있어요.",
    sections: [
      ["반품 요청", "배송 완료 후 반품을 요청할 수 있는 주문에는 주문 상세에 반품 요청 메뉴가 표시됩니다. 사유를 입력해 접수해 주세요."],
      ["주문 취소", "취소할 수 있는 주문에는 주문 상세에 주문 취소 버튼이 표시됩니다. 배송 준비가 진행된 뒤에는 취소가 제한될 수 있어요."],
      ["처리 상태", "반품과 환불의 진행 상태는 주문 상세에서 확인할 수 있습니다. 상태가 변경되면 최신 내용이 화면에 반영됩니다."],
    ],
  },
} as const;

export function TrustPolicyPage({ kind }: { kind: keyof typeof trustSections }) {
  const page = trustSections[kind];
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">{page.eyebrow}</p><h1>{page.title}</h1><p>{page.intro}</p></header><div className="trust-card-list">{page.sections.map(([title, body]) => <section className="section-card" key={title}><h2>{title}</h2><p>{body}</p></section>)}</div><div className="trust-next-links"><Link className="button button-secondary" href="/faq">자주 묻는 질문</Link><Link className="button button-primary" href="/support">고객지원으로 이동</Link></div></section>;
}

export function FaqPage() {
  const questions = [
    ["주문 금액은 어디서 확인하나요?", "주문하기 화면과 주문 상세에서 상품 금액, 할인, 배송비와 최종 결제 금액을 확인할 수 있습니다."],
    ["장바구니 가격과 재고가 달라질 수 있나요?", "장바구니에 담은 뒤에도 가격이나 재고가 변경될 수 있습니다. 주문하기 직전에 최신 정보를 다시 확인하며, 구매할 수 없는 경우에는 이유를 안내합니다."],
    ["정기배송 배송일을 바꿀 수 있나요?", "배송일을 변경할 수 있는 상태라면 정기배송 상세에서 배송일 변경 메뉴를 이용할 수 있습니다."],
    ["반품 요청은 어디서 하나요?", "반품을 요청할 수 있는 주문에는 주문 상세에 반품 요청 메뉴가 표시됩니다. 해당 메뉴에서 사유를 입력해 주세요."],
  ];
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">FAQ</p><h1>자주 묻는 질문</h1><p>주문, 배송, 정기배송을 이용할 때 궁금한 내용을 모았어요.</p></header><div className="trust-card-list">{questions.map(([question, answer]) => <details className="section-card faq-item" key={question}><summary>{question}</summary><p>{answer}</p></details>)}</div><Link className="button button-primary" href="/support">더 도움이 필요하신가요?</Link></section>;
}

export function NoticePage() {
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">Notice</p><h1>공지사항</h1><p>서비스 이용에 필요한 최신 안내를 확인하세요.</p></header><section className="section-card notice-list"><article><span className="tag">서비스 안내</span><h2>주문과 정기배송의 최신 상태를 상세 화면에서 확인해 주세요.</h2><p>가격, 재고, 배송일과 진행 상태가 바뀌면 주문 상세 또는 정기배송 상세에서 최신 내용을 확인할 수 있습니다.</p></article><article><span className="tag">결제 안내</span><h2>외부 결제 기능은 아직 제공하지 않습니다.</h2><p>현재는 주문을 준비하고 상태를 확인하는 흐름까지 이용할 수 있습니다. 외부 결제가 제공되기 전에는 실제 결제가 완료되지 않습니다.</p></article></section></section>;
}

export function SupportPage() {
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">Customer support</p><h1>고객지원</h1><p>주문 번호와 현재 상태를 먼저 확인하면 필요한 정보를 더 빠르게 찾을 수 있어요.</p></header><div className="trust-card-list"><section className="section-card"><h2>주문 도움</h2><p>주문·배송·환불 관련 내용은 주문 상세에서 현재 상태와 배송지를 먼저 확인해 주세요.</p><Link className="button button-secondary" href="/orders">주문 내역 보기</Link></section><section className="section-card"><h2>정기배송 도움</h2><p>다음 배송일, 배송지와 변경 가능한 항목은 정기배송 상세에서 확인할 수 있습니다.</p><Link className="button button-secondary" href="/subscriptions">내 정기배송 보기</Link></section><section className="section-card"><h2>추가 도움이 필요할 때</h2><p>문의 접수 채널은 아직 준비 중입니다. 지금은 자주 묻는 질문과 주문·정기배송 상세에서 이용 가능한 안내를 확인해 주세요.</p><Link className="button button-primary" href="/faq">FAQ 확인</Link></section></div></section>;
}
