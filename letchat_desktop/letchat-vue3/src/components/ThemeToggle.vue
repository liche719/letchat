<template>
  <button
    class="theme-toggle"
    type="button"
    :aria-label="isNight ? '切换到白天模式' : '切换到夜间模式'"
    :title="isNight ? '切换到白天模式' : '切换到夜间模式'"
    @click="toggleTheme"
  >
    <Sunny v-if="isNight" />
    <Moon v-else />
  </button>
</template>

<script setup lang="ts">
import { Moon, Sunny } from '@element-plus/icons-vue'
import { useTheme } from '@/composables/useTheme'

const { isNight, toggleTheme } = useTheme()
</script>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid var(--lc-line-strong);
  border-radius: 11px;
  color: var(--lc-muted);
  background: var(--lc-control-bg);
  cursor: pointer;
  transition:
    border-color 160ms ease,
    color 160ms ease,
    background 160ms ease,
    transform 160ms ease;
}

.theme-toggle:hover {
  border-color: var(--lc-brass);
  color: var(--lc-brass);
  background: var(--lc-control-hover);
  transform: translateY(-1px);
}

.theme-toggle:focus-visible {
  outline: 2px solid var(--lc-focus);
  outline-offset: 3px;
}

.theme-toggle svg {
  width: 17px;
  height: 17px;
}
</style>
