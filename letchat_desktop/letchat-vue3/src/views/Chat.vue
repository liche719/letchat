<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <div class="sidebar">
      <!-- 用户信息 -->
      <div class="user-info">
        <el-avatar :src="userStore.userInfo?.avatar || defaultAvatar" :size="40" @click="userProfileVisible = true" style="cursor: pointer" />
        <span class="username" @click="userProfileVisible = true" style="cursor: pointer">{{ userStore.userInfo?.nickName || '用户' }}</span>
        <div class="user-actions">
          <el-button type="text" @click="userSearchVisible = true" title="搜索用户">
            <el-icon><Search /></el-icon>
          </el-button>
          <el-button type="text" @click="friendApplyVisible = true" title="好友申请">
            <el-icon><User /></el-icon>
          </el-button>
          <el-button type="text" @click="handleLogout" title="退出登录">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 搜索框 -->
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索联系人"
          prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
      </div>

      <!-- 联系人列表 -->
      <ContactList
        :contacts="filteredContacts"
        :selected-contact="currentContact"
        @select-contact="selectContact"
      />
    </div>

    <!-- 聊天区域 -->
    <div class="chat-area" v-if="currentContact">
      <!-- 聊天头部 -->
      <div class="chat-header">
        <div class="contact-title">
          <el-avatar :src="currentContact.avatar || defaultAvatar" :size="32" />
          <span>{{ currentContact.contactName }}</span>
        </div>
        <div class="chat-actions">
          <el-button type="text" @click="showContactInfo">
            <el-icon><InfoFilled /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageListRef">
        <MessageBubble
          v-for="message in messages"
          :key="message.messageId"
          :message="message"
          @retry-message="handleRetryMessage"
        />
      </div>

      <!-- 消息输入区 -->
      <div class="message-input">
        <div class="input-toolbar">
          <el-upload
            class="upload-btn"
            :action="messageUploadUrl"
            :show-file-list="false"
            :before-upload="beforeUpload"
            :headers="uploadHeaders"
            accept="image/*,video/*"
          >
            <el-button type="text">
              <el-icon><Picture /></el-icon>
            </el-button>
          </el-upload>
        </div>
        <div class="input-area">
          <el-input
            v-model="messageText"
            type="textarea"
            :rows="2"
            placeholder="请输入消息..."
            @keydown.enter="handleEnterKey"
            :disabled="!currentContact"
          />
          <el-button 
            type="primary" 
            @click="handleSendMessage"
            :disabled="!messageText.trim() || !currentContact"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div class="empty-state" v-else>
      <el-empty description="选择一个联系人开始聊天" />
    </div>

    <!-- 联系人详情对话框 -->
    <ContactInfoDialog 
      v-model="contactInfoVisible" 
      :contact="currentContact"
    />

    <!-- 搜索用户对话框 -->
    <UserSearchDialog 
      v-model="userSearchVisible"
      @friend-added="handleFriendAdded"
    />

    <!-- 好友申请列表对话框 -->
    <FriendApplyList 
      v-model="friendApplyVisible"
      @apply-processed="handleApplyProcessed"
    />

    <!-- 个人资料 -->
    <UserProfileDialog
      v-model="userProfileVisible"
      @profile-updated="handleProfileUpdated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, User, SwitchButton, InfoFilled, Picture } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import type { Contact } from '@/types/api'
import { useUploadHeaders, getUploadUrl } from '@/utils/uploadConfig'
import ContactInfoDialog from '@/components/ContactInfoDialog.vue'
import ContactList from '@/components/ContactList.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import UserSearchDialog from '@/components/UserSearchDialog.vue'
import FriendApplyList from '@/components/FriendApplyList.vue'
import UserProfileDialog from '@/components/UserProfileDialog.vue'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const searchKeyword = ref('')
const messageText = ref('')
const messageListRef = ref<HTMLElement>()
const contactInfoVisible = ref(false)
const userSearchVisible = ref(false)
const friendApplyVisible = ref(false)
const userProfileVisible = ref(false)

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// 获取上传配置
const uploadHeaders = useUploadHeaders()
const messageUploadUrl = getUploadUrl('/chat/uploadFile')

// 计算属性
const currentContact = computed(() => chatStore.currentContact)
const messages = computed(() => chatStore.messages)
const filteredContacts = computed(() => {
  if (!searchKeyword.value) return chatStore.contacts
  return chatStore.contacts.filter(contact => 
    contact.contactName.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 生命周期
onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  
  // 确保获取用户信息
  if (!userStore.userInfo) {
    await userStore.getUserInfo()
  }
  
  await chatStore.loadContacts()
  
  // 监听WebSocket消息
  if (userStore.socket) {
    userStore.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 2) { // 聊天消息类型
          chatStore.receiveMessage(data.message)
        }
      } catch (error) {
        console.error('解析WebSocket消息失败:', error)
      }
    }
  }
})

