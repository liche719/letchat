<template>
  <div class="contact-list">
    <div class="contact-item" 
         v-for="contact in contacts" 
         :key="contact.contactId"
         :class="{ active: selectedContact?.contactId === contact.contactId }"
         @click="selectContact(contact)">
      
      <div class="contact-avatar">
        <img v-if="contact.avatar" :src="contact.avatar" :alt="contact.contactName" />
        <div v-else class="avatar-placeholder">
          {{ contact.contactName?.charAt(0)?.toUpperCase() }}
        </div>
      </div>
      
      <div class="contact-info">
        <div class="contact-name">{{ contact.contactName }}</div>
        <div class="contact-last-message">{{ contact.lastMessage }}</div>
      </div>
      
      <div class="contact-meta">
        <div class="contact-time">{{ formatTime(contact.lastReceiveTime) }}</div>
        <div v-if="contact.unreadCount > 0" class="unread-badge">
          {{ contact.unreadCount > 99 ? '99+' : contact.unreadCount }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Contact } from '@/types/api'

interface Props {
  contacts: Contact[]
  selectedContact?: Contact
}

interface Emits {
  selectContact: [contact: Contact]
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const selectContact = (contact: Contact) => {
  emit('selectContact', contact)
}

const formatTime = (timestamp: string) => {
  if (!timestamp) return ''
  
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  // 今天
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { 
      hour: '2-digit', 
      minute: '2-digit' 
    })
  }
  
  // 昨天
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天'
  }
  
  // 本周内
  const weekAgo = new Date(now)
  weekAgo.setDate(weekAgo.getDate() - 7)
  if (date > weekAgo) {
    return date.toLocaleDateString('zh-CN', { weekday: 'short' })
  }
  
  // 更早
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric'
  })
}
</script>

<style scoped>
.contact-list {
  height: 100%;
  overflow-y: auto;
}

.contact-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
}

.contact-item:hover {
  background-color: #f5f5f5;
}

.contact-item.active {
  background-color: #e6f7ff;
}

.contact-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 12px;
  flex-shrink: 0;
}

.contact-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background-color: #409eff;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: bold;
}

.contact-info {
  flex: 1;
  min-width: 0;
}

.contact-name {
  font-weight: 500;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-last-message {
  font-size: 13px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-meta {
  text-align: right;
  flex-shrink: 0;
  margin-left: 8px;
}

.contact-time {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.unread-badge {
  background-color: #f56c6c;
  color: white;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 11px;
  min-width: 16px;
  text-align: center;
}
</style>