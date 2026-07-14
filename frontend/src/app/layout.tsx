import type { Metadata } from "next";
import { AppHeader } from "@/components/app-header";
import { AuthProvider } from "@/lib/auth-context";
import "./globals.css";

export const metadata: Metadata = {
  title: "PawCycle Commerce",
  description: "반려동물 사료 상품을 살펴보고 정기배송 구독을 관리합니다.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>
        <AuthProvider>
          <a className="skip-link" href="#main-content">
            본문으로 건너뛰기
          </a>
          <AppHeader />
          <main id="main-content" className="page-shell">
            {children}
          </main>
        </AuthProvider>
      </body>
    </html>
  );
}
