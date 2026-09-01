/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        serif: ['Newsreader', 'Cormorant Garamond', 'Lora', 'Georgia', 'serif'],
        sans: ['IBM Plex Sans', 'Source Sans 3', 'Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'IBM Plex Mono', 'monospace'],
      },
      colors: {
        paper: {
          50: '#FAF8F3',
          100: '#F6F3EB',
          200: '#EFEAE1',
          300: '#E4DDD2',
          400: '#D5CBBF',
          500: '#B8ABA0',
        },
        ink: {
          primary: '#24201C',
          muted: '#7C756B',
          subtle: '#A49D92',
          faint: '#D4CDC4',
          dark: '#181513',
        },
        night: {
          canvas: '#1C1A17',
          surface: '#24211D',
          surfaceHover: '#2C2823',
          border: '#332E27',
          borderSubtle: '#2A251F',
          ink: '#EDE8DF',
          muted: '#9E968B',
          subtle: '#6A6359',
        },
        terracotta: {
          DEFAULT: '#B85333',
          hover: '#9E4427',
          light: '#F5EBE6',
          dark: '#C96848',
        },
        ochre: {
          DEFAULT: '#C48A36',
          light: '#F8F1E5',
        },
      },
      boxShadow: {
        tactile: '0 1px 2px rgba(36, 32, 28, 0.04)',
        paper: '0 4px 20px -2px rgba(36, 32, 28, 0.06)',
        lifted: '0 12px 32px -4px rgba(36, 32, 28, 0.08)',
      },
    },
  },
  plugins: [],
};
