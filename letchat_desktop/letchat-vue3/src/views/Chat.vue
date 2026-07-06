<template>
  <main class="chat-shell">
    <aside class="sidebar">
      <header class="profile">
        <button class="identity" type="button" @click="profileVisible = true">
          <div class="lc-avatar identity-avatar">{{ userSeed }}</div>
          <div class="identity-text">
            <strong>{{ userStore.displayName }}</strong>
            <span>{{ userStore.userInfo?.userId || '未登录' }}</span>
          </div>
        </button>

        <div class="profile-actions">
          <el-tooltip content="添加联系人" placement="bottom">
            <button class="lc-icon-button" type="button" @click="searchVisible = true">
              <Search />
            </button>
          </el-tooltip>
          <el-tooltip content="好友申请" placement="bottom">
            <button class="lc-icon-button badge-wrap" type="button" @click="applyVisible = true">
              <Bell />
              <span v-if="chatStore.pendingApplyCount" class="badge">{{ chatStore.pendingApplyCount }}</span>
            </button>
          </el-tooltip>
          <el-tooltip content="退出登录" placement="bottom">
            <button class="lc-icon-button" type="button" @click="logout">
              <SwitchButton />
            </button>
          </el-tooltip>
        </div>
      </header>

      <div class="search-area">
        <el-input v-model="chatStore.keyword" placeholder="搜索联系人或群聊" clearable :prefix-icon="Search" />
        <el-segmented v-model="chatStore.activeFilter" :options="filterOptions" block />
      </div>

      <ContactList
        :contacts="chatStore.filteredContacts"
        :selected-contact="chatStore.currentContact"
        @select-contact="selectContact"
      />
    </aside>

    <section class="conversation">
      <template v-if="chatStore.currentContact">
        <header class="chat-header">
          <div class="chat-title">
            <div class="lc-avatar title-avatar" :class="{ group: chatStore.currentContact.contactType === 'G' }">
              {{ chatStore.currentContact.contactName.slice(0, 2).toUpperCase() }}
            </div>
            <div class="title-main">
              <h1>{{ chatStore.currentContact.contactName }}</h1>
              <p>{{ chatStore.currentContact.contactType === 'G' ? '群聊' : '好友' }} · {{ connectionText }}</p>
            </div>
          </div>

          <div class="header-actions">
            <span class="status-pill" :class="{ online: userStore.isOnline }">
              <i></i>
              {{ userStore.isOnline ? '实时连接' : '正在重连' }}
            </span>
            <el-tooltip content="聊天详情" placement="bottom">
              <button class="lc-icon-button" type="button" @click="contactInfoVisible = true">
                <InfoFilled />
              </button>
            </el-tooltip>
          </div>
        </header>

        <div ref="messageListRef" class="message-list" v-loading="chatStore.loadingMessages">
          <MessageBubble
            v-for="message in chatStore.messages"
            :key="message.localId || message.messageId"
            :message="message"
            @retry-message="chatStore.retryMessage"
          />

          <div v-if="!chatStore.loadingMessages && chatStore.messages.length === 0" class="empty-chat">
            <div class="empty-chip">
              <ChatLineRound />
            </div>
            <strong>还没有消息</strong>
            <span>第一条消息会从这里开始，发送状态会同步显示。</span>
          </div>
        </div>

        <footer class="composer">
          <div class="composer-tools">
            <el-tooltip content="发送媒体文件" placement="top">
              <button class="lc-icon-button" type="button" @click="fileInputRef?.click()">
                <Paperclip />
              </button>
            </el-tooltip>
            <input ref="fileInputRef" class="file-input" type="file" accept="image/*,video/*" @change="sendFile" />
            <span class="hint">Enter 发送，Shift + Enter 换行</span>
          </div>

          <div class="composer-row">
            <el-input
              v-model="messageText"
              type="textarea"
              resize="none"
              :autosize="{ minRows: 1, maxRows: 5 }"
              placeholder="输入消息..."
              @keydown.enter="handleEnter"
            />
            <el-button type="primary" :disabled="!messageText.trim()" :loading="chatStore.sending" @click="sendText">
              <Promotion />
              发送
            </el-button>
          </div>
        </footer>
      </template>

      <div v-else class="welcome">
        <div class="welcome-inner">
          <div class="welcome-kicker">
            <span :class="{ online: userStore.isOnline }"></span>
            {{ connectionText }}
          </div>
          <h1>选择左侧会话</h1>
          <p>消息、好友申请和离线记录会随连接自动同步。</p>
          <div class="welcome-actions">
            <el-button type="primary" @click="searchVisible = true">
              <Search />
              添加联系人
            </el-button>
            <el-button @click="applyVisible = true">
              <Bell />
              查看申请
            </el-button>
          </div>
        </div>

        <div class="welcome-rail" aria-hidden="true">
          <div class="rail-line"></div>
          <div class="rail-item active">
            <span></span>
            <strong>WebSocket</strong>
            <em>online</em>
          </div>
          <div class="rail-item">
            <span></span>
            <strong>RabbitMQ</strong>
            <em>ready</em>
          </div>
          <div class="rail-item">
            <span></span>
            <strong>Offline Sync</strong>
            <em>idle</em>
          </div>
        </div>
      </div>
    </section>

    <UserSearchDialog v-model="searchVisible" @friend-added="reloadContacts" />
    <FriendApplyList v-model="applyVisible" @apply-processed="reloadContacts" />
    <ContactInfoDialog v-model="contactInfoVisible" :contact="chatStore.currentContact" @changed="afterContactChanged" />
    <UserProfileDialog v-model="profileVisible" @profile-updated="userStore.refreshUserInfo" />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Bell,
  ChatLineRound,
  InfoFilled,
  Paperclip,
  Promotion,
  Search,
  SwitchButton,
} from '@element-plus/icons-vue'
import ContactInfoDialog from '@/components/ContactInfoDialog.vue'
import ContactList from '@/components/ContactList.vue'
import FriendApplyList from '@/components/FriendApplyList.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import UserProfileDialog from '@/components/UserProfileDialog.vue'
import UserSearchDialog from '@/components/UserSearchDialog.vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import type { ContactInfo } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const messageText = ref('')
const searchVisible = ref(false)
const applyVisible = ref(false)
const contactInfoVisible = ref(false)
const profileVisible = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '好友', value: 'U' },
  { label: '群聊', value: 'G' },
]

