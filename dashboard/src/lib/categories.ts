// The scam types Linda reports (contract 5.1 `category`), in words people use. One list for every screen.
export const CATEGORY_NAMES: Record<string, string> = {
  fake_mpesa: "Fake M-Pesa message", sent_by_mistake: "“Sent by mistake”", prize: "Prize scam", fuliza_upgrade: "Fuliza upgrade",
  kra_refund: "KRA refund", job_fee: "Job fee", loan_fee: "Loan fee", pin_request: "PIN request", phishing_link: "Phishing link", other: "Scam",
};

export const categoryName = (c: string): string => CATEGORY_NAMES[c] ?? CATEGORY_NAMES.other;
