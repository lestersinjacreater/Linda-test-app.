import type { Config } from "tailwindcss";

// Linda's dashboard palette: the Safaricom greens and neutrals of docs/design-system.md (light, calm, readable on a
// projector). Red and amber only ever mean risk.
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "#F6F8F7",        // background
        card: "#FFFFFF",      // surface
        raised: "#EEF2F0",    // surfaceRaised
        ink: "#0F1A14",       // textPrimary
        muted: "#5B6B62",     // textSecondary
        line: "#DCE4DF",      // border
        green: "#00A651",     // green500: brand, "warned"
        deep: "#007A3D",      // green700: buttons, headings
        forest: "#00602F",    // green900
        mint: "#E8F6EE",      // green50: safe tint
        amber: "#8A5A00",     // caution text
        amberTint: "#FFF4D6", // caution tint
        amberDot: "#F2A900",  // a phone that got the scam
        red: "#E4002B",       // scam
        redTint: "#FDE8EC",   // scam tint
      },
      boxShadow: { card: "0 4px 16px rgba(0, 96, 47, 0.08)" },
      borderRadius: { card: "24px" },
    },
  },
  plugins: [],
};
export default config;