const userSeed = computed(() => userStore.displayName.slice(0, 2).toUpperCase())
const connectionText = computed(() => {
  if (userStore.socketStatus === 'open') return '连接正常'
  if (userStore.socketStatus === 'connecting') return '连接中'
  if (userStore.socketStatus === 'error') return '连接异常'
  return '未连接'
})

function scrollToBottom() {
  nextTick(() => {
    const node = messageListRef.value
    if (node) node.scrollTop = node.scrollHeight
  })
}

async function selectContact(contact: ContactInfo) {
  await chatStore.setCurrentContact(contact)
  scrollToBottom()
}

async function sendText() {
  const content = messageText.value
  if (!content.trim()) return
  messageText.value = ''
  try {
    await chatStore.sendMessage(content)
    scrollToBottom()
  } catch {
    ElMessage.error('消息发送失败')
  }
}

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  event.preventDefault()
  sendText()
}

async function sendFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('文件不能超过 50MB')
    input.value = ''
    return
  }

  try {
    await chatStore.sendFileMessage(file)
    scrollToBottom()
  } catch {
    ElMessage.error('文件发送失败')
  } finally {
    input.value = ''
  }
}

async function reloadContacts() {
  await chatStore.loadContacts()
}

async function afterContactChanged() {
  chatStore.clearCurrentContact()
  await reloadContacts()
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', { type: 'warning' })
  } catch {
    return
  }
  await userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.refreshUserInfo().catch(() => null)
  }
  chatStore.attachSocket()
  await chatStore.loadContacts()
})

onBeforeUnmount(() => {
  chatStore.detachSocket()
})

watch(
  () => chatStore.messages.length,
  () => scrollToBottom(),
)
</script>

<style scoped>
.chat-shell {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 14px;
  height: 100vh;
  padding: 16px;
}

.sidebar,
.conversation {
  min-height: 0;
  border: 1px solid rgba(255, 255, 255, 0.82);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--lc-shadow);
  backdrop-filter: blur(18px);
}

.sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.profile {
  padding: 14px;
  border-bottom: 1px solid var(--lc-line);
}

.identity {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  width: 100%;
  padding: 0;
  border: 0;
  color: var(--lc-text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.identity-avatar {
  width: 42px;
  height: 42px;
  border-radius: 13px;
}

.identity-text {
  min-width: 0;
}

.identity strong,
.identity span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity strong {
  font-size: 15px;
  font-weight: 850;
}

.identity span {
  margin-top: 2px;
  color: var(--lc-muted);
  font-size: 11px;
}

.profile-actions {
  display: flex;
  gap: 8px;
  margin-top: 13px;
}

.badge-wrap {
  position: relative;
}

.badge {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  color: #ffffff;
  font-size: 10px;
  font-weight: 800;
  line-height: 17px;
  background: var(--lc-danger);
}

.search-area {
  display: grid;
  gap: 10px;
  padding: 12px 14px 8px;
}

.conversation {
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--lc-line);
  background: rgba(255, 255, 255, 0.56);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.title-avatar {
  width: 42px;
  height: 42px;
  border-radius: 13px;
}

.title-avatar.group {
  background: linear-gradient(145deg, #33444d, #0d7c69);
}

.title-main {
  min-width: 0;
}

.chat-title h1,
.chat-title p {
  margin: 0;
}

.chat-title h1 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
  font-weight: 850;
}

.chat-title p {
  margin-top: 2px;
  color: var(--lc-muted);
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 9px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 9px;
  border: 1px solid var(--lc-line);
  border-radius: 999px;
  color: var(--lc-muted);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.74);
}

.status-pill i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lc-amber);
}

