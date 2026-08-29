import Link from "next/link";

const trustSections = {
  shipping: { eyebrow: "배송 정책", title: "배송 안내", intro: "주문을 안심하고 기다릴 수 있도록 배송 기준을 한곳에 정리했어요.", sections: [["배송 시작", "결제와 주문 확인이 완료되면 배송 준비를 시작합니다. 배송 상태는 주문 상세와 알림에서 확인할 수 있어요."], ["배송지 확인", "주문 직전에 선택한 배송지를 다시 확인합니다. 주문이 만들어진 뒤에는 주문 상세의 배송지 정보가 기준입니다."], ["배송 지연", "재고나 배송 상태에 변동이 생기면 주문 상세와 알림에서 확인할 수 있습니다."]] },
  returns: { eyebrow: "교환·반품 정책", title: "교환·반품 안내", intro: "상품 상태와 주문 상태에 따라 가능한 절차가 달라질 수 있어요.", sections: [["반품 요청", "배송 완료 후 주문 상세에서 요청 가능 여부가 표시될 때 반품 사유를 입력해 요청할 수 있습니다."], ["주문 취소", "결제가 완료되고 배송 준비 중인 주문만 주문 상세의 취소 요청을 사용할 수 있습니다."], ["처리 상태", "요청·승인·수령·환불 상태는 주문 상세에서 최신 상태를 확인할 수 있습니다."]] },
} as const;

export function TrustPolicyPage({ kind }: { kind: keyof typeof trustSections }) {
  const page = trustSections[kind];
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">{page.eyebrow}</p><h1>{page.title}</h1><p>{page.intro}</p></header><div className="trust-card-list">{page.sections.map(([title, body]) => <section className="section-card" key={title}><h2>{title}</h2><p>{body}</p></section>)}</div><div className="trust-next-links"><Link className="button button-secondary" href="/faq">자주 묻는 질문</Link><Link className="button button-primary" href="/support">고객지원으로 이동</Link></div></section>;
}

export function FaqPage() {
  const questions = [["주문 금액은 어디서 확인하나요?", "주문 확인 화면과 주문 상세에서 상품 금액, 할인, 배송비, 최종 결제 금액을 확인할 수 있습니다."], ["장바구니 가격과 재고가 달라질 수 있나요?", "장바구니는 현재 상품 정보를 보여주며 주문 직전에 가격·주소·재고를 다시 확인합니다. 달라진 내용이 있으면 주문을 만들지 않고 안내합니다."], ["정기배송 배송일을 바꿀 수 있나요?", "현재 가능한 작업으로 표시되는 경우에만 다음 배송일을 변경할 수 있습니다."], ["반품 요청은 어디서 하나요?", "주문 상세에서 요청 가능할 때 반품 요청을 선택하고 사유를 입력해 주세요."]];
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">FAQ</p><h1>자주 묻는 질문</h1><p>주문, 배송, 정기배송을 이용할 때 궁금한 내용을 모았어요.</p></header><div className="trust-card-list">{questions.map(([question, answer]) => <details className="section-card faq-item" key={question}><summary>{question}</summary><p>{answer}</p></details>)}</div><Link className="button button-primary" href="/support">더 도움이 필요하신가요?</Link></section>;
}

export function NoticePage() {
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">Notice</p><h1>공지사항</h1><p>서비스 이용에 필요한 최신 안내를 확인하세요.</p></header><section className="section-card notice-list"><article><span className="tag">서비스 안내</span><h2>주문·정기배송 상태는 최신 정보 기준으로 표시됩니다.</h2><p>가격, 재고, 배송일과 주문 상태는 화면에서 임의로 계산하지 않고 확인된 결과를 표시합니다.</p></article><article><span className="tag">결제 안내</span><h2>온라인 결제는 준비 중입니다.</h2><p>현재는 주문 준비와 상태 확인을 제공하며, 결제 기능은 준비가 끝난 뒤 안내드릴 예정입니다.</p></article></section></section>;
}

export function SupportPage() {
  return <section className="trust-page"><header className="page-heading"><p className="eyebrow">Customer support</p><h1>고객지원</h1><p>주문 번호와 현재 상태를 먼저 확인하면 더 빠르게 도움을 받을 수 있어요.</p></header><div className="trust-card-list"><section className="section-card"><h2>주문 도움</h2><p>주문·배송·환불 문의는 주문 상세의 상태와 배송지를 확인한 뒤 주문 번호를 준비해 주세요.</p><Link className="button button-secondary" href="/orders">주문 내역 보기</Link></section><section className="section-card"><h2>정기배송 도움</h2><p>다음 배송일, 배송지, 보류 사유는 정기배송 상세에서 확인할 수 있습니다.</p><Link className="button button-secondary" href="/subscriptions">정기배송 보기</Link></section><section className="section-card"><h2>문의 채널</h2><p>문의 접수 채널은 운영 전환 시 연결됩니다. 연결 전에는 FAQ와 주문·정기배송 상세의 최신 상태를 확인해 주세요.</p><Link className="button button-primary" href="/faq">FAQ 확인</Link></section></div></section>;
}
