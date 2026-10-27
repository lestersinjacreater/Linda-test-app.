// The dashboard's one card style (docs/design-system.md 7.15): white, large corners, a soft green shadow, an optional
// small title. Every panel on the screen is one of these, so the page reads as one calm system.
export function Card({ title, right, className = "", children }: {
  title?: string; right?: React.ReactNode; className?: string; children: React.ReactNode;
}) {
  return (
    <section className={`rounded-card bg-card p-5 shadow-card ${className}`}>
      {(title || right) && (
        <div className="mb-3 flex items-center gap-3">
          {title && <h2 className="text-lg font-semibold text-ink">{title}</h2>}
          {right && <div className="ml-auto">{right}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

/** A small rounded label. Colour never alone: it always has words. */
export function Pill({ children, tone = "green" }: { children: React.ReactNode; tone?: "green" | "amber" | "red" | "grey" }) {
  const look = {
    green: "bg-mint text-deep",
    amber: "bg-amberTint text-amber",
    red: "bg-redTint text-red",
    grey: "bg-raised text-muted",
  }[tone];
  return <span className={`inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold ${look}`}>{children}</span>;
}
