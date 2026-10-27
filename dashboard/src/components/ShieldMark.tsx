/**
 * Linda's mark (docs/design-system.md 10, after Kelvin's design): a green rounded square with a white shield and a
 * tick. The same drawing as the app's logo and launcher icon.
 */
export function ShieldMark({ size = 48 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 100 100" aria-hidden="true">
      <rect width="100" height="100" rx="24" fill="#007A3D" />
      <path d="M50 20 L74 29 L74 50 C74 66 63 76 50 81 C37 76 26 66 26 50 L26 29 Z"
        fill="none" stroke="#fff" strokeWidth="6" strokeLinejoin="round" />
      <path d="M39 50 L47 58 L62 42" fill="none" stroke="#fff" strokeWidth="6.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