.status-pill.online i {
  background: var(--lc-accent);
  box-shadow: 0 0 0 4px rgba(13, 124, 105, 0.12);
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 20px;
  background:
    linear-gradient(rgba(248, 250, 248, 0.78), rgba(248, 250, 248, 0.78)),
    linear-gradient(90deg, rgba(13, 124, 105, 0.035) 1px, transparent 1px),
    linear-gradient(rgba(13, 124, 105, 0.028) 1px, transparent 1px);
  background-size: auto, 28px 28px, 28px 28px;
}

.empty-chat {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  min-height: 100%;
  color: var(--lc-muted);
  text-align: center;
}

.empty-chip {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--lc-line);
  border-radius: 13px;
  color: var(--lc-accent);
  background: rgba(255, 255, 255, 0.78);
}

.empty-chip :deep(svg) {
  width: 20px;
  height: 20px;
}

.empty-chat strong {
  color: var(--lc-text);
  font-size: 15px;
  font-weight: 850;
}

.empty-chat span {
  max-width: 280px;
  font-size: 12px;
}

.composer {
  padding: 12px 14px 14px;
  border-top: 1px solid var(--lc-line);
  background: rgba(255, 255, 255, 0.8);
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 9px;
}

.hint {
  color: var(--lc-soft);
  font-size: 12px;
}

.file-input {
  display: none;
}

.composer-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px;
}

.composer-row .el-button {
  height: 38px;
  border-radius: 10px;
}

.composer-row .el-button :deep(svg) {
  width: 15px;
  height: 15px;
}

.welcome {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 440px) 260px;
  align-items: center;
  justify-content: center;
  gap: 64px;
  flex: 1;
  padding: 52px;
  overflow: hidden;
  background:
    linear-gradient(110deg, rgba(255, 255, 255, 0.84), rgba(255, 255, 255, 0.62)),
    radial-gradient(circle at 78% 26%, rgba(13, 124, 105, 0.13), transparent 28%);
}

.welcome-inner {
  position: relative;
  z-index: 1;
}

.welcome-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--lc-line);
  border-radius: 999px;
  color: var(--lc-muted);
  font-size: 12px;
  background: rgba(255, 255, 255, 0.78);
}

.welcome-kicker span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lc-amber);
}

.welcome-kicker span.online {
  background: var(--lc-accent);
}

.welcome h1,
.welcome p {
  margin: 0;
}

.welcome h1 {
  margin-top: 18px;
  color: var(--lc-text);
  font-size: clamp(36px, 5vw, 66px);
  line-height: 1.02;
  font-weight: 950;
  letter-spacing: 0;
}

.welcome p {
  max-width: 380px;
  margin-top: 14px;
  color: var(--lc-muted);
}

.welcome-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.welcome-actions .el-button {
  border-radius: 10px;
}

.welcome-actions .el-button :deep(svg) {
  width: 15px;
  height: 15px;
}

.welcome-rail {
  position: relative;
  display: grid;
  gap: 14px;
  padding-left: 28px;
}

.rail-line {
  position: absolute;
  top: 20px;
  bottom: 20px;
  left: 8px;
  width: 1px;
  background: var(--lc-line-strong);
}

.rail-item {
  position: relative;
  display: grid;
  gap: 3px;
  padding: 12px 14px;
  border: 1px solid var(--lc-line);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.78);
}

.rail-item span {
  position: absolute;
  top: 18px;
  left: -25px;
  width: 10px;
  height: 10px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: var(--lc-soft);
  box-shadow: 0 0 0 1px var(--lc-line-strong);
}

.rail-item.active span {
  background: var(--lc-accent);
}

.rail-item strong {
  font-size: 13px;
  font-weight: 850;
}

.rail-item em {
  color: var(--lc-soft);
  font-size: 11px;
  font-style: normal;
}

@media (max-width: 960px) {
  .chat-shell {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 100vh;
  }

  .sidebar {
    min-height: 420px;
  }

  .conversation {
    min-height: 660px;
  }

  .welcome {
    grid-template-columns: 1fr;
  }

  .welcome-rail {
    display: none;
  }
}

@media (max-width: 640px) {
  .chat-shell {
    padding: 10px;
  }

  .chat-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .composer-row {
    grid-template-columns: 1fr;
  }

  .composer-row .el-button {
    width: 100%;
  }
}
</style>
