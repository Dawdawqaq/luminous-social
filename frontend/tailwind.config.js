export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: [
          "Noto Sans SC",
          "Microsoft YaHei",
          "PingFang SC",
          "system-ui",
          "sans-serif",
        ],
        serif: [
          "Noto Sans SC",
          "Microsoft YaHei",
          "PingFang SC",
          "system-ui",
          "sans-serif",
        ],
        mono: ["Consolas", "monospace"],
      },
      colors: {
        paper: {
          50: "#FFFFFF",
          100: "#F3F4F6",
          200: "#EEF2F7",
          300: "#D1D5DB",
          400: "#9CA3AF",
          500: "#6B7280",
        },
        ink: {
          primary: "#1A1A1A",
          muted: "#526071",
          subtle: "#667085",
          faint: "#D1D5DB",
          dark: "#000000",
        },
        night: {
          canvas: "#141820",
          surface: "#1F2633",
          surfaceHover: "#2B3545",
          border: "#778296",
          borderSubtle: "#3D4657",
          ink: "#F8FAFC",
          muted: "#B9C3D2",
          subtle: "#A3AFC2",
        },
        terracotta: {
          DEFAULT: "#2563EB",
          hover: "#1D4ED8",
          light: "#EFF6FF",
          dark: "#93B4FF",
        },
        ochre: { DEFAULT: "#FDE047", light: "#FEF9C3" },
      },
      boxShadow: {
        tactile: "2px 2px 0 #1A1A1A",
        paper: "4px 4px 0 #1A1A1A",
        lifted: "6px 6px 0 #1A1A1A",
      },
    },
  },
  plugins: [],
};
