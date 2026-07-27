import { computed, ref } from 'vue'

export type ThemeMode = 'day' | 'night'

const STORAGE_KEY = 'letchat-theme'
const theme = ref<ThemeMode>('day')
let initialized = false

function applyTheme(mode: ThemeMode) {
  theme.value = mode
  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode === 'night' ? 'dark' : 'light'
  document
    .querySelector<HTMLMetaElement>('meta[name="theme-color"]')
    ?.setAttribute('content', mode === 'night' ? '#0c1511' : '#e7e4d8')
}

export function initializeTheme() {
  if (initialized || typeof window === 'undefined') return

  const savedTheme = window.localStorage.getItem(STORAGE_KEY)
  const preferredTheme: ThemeMode = window.matchMedia('(prefers-color-scheme: dark)').matches
    ? 'night'
    : 'day'
  applyTheme(savedTheme === 'day' || savedTheme === 'night' ? savedTheme : preferredTheme)
  initialized = true
}

export function useTheme() {
  initializeTheme()

  function setTheme(mode: ThemeMode) {
    applyTheme(mode)
    window.localStorage.setItem(STORAGE_KEY, mode)
  }

  function toggleTheme() {
    setTheme(theme.value === 'night' ? 'day' : 'night')
  }

  return {
    theme,
    isNight: computed(() => theme.value === 'night'),
    setTheme,
    toggleTheme,
  }
}
