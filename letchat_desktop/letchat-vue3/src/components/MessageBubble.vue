<template>
  <div v-if="isSystemMessage" class="system-line">
    <span>{{ systemText }}</span>
  </div>

  <article v-else class="message" :class="{ mine: isMine }">
    <div v-if="!isMine" class="lc-avatar avatar">{{ avatarSeed }}</div>

    <div class="bubble-wrap">
      <div class="sender" v-if="!isMine">{{ message.senderName }}</div>
      <div class="bubble">
        <template v-if="message.messageType === 5">
          <div class="file-block">
            <Picture v-if="isImageFile" :size="20" />
            <Document v-else :size="20" />
            <div>
              <strong>{{ message.fileName || message.messageContent || '媒体文件' }}</strong>
              <span>{{ formatFileSize(message.fileSize) }}</span>
            </div>
          </div>
        </template>
        <template v-else>
          <p>{{ message.messageContent }}</p>
        </template>
      </div>

      <div class="meta">
        <span>{{ formatTime(message.sendTime) }}</span>
        <template v-if="isMine">
          <Loading v-if="message.sendStatus === 'sending'" class="status sending" />
          <Check v-else-if="message.sendStatus === 'sent'" class="status sent" />
          <button v-else-if="message.sendStatus === 'failed'" type="button" class="retry" @click="$emit('retry-message', message)">
            <CircleClose :size="14" />
            重试
          </button>
        </template>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, CircleClose, Document, Loading, Picture } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import type { ChatMessage } from '@/types/api'

const props = defineProps<{
  message: ChatMessage
}>()

defineEmits<{
  'retry-message': [message: ChatMessage]
}>()

const userStore = useUserStore()
const systemTypes = new Set([1, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13])

const isMine = computed(() => props.message.senderId === userStore.userInfo?.userId)
const isSystemMessage = computed(() => systemTypes.has(props.message.messageType))
const avatarSeed = computed(() => props.message.senderName?.slice(0, 2).toUpperCase() || 'LC')
const isImageFile = computed(() => /\.(png|jpe?g|gif|webp|bmp)$/i.test(props.message.fileName || ''))

const systemText = computed(() => {
  if (props.message.messageContent) return props.message.messageContent
  const label: Record<number, string> = {
    1: '好友申请消息',
    3: '群聊已创建',
    4: '收到新的好友申请',
    6: '文件上传完成',
    7: '当前账号已在其他设备登录',
    8: '群聊已解散',
    9: '有成员加入群聊',
    10: '昵称已更新',
    11: '有成员退出群聊',
    12: '有成员被移出群聊',
    13: '好友申请消息',
  }
  return label[props.message.messageType] || '系统消息'
})

function formatTime(value: number) {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatFileSize(bytes?: number) {
  if (!bytes) return '待上传'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}
</script>

<style scoped>
.system-line {
  display: flex;
  justify-content: center;
  margin: 14px 0;
}

.system-line span {
  max-width: min(680px, 86%);
  padding: 6px 12px;
  border: 1px solid var(--lc-line);
  border-radius: 999px;
  color: var(--lc-muted);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.7);
}

.message {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin: 14px 0;
  animation: message-in 0.22s ease both;
}

.message.mine {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  font-size: 12px;
}

.bubble-wrap {
  max-width: min(68%, 680px);
}

.sender {
  margin: 0 0 4px 4px;
  color: var(--lc-muted);
  font-size: 12px;
}

.bubble {
  padding: 10px 13px;
  border: 1px solid var(--lc-line);
  border-radius: 15px 15px 15px 5px;
  color: var(--lc-text);
  background: #ffffff;
  box-shadow: 0 10px 30px rgba(21, 36, 31, 0.08);
}

.mine .bubble {
  border-color: rgba(15, 143, 118, 0.35);
  border-radius: 15px 15px 5px 15px;
  color: #ffffff;
  background: linear-gradient(145deg, #0f8f76, #0b6c5a);
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.file-block {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
}

.file-block strong,
.file-block span {
  display: block;
}

.file-block strong {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.file-block span {
  margin-top: 3px;
  opacity: 0.74;
  font-size: 12px;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 5px;
  color: var(--lc-soft);
  font-size: 11px;
}

.status {
  width: 14px;
  height: 14px;
}

.sending {
  animation: spin 0.9s linear infinite;
}

.sent {
  color: var(--lc-accent);
}

.retry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  color: var(--lc-danger);
  background: transparent;
  cursor: pointer;
}

@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .bubble-wrap {
    max-width: 82%;
  }
}
</style>
