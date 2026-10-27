/** Linda's mark: five rounded bars in the green ramp, forming a shield (the logo, docs/design-system.md 10). */
export function ShieldMark() {
  const tints = ["#B7E4C7", "#74C69D", "#2DBE6C", "#00A651", "#007A3D"];
  return (
    <svg width="44" height="48" viewBox="0 0 44 48" aria-hidden="true">
      <defs><clipPath id="shield"><path d="M22 2 L40 8 L38 28 Q34 40 22 46 Q10 40 6 28 L4 8 Z" /></clipPath></defs>
      <g clipPath="url(#shield)">
        {tints.map((c, i) => <rect key={c} x={4 + i * 7.2} y="0" width="7.4" height="48" fill={c} />)}
      </g>
    </svg>
  );
}
