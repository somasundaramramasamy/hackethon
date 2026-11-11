export default function Logo({ size = 40 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" aria-hidden="true">
      <defs>
        <linearGradient id="kv-shield" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#5b8cff" />
          <stop offset="1" stopColor="#2f5bea" />
        </linearGradient>
      </defs>
      <path d="M24 3 6 10v12c0 11.5 7.7 20.6 18 23 10.3-2.4 18-11.5 18-23V10L24 3Z" fill="url(#kv-shield)" />
      <path d="M24 7.5 10 13v9.2c0 9.2 6 16.6 14 18.7 8-2.1 14-9.5 14-18.7V13L24 7.5Z" fill="none" stroke="#fff" strokeOpacity=".55" strokeWidth="1.5" />
      <path d="M21 15h6v6h6v6h-6v6h-6v-6h-6v-6h6z" fill="#fff" />
    </svg>
  )
}
