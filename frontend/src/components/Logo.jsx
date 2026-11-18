import { useId } from 'react'

// Kavasam means armour: a shield around a conversation that has been checked.
export const SHIELD_PATH = 'M32 4 10 12v18c0 15 9.5 26.5 22 30 12.5-3.5 22-15 22-30V12L32 4Z'
export const BUBBLE_PATH =
  'M25 20h14a6 6 0 0 1 6 6v7a6 6 0 0 1-6 6h-8l-6 5v-5a6 6 0 0 1-6-6v-7a6 6 0 0 1 6-6Z'
export const CHECK_PATH = 'M25.5 29.5 30 34l8.5-9'

export default function Logo({ size = 40 }) {
  const id = useId().replace(/:/g, '')
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient id={`${id}-shield`} x1="10" y1="4" x2="54" y2="60" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#6aa1ff" />
          <stop offset="0.55" stopColor="#3b6cf6" />
          <stop offset="1" stopColor="#7c5cf6" />
        </linearGradient>
        <linearGradient id={`${id}-shine`} x1="32" y1="4" x2="32" y2="34" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#fff" stopOpacity=".35" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={SHIELD_PATH} fill={`url(#${id}-shield)`} />
      <path d="M32 4 10 12v18c0 2 .2 3.9.5 5.8C17 30 25 27 32 27s15 3 21.5 8.8c.3-1.9.5-3.8.5-5.8V12L32 4Z" fill={`url(#${id}-shine)`} />
      <path d={BUBBLE_PATH} fill="#fff" />
      <path d={CHECK_PATH} fill="none" stroke="#3b6cf6" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
