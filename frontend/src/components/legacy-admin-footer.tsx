import Link from "next/link";

export function LegacyAdminFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <div className="footer-brand-copy">
          <strong>PawCycle</strong>
          <p>반복되는 반려생활을 더 편하게.</p>
        </div>
        <nav className="footer-navigation" aria-label="Footer navigation">
          <div><p className="footer-heading">쇼핑</p><Link href="/products">상품</Link><Link href="/subscriptions">정기배송</Link></div>
          <div><p className="footer-heading">주문 / 계정</p><Link href="/orders">주문 내역</Link><Link href="/my">내 정보</Link></div>
          <div><p className="footer-heading">고객지원</p><Link href="/shipping">배송 정책</Link><Link href="/returns">교환·반품</Link><Link href="/faq">FAQ</Link><Link href="/notice">공지사항</Link><Link href="/support">고객지원</Link></div>
        </nav>
      </div>
    </footer>
  );
}
