/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      // Color palette from UI/UX file: SaaS General
      // Primary: Trust Blue #2563EB, Background: #F8FAFC, Text: #1E293B
      // Accent CTA: #EA580C, Destructive: #DC2626
      colors: {
        primary: {
          50:  '#EFF6FF',
          100: '#DBEAFE',
          500: '#3B82F6',
          600: '#2563EB',  // ← primary brand color
          700: '#1D4ED8',
        },
        slate: {
          50:  '#F8FAFC',  // ← page background
          100: '#F1F5F9',
          200: '#E2E8F0',
          400: '#94A3B8',
          500: '#64748B',  // ← muted text
          700: '#334155',
          800: '#1E293B',  // ← body text
          900: '#0F172A',  // ← sidebar bg
        },
      },
      fontFamily: {
        // Plus Jakarta Sans: warm, friendly SaaS font
        // JetBrains Mono: for data values, badges, cache status
        sans: ['"Plus Jakarta Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
    },
  },
  plugins: [],
}