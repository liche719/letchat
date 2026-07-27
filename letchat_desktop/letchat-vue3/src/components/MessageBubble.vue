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
          <button class="file-block" type="button" @click="$emit('download-file', message)">
            <Picture v-if="isImageFile" :size="20" />
            <Document v-else :size="20" />
            <div>
              <strong>{{ message.fileName || message.messageContent || '媒体文件' }}</strong>
              <span>{{ formatFileSize(message.fileSize) }}</span>
            </div>
          </button>
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
          <button
            v-else-if="message.sendStatus === 'failed'"
            type="button"
            class="retry"
            @click="$emit('retry-message', message)"
          >
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
  'download-file': [message: ChatMessage]
}>()

const userStore = useUserStore()
const systemTypes = new Set([1, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13])

const isMine = computed(() => props.message.senderId === userStore.userInfo?.userId)
const isSystemMessage = computed(() => systemTypes.has(props.message.messageType))
const avatarSeed = computed(() => props.message.senderName?.slice(0, 2).toUpperCase() || 'LC')
const isImageFile = computed(() =>
  /\.(png|jpe?g|gif|webp|bmp)$/i.test(props.message.fileName || ''),
)

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
  margin: 15px 0;
}

.system-line span {
  max-width: min(680px, 86%);
  padding: 6px 11px;
  border-radius: 999px;
  color: var(--lc-muted);
  background: color-mix(in srgb, var(--lc-panel) 84%, transparent);
  font-size: 12px;
}

.message {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin: 15px 0;
  animation: message-in 220ms ease both;
}

.message.mine {
  justify-content: flex-end;
}

.avatar {
  width: 34px;
  height: 34px;
  font-size: 10px;
}

.bubble-wrap {
  max-width: min(68%, 680px);
}

.sender {
  margin: 0 0 4px 3px;
  color: var(--lc-muted);
  font-size: 11px;
}

.bubble {
  padding: 10px 13px;
  border: 1px solid var(--lc-line-strong);
  border-radius: 5px 16px 16px 16px;
  color: var(--lc-text);
  background: color-mix(in srgb, var(--lc-surface) 94%, transparent);
  box-shadow: var(--lc-shadow-soft);
}

.mine .bubble {
  border-color: var(--lc-accent);
  border-radius: 16px 5px 16px 16px;
  color: var(--lc-on-accent);
  background: var(--lc-accent);
  box-shadow: 0 10px 26px var(--lc-accent-soft);
}

.bubble p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.65;
}

.file-block {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.file-block:hover strong { text-decoration: underline; }
.file-block:focus-visible { outline: 2px solid currentColor; outline-offset: 4px; border-radius: 4px; }

.file-block > svg {
  flex: 0 0 20px;
  width: 20px;
  height: 20px;
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
  font-weight: 600;
}

.file-block span {
  margin-top: 3px;
  opacity: 0.72;
  font-size: 12px;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 5px;
  color: var(--lc-soft);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.status {
  width: 13px;
  height: 13px;
}

.sending {
  animation: spin 900ms linear infinite;
}

.sent {
  color: var(--lc-accent);
}

.retry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  color: var(--lc-danger);
  background: transparent;
  cursor: pointer;
  font-size: 12px;
}

.retry:focus-visible {
  outline: 2px solid var(--lc-focus);
  outline-offset: 2px;
}

@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(7px);
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

  .file-block {
    min-width: 180px;
  }
}
</style>
