import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Linda",
  description: "Real numbers from real Linda phones: phones protected and scams caught.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
