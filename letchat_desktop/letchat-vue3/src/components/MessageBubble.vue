<template>
  <!-- 系统消息（居中显示，特殊样式） -->
  <div v-if="[0, 1, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13].includes(message.messageType)" class="system-message-wrapper">
    <div class="system-message">
      {{ message.messageContent || getMessageTypeDesc(message.messageType) }}
    </div>
  </div>
  
  <!-- 普通消息（左右布局） -->
  <div v-else :class="['message-bubble', isMyMessage ? 'my-message' : 'other-message']">
    <div class="message-content">
      <!-- 普通聊天消息 -->
      <div v-if="message.messageType === 2" class="text-message" v-html="formatMessageContent(message.messageContent)">
      </div>
      
      <!-- 媒体文件消息 -->
      <div v-else-if="message.messageType === 5" class="image-message">
        <img v-if="message.fileName && isImage(message.fileName)" :src="message.messageContent" alt="图片" @click="previewImage" />
        <div v-else class="file-message" @click="downloadFile">
          <div class="file-info">
            <el-icon><Document /></el-icon>
            <div class="file-details">
              <div class="file-name">{{ message.fileName || '文件' }}</div>
              <div class="file-size">{{ formatFileSize(message.fileSize || 0) }}</div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 其他消息 -->
      <div v-else class="text-message" v-html="formatMessageContent(message.messageContent)">
      </div>
    </div>
    
    <div class="message-meta">
      <span class="message-time">{{ formatTime(message.sendTime) }}</span>
      <span v-if="isMyMessage" class="message-status">
        <el-icon v-if="message.sendStatus === 1" class="sending"><Loading /></el-icon>
        <el-icon v-else-if="message.sendStatus === 2" class="sent"><Check /></el-icon>
        <el-icon v-else-if="message.sendStatus === 3" class="failed" @click="retryMessage"><CircleClose /></el-icon>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { ChatMessage } from '@/types/api'

interface Props {
  message: ChatMessage
}

const props = defineProps<Props>()
const emit = defineEmits<{
  retryMessage: [message: ChatMessage]
}>()
const userStore = useUserStore()

// 判断消息是否是自己发送的
const isMyMessage = computed(() => {
  return props.message.senderId === userStore.userInfo?.userId
})

const formatTime = (timestamp: string | number) => {
  // 处理毫秒级时间戳
  const time = typeof timestamp === 'number' ? timestamp : new Date(timestamp).getTime()
  const date = new Date(time)
  return date.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const isImage = (fileName: string) => {
  const imageExtensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp']
  const extension = fileName.toLowerCase().split('.').pop()
  return extension ? imageExtensions.includes(extension) : false
}

const getMessageTypeDesc = (messageType: number) => {
  const messageTypeMap: Record<number, string> = {
    0: '连接消息',
    1: '添加好友消息',
    3: '群创建成功',
    4: '好友申请',
    6: '文件上传完成',
    7: '强制下线',
    8: '群聊已解散',
    9: '加入群聊',
    10: '更新昵称',
    11: '退出群聊',
    12: '被管理员移出群聊',
    13: '添加好友打招呼消息'
  }
  return messageTypeMap[messageType] || '未知消息类型'
}

const previewImage = () => {
  // 实现图片预览功能
  ElMessage.info('图片预览功能开发中...')
}

const downloadFile = () => {
  // 实现文件下载功能
  ElMessage.info('文件下载功能开发中...')
}

const retryMessage = () => {
  if (props.message.sendStatus === 3) {
    // 触发重试事件
    emit('retry-message', props.message)
  }
}

const formatMessageContent = (content: string) => {
  if (!content) return ''
  // 只允许<br>标签，其他HTML标签转义
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
    .replace(/&lt;br&gt;/gi, '<br/>')
    .replace(/&lt;br\/&gt;/gi, '<br/>')
    .replace(/&lt;br \/&gt;/gi, '<br/>')
}
</script>

<style scoped>
.message-bubble {
  margin-bottom: 16px;
  max-width: 70%;
}

.my-message {
  margin-left: auto;
  text-align: right;
}

.other-message {
  margin-right: auto;
  text-align: left;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  display: inline-block;
  max-width: 100%;
}

.my-message .message-content {
  background-color: #409eff;
  color: white;
}

.other-message .message-content {
  background-color: #f0f2f5;
  color: #333;
}

.text-message {
  word-break: break-word;
  white-space: pre-wrap;
}

.system-message-wrapper {
  text-align: center;
  margin: 16px 0;
}

.system-message {
  display: inline-block;
  font-size: 12px;
  color: #909399;
  background-color: #f4f4f5;
  border-radius: 16px;
  padding: 6px 12px;
  max-width: 80%;
  word-break: break-word;
}

.image-message img {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  cursor: pointer;
}

.file-message {
  cursor: pointer;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-details {
  flex: 1;
}

.file-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.file-size {
  font-size: 12px;
  opacity: 0.7;
}

.message-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.my-message .message-meta {
  text-align: right;
}

.other-message .message-meta {
  text-align: left;
}

.message-status {
  margin-left: 8px;
}

.sending {
  color: #909399;
}

.sent {
  color: #67c23a;
}

.failed {
  color: #f56c6c;
}
</style>