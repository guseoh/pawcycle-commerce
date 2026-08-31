import type { Metadata } from "next";
import { CustomerShell } from "@/components/customer-shell";
import { AuthProvider } from "@/lib/auth-context";
import "./admin/legacy-shell.css";
import "./globals.css";
import "./shopping.css";
import "./visual-closure.css";
import "./visual-closure-v2.css";
import "./admin-operational-v2.css";

export const metadata: Metadata = {
  title: "PawCycle Commerce",
  description: "반려동물 소모품을 쇼핑하고 정기배송을 관리하는 PawCycle입니다.",
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
