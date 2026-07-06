<template>
  <div class="contact-list">
    <button
      v-for="contact in contacts"
      :key="contact.contactId"
      class="contact-row"
      :class="{ active: selectedContact?.contactId === contact.contactId }"
      type="button"
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
          <span class="last">{{ contact.lastMessage || (contact.contactType === 'G' ? '群聊暂无消息' : '好友暂无消息') }}</span>
        </div>
      </div>

      <span v-if="contact.unreadCount" class="unread">{{ contact.unreadCount > 99 ? '99+' : contact.unreadCount }}</span>
    </button>

    <div v-if="contacts.length === 0" class="empty">
      <div class="empty-mark">
        <ChatDotRound />
      </div>
      <strong>暂无联系人</strong>
      <span>添加好友或加入群聊后，会话会显示在这里。</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ChatDotRound, UserFilled } from '@element-plus/icons-vue'
import type { ContactInfo } from '@/types/api'

defineProps<{
  contacts: ContactInfo[]
  selectedContact: ContactInfo | null
}>()

defineEmits<{
  'select-contact': [contact: ContactInfo]
}>()

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
  overflow-y: auto;
  padding: 6px 8px 12px;
}

.contact-row {
  position: relative;
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  color: var(--lc-text);
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition:
    background 0.16s ease,
    border-color 0.16s ease,
    transform 0.16s ease;
}

.contact-row:hover {
  background: rgba(255, 255, 255, 0.72);
}

.contact-row.active {
  border-color: rgba(13, 124, 105, 0.2);
  background: #ffffff;
  box-shadow: 0 8px 22px rgba(22, 35, 31, 0.07);
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  font-size: 12px;
}

.avatar :deep(svg) {
  width: 17px;
  height: 17px;
}

.avatar.group {
  background: linear-gradient(145deg, #33444d, #0d7c69);
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
  font-weight: 780;
}

.time {
  flex: 0 0 auto;
  color: var(--lc-soft);
  font-size: 11px;
}

.subline {
  margin-top: 4px;
  color: var(--lc-muted);
  font-size: 12px;
}

.last {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread {
  position: absolute;
  right: 9px;
  bottom: 8px;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  color: #ffffff;
  font-size: 10px;
  font-weight: 800;
  text-align: center;
  line-height: 17px;
  background: var(--lc-danger);
}

.empty {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 42px 22px;
  color: var(--lc-soft);
  text-align: center;
}

.empty-mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border: 1px solid var(--lc-line);
  border-radius: 14px;
  color: var(--lc-muted);
  background: rgba(255, 255, 255, 0.66);
}

.empty-mark :deep(svg) {
  width: 21px;
  height: 21px;
}

.empty strong {
  color: var(--lc-text);
  font-size: 13px;
  font-weight: 800;
}

.empty span {
  max-width: 180px;
  font-size: 12px;
  line-height: 1.55;
}
</style>