// 方法
const selectContact = async (contact: Contact) => {
  await chatStore.setCurrentContact(contact)
  chatStore.clearUnreadCount(contact.contactId)
  scrollToBottom()
}

const handleSendMessage = async () => {
  if (!messageText.value.trim() || !currentContact.value) return
  
  try {
    await chatStore.sendMessage(messageText.value, 2)
    
    messageText.value = ''
    scrollToBottom()
  } catch (error) {
    ElMessage.error('发送消息失败')
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
  })
}

const handleFriendAdded = () => {
  // 好友添加成功后刷新联系人列表
  chatStore.loadContacts()
}

const handleApplyProcessed = () => {
  // 处理好友申请后的逻辑
  chatStore.loadContacts()
}

const handleProfileUpdated = () => {
  // 个人资料更新后的逻辑
  // 用户信息已在store中更新，这里可以添加额外逻辑
}

const handleSearch = () => {
  // 搜索逻辑已在计算属性中处理
}

const showContactInfo = () => {
  contactInfoVisible.value = true
}

const beforeUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isVideo = file.type.startsWith('video/')
  
  if (!isImage && !isVideo) {
    ElMessage.error('只能上传图片或视频文件！')
    return false
  }
  
  const maxSize = 50 * 1024 * 1024 // 50MB
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过50MB！')
    return false
  }
  
  handleFileUpload(file, 5) // 媒体文件消息
  return false // 阻止自动上传
}

const handleFileUpload = async (file: File, messageType: number) => {
  if (!currentContact.value) return
  
  try {
    await chatStore.sendFileMessage({
      contactId: currentContact.value.contactId,
      file,
      messageType
    })
    scrollToBottom()
  } catch (error) {
    ElMessage.error('文件上传失败')
  }
}

const previewImage = (url: string) => {
  // 实现图片预览功能
  ElMessage.info('图片预览功能开发中...')
}

const handleRetryMessage = async (message: ChatMessage) => {
  try {
    if (message.messageType === 2) {
      // 文本消息重试
      await chatStore.sendMessage(message.messageContent, 2)
    } else {
      // 媒体消息重试（需要用户重新选择文件）
      ElMessage.warning('媒体消息请重新上传')
    }
  } catch (error) {
    ElMessage.error('重发消息失败')
  }
}

const handleEnterKey = (event: KeyboardEvent) => {
  // 如果按下了Shift键，允许换行
  if (event.shiftKey) {
    return // 允许默认行为（换行）
  }
  
  // 否则阻止默认行为并发送消息
  event.preventDefault()
  handleSendMessage()
}

// 监听消息变化，自动滚动到底部
watch(messages, () => {
  scrollToBottom()
}, { deep: true })
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
}

.sidebar {
  width: 300px;
  background: white;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.user-info {
  padding: 15px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  gap: 10px;
}

.username {
  flex: 1;
  font-weight: 500;
  font-size: 14px;
}

.user-actions {
  display: flex;
  gap: 8px;
}

.user-actions .el-button {
  padding: 4px;
  font-size: 16px;
}

.search-box {
  padding: 15px;
  border-bottom: 1px solid #e4e7ed;
}

.contact-list {
  flex: 1;
  overflow-y: auto;
}

.contact-item {
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 10px;
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

.contact-info {
  flex: 1;
  min-width: 0;
}

.contact-name {
  font-weight: 500;
  margin-bottom: 4px;
}

.last-message {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-meta {
  text-align: right;
  font-size: 12px;
}

.unread-count {
  background: #ff4d4f;
  color: white;
  border-radius: 10px;
  padding: 2px 6px;
  font-size: 11px;
  margin-bottom: 4px;
}

.last-time {
  color: #999;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
}

.chat-header {
  padding: 15px 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.contact-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
}

.message-list {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.message-item {
  display: flex;
  margin-bottom: 15px;
  gap: 10px;
}

.message-item.own {
  flex-direction: row-reverse;
}

.message-content {
  max-width: 70%;
}

.message-bubble {
  padding: 10px 15px;
  border-radius: 10px;
  word-break: break-word;
}

.message-item.other .message-bubble {
  background: #f0f0f0;
}

.message-item.own .message-bubble {
  background: #1890ff;
  color: white;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
  text-align: right;
}

.message-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  cursor: pointer;
}

.message-video {
  max-width: 300px;
  max-height: 200px;
  border-radius: 8px;
}

.message-input {
  padding: 15px;
  border-top: 1px solid #e4e7ed;
}

.input-toolbar {
  margin-bottom: 10px;
}

.upload-btn {
  display: inline-block;
}

.input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-area .el-textarea {
  flex: 1;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
}
</style>