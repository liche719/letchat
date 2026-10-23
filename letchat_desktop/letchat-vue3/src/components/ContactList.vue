<template>
  <div class="contact-list">
    <button
      v-for="contact in contacts"
      :key="contact.contactId"
      class="contact-row"
      :class="{ active: selectedContact?.contactId === contact.contactId }"
      type="button"
      :aria-current="selectedContact?.contactId === contact.contactId ? 'true' : undefined"
      :aria-label="contactLabel(contact)"
      @click="$emit('select-contact', contact)"
    >
      <div class="lc-avatar avatar" :class="{ group: contact.contactType === 'G' }">
        <UserFilled v-if="contact.contactType === 'G'" />
        <span v-else>{{ seed(contact.contactName) }}</span>
      </div>

      <div class="main">
        <div class="topline">
          <span class="name">{{ contact.contactName }}</span>
          <span class="time">{{ formatTime(contact.lastReceiveTime) }}</span>
        </div>
        <div class="subline">
          <span class="last">{{
            contact.lastMessage || (contact.contactType === 'G' ? '群聊暂无消息' : '好友暂无消息')
          }}</span>
        </div>
      </div>

      <span v-if="contact.unreadCount" class="unread">{{
        contact.unreadCount > 99 ? '99+' : contact.unreadCount
      }}</span>
    </button>

    <div v-if="loading" class="state" role="status" aria-live="polite">
      <Loading class="state-icon spinning" />
      <strong>联系人加载中…</strong>
      <span>正在同步你的联系人和群聊</span>
    </div>

    <div v-else-if="error" class="state error-state" role="alert">
      <div class="empty-mark">
        <WarningFilled />
      </div>
      <strong>{{ error }}</strong>
      <button class="retry-button" type="button" @click="$emit('retry')">重试</button>
    </div>

    <div v-else-if="contacts.length === 0" class="empty">
      <div class="empty-mark">
        <ChatDotRound />
      </div>
      <strong>{{ hasFilter ? '没有匹配的联系人' : '暂无联系人' }}</strong>
      <span>{{ hasFilter ? '换个关键词或筛选条件试试。' : '添加好友或加入群聊后，会话会显示在这里。' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChatDotRound, Loading, UserFilled, WarningFilled } from '@element-plus/icons-vue'
import type { ContactInfo } from '@/types/api'

defineProps<{
  contacts: ContactInfo[]
  selectedContact: ContactInfo | null
  loading: boolean
  error: string
  hasFilter: boolean
}>()

defineEmits<{
  'select-contact': [contact: ContactInfo]
  retry: []
}>()

function contactLabel(contact: ContactInfo) {
  const unread = contact.unreadCount ? `，${contact.unreadCount} 条未读消息` : ''
  return `${contact.contactName}${unread}`
}

function seed(name: string) {
  return name?.slice(0, 2).toUpperCase() || 'LC'
}

function formatTime(value?: string | number) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return '昨天'
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
.contact-list {
  min-height: 0;
  padding: 0 8px 12px;
  overflow-y: auto;
}

.contact-row {
  position: relative;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 11px;
  width: 100%;
  padding: 11px 9px;
  border: 0;
  border-bottom: 1px solid var(--lc-line);
  border-left: 2px solid transparent;
  color: var(--lc-text);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 160ms ease,
    background 160ms ease,
    transform 160ms ease;
}

.contact-row:hover {
  background: var(--lc-panel);
}

.contact-row:focus-visible {
  outline: 2px solid var(--lc-focus);
  outline-offset: -2px;
}

.contact-row.active {
  border-left-color: var(--lc-accent);
  background: var(--lc-accent-soft);
}

.avatar {
  width: 40px;
  height: 40px;
  font-size: 11px;
}

.avatar :deep(svg) {
  width: 16px;
  height: 16px;
}

.avatar.group {
  border-color: var(--lc-brass);
  background: var(--lc-brass);
  box-shadow: 0 0 0 3px var(--lc-brass-soft);
}

.main {
  min-width: 0;
}

.topline,
.subline {
  display: flex;
  align-items: center;
  min-width: 0;
}

.topline {
  justify-content: space-between;
  gap: 8px;
}

.name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
}

.time {
  flex: 0 0 auto;
  color: var(--lc-soft);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.subline {
  margin-top: 4px;
  color: var(--lc-muted);
  font-size: 12px;
}

.last {
  min-width: 0;
  padding-right: 26px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread {
  position: absolute;
  right: 9px;
  bottom: 11px;
  display: grid;
  place-items: center;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border: 2px solid var(--lc-surface);
  border-radius: 50%;
  color: #fff7ec;
  background: var(--lc-coral);
  font-size: 10px;
  font-weight: 700;
  line-height: 13px;
}

.empty {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  min-height: 260px;
  padding: 34px 22px;
  color: var(--lc-soft);
  text-align: center;
}

.state {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  min-height: 260px;
  padding: 34px 22px;
  color: var(--lc-soft);
  text-align: center;
}

.state strong {
  margin-top: 8px;
  color: var(--lc-text);
  font-size: 14px;
  font-weight: 600;
}

.state span {
  max-width: 220px;
  font-size: 12px;
  line-height: 1.6;
}

.state-icon {
  width: 22px;
  height: 22px;
  color: var(--lc-accent);
}

.spinning {
  animation: contact-list-spin 900ms linear infinite;
}

.error-state {
  color: var(--lc-danger);
}

.error-state .empty-mark {
  color: var(--lc-danger);
}

.retry-button {
  padding: 6px 12px;
  border: 1px solid var(--lc-line-strong);
  border-radius: 8px;
  color: var(--lc-accent);
  background: var(--lc-control-bg);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
}

.retry-button:hover {
  border-color: var(--lc-accent);
  background: var(--lc-accent-soft);
}

.retry-button:focus-visible {
  outline: 2px solid var(--lc-focus);
  outline-offset: 2px;
}

.empty-mark {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--lc-line-strong);
  border-radius: 50%;
  color: var(--lc-muted);
  background: var(--lc-surface);
}

.empty-mark::after {
  position: absolute;
  inset: -8px;
  border: 1px dashed var(--lc-line-strong);
  border-radius: 50%;
  content: '';
}

.empty-mark :deep(svg) {
  width: 18px;
  height: 18px;
}

.empty strong {
  margin-top: 8px;
  color: var(--lc-text);
  font-size: 14px;
  font-weight: 600;
}

.empty span {
  max-width: 190px;
  font-size: 12px;
  line-height: 1.6;
}

@keyframes contact-list-spin {
  to { transform: rotate(360deg); }
}
</style>
