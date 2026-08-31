import type { Metadata } from "next";
import { CustomerShell } from "@/components/customer-shell";
import { AuthProvider } from "@/lib/auth-context";
import "./admin/legacy-shell.css";
import "./globals.css";
import "./shopping.css";
import "./visual-closure.css";

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
          <CustomerShell>{children}</CustomerShell>
        </AuthProvider>
      </body>
    </html>
  );
}
