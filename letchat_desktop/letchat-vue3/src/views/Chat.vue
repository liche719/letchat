<template>
  <main class="chat-shell" :class="{ 'has-active-contact': chatStore.currentContact }">
    <aside class="sidebar">
      <div class="sidebar-mapbar">
        <div class="sidebar-heading">
          <strong>消息</strong>
          <span v-if="chatStore.unreadTotal">{{ chatStore.unreadTotal }} 条未读</span>
        </div>
        <ThemeToggle />
      </div>

      <header class="profile">
        <button class="identity" type="button" @click="profileVisible = true">
          <div class="lc-avatar identity-avatar">{{ userSeed }}</div>
          <div class="identity-text">
            <strong>{{ userStore.displayName }}</strong>
            <span>查看个人资料</span>
          </div>
        </button>

        <div class="profile-actions">
          <el-tooltip content="添加联系人" placement="bottom">
            <button
              class="lc-icon-button"
              type="button"
              aria-label="添加联系人"
              @click="searchVisible = true"
            >
              <Search />
            </button>
          </el-tooltip>
          <el-tooltip content="创建群聊" placement="bottom">
            <button
              class="lc-icon-button"
              type="button"
              aria-label="创建群聊"
              @click="createGroup"
            >
              <UserFilled />
            </button>
          </el-tooltip>
          <el-tooltip content="好友申请" placement="bottom">
            <button
              class="lc-icon-button badge-wrap"
              type="button"
              aria-label="查看好友申请"
              @click="applyVisible = true"
            >
              <Bell />
              <span v-if="chatStore.pendingApplyCount" class="badge">{{
                chatStore.pendingApplyCount
              }}</span>
            </button>
          </el-tooltip>
          <el-tooltip content="退出登录" placement="bottom">
            <button class="lc-icon-button" type="button" aria-label="退出登录" @click="logout">
              <SwitchButton />
            </button>
          </el-tooltip>
        </div>
      </header>

      <div class="search-area">
        <el-input
          v-model="chatStore.keyword"
          placeholder="搜索联系人或群聊…"
          aria-label="搜索联系人或群聊"
          autocomplete="off"
          clearable
          :prefix-icon="Search"
        />
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
            <button
              class="lc-icon-button mobile-back"
              type="button"
              aria-label="返回会话列表"
              @click="chatStore.clearCurrentContact()"
            >
              <ArrowLeft />
            </button>
            <div
              class="lc-avatar title-avatar"
              :class="{ group: chatStore.currentContact.contactType === 'G' }"
            >
              {{ chatStore.currentContact.contactName.slice(0, 2).toUpperCase() }}
            </div>
            <div class="title-main">
              <h1>{{ chatStore.currentContact.contactName }}</h1>
              <p>{{ conversationSubtitle }}</p>
            </div>
          </div>

          <div class="header-actions">
            <ThemeToggle class="mobile-theme" />
            <el-tooltip content="聊天详情" placement="bottom">
              <button
                class="lc-icon-button"
                type="button"
                aria-label="查看聊天详情"
                @click="contactInfoVisible = true"
              >
                <InfoFilled />
              </button>
            </el-tooltip>
          </div>
        </header>

        <div ref="messageListRef" class="message-list" v-loading="chatStore.loadingMessages">
          <div v-if="chatStore.messages.length" class="history-action">
            <el-button
              text
              :loading="chatStore.loadingOlderMessages"
              :disabled="!chatStore.hasMoreMessages"
              @click="loadOlderMessages"
            >
              {{ chatStore.hasMoreMessages ? '加载更早消息' : '没有更早消息了' }}
            </el-button>
          </div>
          <MessageBubble
            v-for="message in chatStore.messages"
            :key="message.localId || message.messageId"
            :message="message"
            @retry-message="chatStore.retryMessage"
            @download-file="downloadFile"
          />

          <div
            v-if="!chatStore.loadingMessages && chatStore.messages.length === 0"
            class="empty-chat"
          >
            <div class="empty-chip">
              <ChatLineRound />
            </div>
            <strong>还没有消息</strong>
            <span>发一条消息，和对方打个招呼吧。</span>
          </div>
        </div>

        <footer class="composer">
          <div class="composer-tools">
            <el-tooltip content="发送媒体文件" placement="top">
              <button
                class="lc-icon-button"
                type="button"
                aria-label="发送媒体文件"
                @click="fileInputRef?.click()"
              >
                <Paperclip />
              </button>
            </el-tooltip>
            <input
              ref="fileInputRef"
              class="file-input"
              type="file"
              accept="image/*,video/*"
              aria-label="选择媒体文件"
              @change="sendFile"
            />
            <span class="hint">按回车发送，按 Shift + 回车换行</span>
          </div>

          <div class="composer-row">
            <el-input
              v-model="messageText"
              type="textarea"
              resize="none"
              :autosize="{ minRows: 1, maxRows: 5 }"
              placeholder="输入消息…"
              aria-label="消息内容"
              autocomplete="off"
              @keydown.enter="handleEnter"
            />
            <el-button
              type="primary"
              :disabled="!messageText.trim()"
              :loading="chatStore.sending"
              @click="sendText"
            >
              <Promotion />
              发送
            </el-button>
          </div>
        </footer>
      </template>

      <div v-else class="welcome">
        <div class="welcome-content">
          <div class="welcome-preview" aria-hidden="true">
            <span class="welcome-avatar">聊</span>
            <span class="welcome-bubble bubble-one"></span>
            <span class="welcome-bubble bubble-two"></span>
            <span class="welcome-bubble bubble-three"></span>
          </div>
          <h1>选择一个联系人开始聊天</h1>
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
      </div>
    </section>

    <UserSearchDialog v-model="searchVisible" @friend-added="reloadContacts" />
    <FriendApplyList v-model="applyVisible" @apply-processed="reloadContacts" />
    <ContactInfoDialog
      v-model="contactInfoVisible"
      :contact="chatStore.currentContact"
      @changed="afterContactChanged"
      @edit-group="editGroup"
    />
    <GroupEditorDialog v-model="groupEditorVisible" :group="editingGroup" @saved="afterGroupSaved" />
    <UserProfileDialog v-model="profileVisible" @profile-updated="userStore.refreshUserInfo" />
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Bell,
  ChatLineRound,
  InfoFilled,
  Paperclip,
  Promotion,
  Search,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue'
