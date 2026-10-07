import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Linda radar",
  description: "Live demo: Linda phones detect a scam, the radar confirms it, the network warns everyone.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
