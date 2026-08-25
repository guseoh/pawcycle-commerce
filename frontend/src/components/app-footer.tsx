import Link from "next/link";

export function AppFooter() {
  return (
    <footer className="site-footer">
      <div className="footer-inner">
        <div>
          <strong>PawCycle</strong>
          <p>필요한 반려생활 상품과 정기배송을 한곳에서 관리하세요.</p>
        </div>
        <nav aria-label="Footer navigation">
          <Link href="/products">상품</Link>
          <Link href="/subscriptions">정기배송</Link>
          <Link href="/orders">주문</Link>
        </nav>
      </div>
    </footer>
  );
}