import ContactInfoDialog from '@/components/ContactInfoDialog.vue'
import ContactList from '@/components/ContactList.vue'
import FriendApplyList from '@/components/FriendApplyList.vue'
import GroupEditorDialog from '@/components/GroupEditorDialog.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import UserProfileDialog from '@/components/UserProfileDialog.vue'
import UserSearchDialog from '@/components/UserSearchDialog.vue'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { chatApi } from '@/api/chat'
import type { ChatMessage, ContactInfo, GroupInfo } from '@/types/api'

defineOptions({ name: 'ChatView' })

const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()

const messageText = ref('')
const searchVisible = ref(false)
const applyVisible = ref(false)
const contactInfoVisible = ref(false)
const profileVisible = ref(false)
const groupEditorVisible = ref(false)
const editingGroup = ref<GroupInfo | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '好友', value: 'U' },
  { label: '群聊', value: 'G' },
]

const userSeed = computed(() => userStore.displayName.slice(0, 2).toUpperCase())
const conversationSubtitle = computed(() => {
  const contact = chatStore.currentContact
  if (!contact) return ''
  if (contact.contactType !== 'G') return '好友'
  return contact.memberCount ? `${contact.memberCount} 位成员` : '群聊'
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

async function loadOlderMessages() {
  const node = messageListRef.value
  const previousHeight = node?.scrollHeight || 0
  await chatStore.loadOlderMessages()
  await nextTick()
  if (node) node.scrollTop = node.scrollHeight - previousHeight
}

async function sendText() {
  const content = messageText.value
  if (!content.trim()) return
  messageText.value = ''
  try {
    await chatStore.sendMessage(content)
    scrollToBottom()
  } catch {
    ElMessage.error('消息发送失败，请检查网络后重试')
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
    ElMessage.error('文件不能超过 50 MB')
    input.value = ''
    return
  }

  try {
    await chatStore.sendFileMessage(file)
    scrollToBottom()
  } catch {
    ElMessage.error('文件发送失败，请检查网络后重试')
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

function editGroup(group: GroupInfo | null) {
  editingGroup.value = group
  groupEditorVisible.value = true
}

function createGroup() {
  editingGroup.value = null
  groupEditorVisible.value = true
}

async function afterGroupSaved() {
  await reloadContacts()
  const group = editingGroup.value
  editingGroup.value = null
  if (group) {
    const contact = chatStore.contacts.find((item) => item.contactId === group.groupId)
    if (contact) await selectContact(contact)
  }
}

async function downloadFile(message: ChatMessage) {
  if (!message.messageId || message.messageId.startsWith('local-')) {
    ElMessage.warning('文件还未上传完成，请稍后再试')
    return
  }
  try {
    const blob = await chatApi.downloadFile(message.messageId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = message.fileName || '聊天文件'
    link.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('文件下载失败，请稍后重试')
  }
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
  grid-template-columns: 326px minmax(0, 1fr);
  gap: 12px;
  height: 100vh;
  padding: 12px;
  background:
    radial-gradient(
      circle at 88% 8%,
      color-mix(in srgb, var(--lc-accent) 7%, transparent),
      transparent 28%
    ),
    var(--lc-bg);
}

.sidebar,
.conversation {
  min-height: 0;
  border: 1px solid var(--lc-line-strong);
  border-radius: 18px;
  background: var(--lc-surface);
  box-shadow: var(--lc-shadow-soft);
}

.sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-mapbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 64px;
  padding: 0 14px 0 16px;
  border-bottom: 1px solid var(--lc-line);
  background: var(--lc-surface);
}

.sidebar-heading {
  display: flex;
  align-items: baseline;
  gap: 9px;
  min-width: 0;
}

.sidebar-heading strong,
.sidebar-heading span {
  display: block;
}

.sidebar-heading strong {
  color: var(--lc-text);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.sidebar-heading span {
  color: var(--lc-muted);
  font-size: 12px;
}

.profile {
  padding: 15px 14px 13px;
  border-bottom: 1px solid var(--lc-line);
}

.identity {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 0;
  border: 0;
  color: var(--lc-text);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.identity:focus-visible {
  outline: 2px solid var(--lc-focus);
  outline-offset: 4px;
}

.identity-avatar {
  width: 42px;
  height: 42px;
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
  font-weight: 600;
}

.identity span {
  margin-top: 3px;
  color: var(--lc-muted);
  font-size: 12px;
}

.profile-actions {
  display: flex;
  gap: 7px;
  margin-top: 14px;
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
  border: 2px solid var(--lc-surface);
  border-radius: 50%;
  color: #fff7ec;
  background: var(--lc-coral);
  font-size: 9px;
  font-weight: 700;
  line-height: 13px;
}

.search-area {
  display: grid;
  gap: 10px;
  padding: 12px 14px 9px;
  border-bottom: 1px solid var(--lc-line);
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
  min-height: 72px;
  padding: 11px 16px;
  border-bottom: 1px solid var(--lc-line);
  background: var(--lc-surface);
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.mobile-back,
.mobile-theme {
  display: none;
}

.title-avatar {
  width: 42px;
  height: 42px;
}

.title-avatar.group {
  border-color: var(--lc-brass);
  background: var(--lc-brass);
  box-shadow: 0 0 0 3px var(--lc-brass-soft);
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
  color: var(--lc-text);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
  font-weight: 600;
}

.chat-title p {
  margin-top: 3px;
  color: var(--lc-muted);
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-list {
  flex: 1;
  min-height: 0;
  padding: 18px 22px;
  background:
    radial-gradient(
      circle at 80% 12%,
      color-mix(in srgb, var(--lc-accent) 5%, transparent),
      transparent 30%
    ),
    var(--lc-surface-subtle);
  overflow-y: auto;
}

.history-action {
  display: flex;
  justify-content: center;
  min-height: 34px;
}

.empty-chat {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 9px;
  min-height: 100%;
  color: var(--lc-muted);
  text-align: center;
}

.empty-chip {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--lc-line-strong);
  border-radius: 50%;
  color: var(--lc-accent);
  background: var(--lc-surface);
}

.empty-chip::after {
  position: absolute;
  inset: -8px;
  border: 1px dashed var(--lc-line-strong);
  border-radius: 50%;
  content: '';
}

.empty-chip :deep(svg) {
  width: 18px;
  height: 18px;
}

.empty-chat strong {
  margin-top: 8px;
  color: var(--lc-text);
  font-size: 16px;
  font-weight: 600;
}

.empty-chat span {
  max-width: 280px;
  font-size: 12px;
  line-height: 1.65;
}

.composer {
  padding: 10px 14px 13px;
  border-top: 1px solid var(--lc-line);
  background: var(--lc-surface);
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 8px;
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
  gap: 9px;
}

.composer-row .el-button {
  height: 38px;
  font-size: 13px;
}

.composer-row .el-button :deep(svg) {
  width: 15px;
  height: 15px;
}

.welcome {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: grid;
  place-items: center;
  padding: 40px;
  color: var(--lc-text);
  background:
    radial-gradient(
      circle at 50% 38%,
      color-mix(in srgb, var(--lc-accent) 8%, transparent),
      transparent 34%
    ),
    var(--lc-surface-subtle);
  isolation: auto;
}

.welcome::before,
.welcome::after {
  content: none;
}

.welcome-content {
  display: grid;
  justify-items: center;
  width: min(100%, 520px);
  text-align: center;
  animation: welcome-card-in 360ms ease both;
}

.welcome-preview {
  position: relative;
  width: 230px;
  height: 170px;
  margin-bottom: 28px;
  border: 1px solid var(--lc-line);
  border-radius: 24px;
  background: var(--lc-surface);
  box-shadow: var(--lc-shadow-soft);
}

.welcome-avatar {
  position: absolute;
  top: 18px;
  left: 18px;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  color: var(--lc-on-accent);
  background: var(--lc-accent);
  font-weight: 700;
}

.welcome-bubble {
  position: absolute;
  display: block;
  height: 24px;
  border-radius: 12px;
}

.bubble-one {
  top: 26px;
  left: 73px;
  width: 116px;
  border-bottom-left-radius: 4px;
  background: var(--lc-panel);
}

.bubble-two {
  top: 78px;
  right: 20px;
  width: 130px;
  border-bottom-right-radius: 4px;
  background: var(--lc-accent);
}

.bubble-three {
  bottom: 24px;
  left: 28px;
  width: 104px;
  border-bottom-left-radius: 4px;
  background: var(--lc-panel);
}

.welcome-content h1 {
  margin: 0;
  color: var(--lc-text);
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 700;
  letter-spacing: -0.04em;
  line-height: 1.2;
  text-wrap: balance;
}

.welcome-content .welcome-actions {
  margin-top: 20px;
}

.welcome-content .welcome-actions .el-button {
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 0;
}

@keyframes welcome-card-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1050px) {
  .chat-shell {
    grid-template-columns: 300px minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .chat-shell {
    display: block;
    height: 100dvh;
    padding: 0;
  }

  .sidebar,
  .conversation {
    width: 100%;
    height: 100dvh;
    border: 0;
  }

  .chat-shell.has-active-contact .sidebar {
    display: none;
  }

  .chat-shell:not(.has-active-contact) .conversation {
    display: none;
  }

  .sidebar-mapbar {
    min-height: calc(62px + env(safe-area-inset-top));
    padding-top: env(safe-area-inset-top);
  }

  .mobile-back,
  .mobile-theme {
    display: inline-grid;
  }

  .chat-header {
    min-height: calc(64px + env(safe-area-inset-top));
    padding: calc(9px + env(safe-area-inset-top)) 11px 9px;
  }

  .title-avatar {
    width: 38px;
    height: 38px;
  }

  .header-actions {
    gap: 6px;
  }

  .message-list {
    padding: 14px 12px;
  }

  .composer {
    padding: 9px 10px calc(10px + env(safe-area-inset-bottom));
  }

  .hint {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 420px) {
  .sidebar-mapbar {
    padding-right: 11px;
    padding-left: 13px;
  }

  .profile {
    padding-right: 12px;
    padding-left: 12px;
  }

  .search-area {
    padding-right: 12px;
    padding-left: 12px;
  }

  .chat-title {
    gap: 8px;
  }

  .title-main {
    max-width: 118px;
  }

  .composer-row {
    grid-template-columns: minmax(0, 1fr) 72px;
  }

  .composer-row .el-button {
    padding-right: 10px;
    padding-left: 10px;
  }
}
</style>
