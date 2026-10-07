import type { Config } from "tailwindcss";

// Linda's palette (root CLAUDE.md / android spec section 10): dark, vivid, readable from the back of a room.
const config: Config = {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "#0B0F1A",
        card: "#151B2E",
        cyan: "#00E5FF",
        pink: "#FF2E88",
        amber: "#FFC940",
        mint: "#35F2A0",
        muted: "#A6B0C6",
      },
    },
  },
  plugins: [],
};
export default config;
