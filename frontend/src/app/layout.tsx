import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "PawCycle Commerce",
  description: "PawCycle Commerce frontend application foundation",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
