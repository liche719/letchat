<template>
    <main
      id="main-content"
      class="chat-shell"
      :class="{ 'has-active-contact': chatStore.currentContact }"
      tabindex="-1"
    >
    <aside class="sidebar">
      <div class="sidebar-mapbar">
        <div class="sidebar-heading">
          <strong>消息</strong>
          <span v-if="chatStore.unreadTotal">{{ chatStore.unreadTotal }} 条未读</span>
        </div>
          <span
            class="connection-status"
            :class="`is-${userStore.socketStatus}`"
            role="status"
            aria-live="polite"
          >
            <span class="connection-dot" aria-hidden="true"></span>
            {{ connectionLabel }}
          </span>
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

      <div
        v-if="userStore.socketStatus === 'error' || userStore.socketStatus === 'closed'"
        class="connection-banner"
        role="alert"
      >
        <span>{{ connectionLabel }}，新消息可能会延迟到达</span>
        <button type="button" @click="reconnectSocket">重新连接</button>
      </div>

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

      <div v-if="chatStore.offlineSyncError" class="sync-banner" role="status">
        <span>{{ chatStore.offlineSyncError }}</span>
        <button type="button" @click="retryOfflineSync">重试</button>
      </div>

      <ContactList
        :contacts="chatStore.filteredContacts"
        :selected-contact="chatStore.currentContact"
        :loading="chatStore.loadingContacts"
        :error="chatStore.contactsError"
        :has-filter="hasContactFilter"
        @select-contact="selectContact"
        @retry="reloadContacts"
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
              @click="clearSelectedContact"
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

        <div
          ref="messageListRef"
          class="message-list"
          v-loading="chatStore.loadingMessages"
          role="log"
          aria-live="polite"
          aria-relevant="additions text"
          :aria-label="`与${chatStore.currentContact.contactName}的消息`"
          @scroll="handleMessageScroll"
        >
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
          <div v-if="chatStore.messageError" class="message-error" role="alert">
            <span>{{ chatStore.messageError }}</span>
            <button type="button" @click="retryMessages">重试</button>
          </div>
          <MessageBubble
            v-for="message in chatStore.messages"
            :key="message.localId || message.messageId"
            :message="message"
            @retry-message="chatStore.retryMessage"
            @download-file="downloadFile"
          />

          <div
            v-if="!chatStore.loadingMessages && !chatStore.messageError && chatStore.messages.length === 0"
            class="empty-chat"
          >
            <div class="empty-chip">
              <ChatLineRound />
            </div>
            <strong>还没有消息</strong>
            <span>发一条消息，和对方打个招呼吧。</span>
          </div>
        </div>

        <button
          v-if="showNewMessages"
          class="new-message-pill"
          type="button"
          aria-label="跳转到最新消息"
          @click="scrollToBottom('smooth')"
        >
          <ArrowDown aria-hidden="true" />
          新消息
        </button>

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
    <FriendApplyList v-model="applyVisible" @apply-processed="handleApplyProcessed" />
    <ContactInfoDialog
      v-model="contactInfoVisible"
      :contact="chatStore.currentContact"
      @changed="afterContactChanged"
      @edit-group="editGroup"
    />
    <GroupEditorDialog v-model="groupEditorVisible" :group="editingGroup" @saved="afterGroupSaved" />
    <UserProfileDialog v-model="profileVisible" @profile-updated="handleProfileUpdated" />
    <el-dialog
      v-model="mediaPreviewVisible"
      :title="mediaPreviewName"
      width="min(720px, calc(100vw - 24px))"
      destroy-on-close
      class="media-preview-dialog"
      @closed="releaseMediaPreview"
    >
      <img
        v-if="mediaPreviewKind === 'image'"
        :src="mediaPreviewUrl"
        :alt="mediaPreviewName"
        class="media-preview-image"
      />
      <video v-else :src="mediaPreviewUrl" class="media-preview-video" controls playsinline />
      <template #footer>
        <el-button @click="mediaPreviewVisible = false">关闭</el-button>
        <el-button type="primary" @click="downloadPreview">下载</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ArrowDown,
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
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()

const DRAFT_KEY_PREFIX = 'letchat_drafts:'
const drafts = ref<Record<string, string>>({})
const messageText = computed({
  get: () => {
    const contactId = chatStore.currentContact?.contactId
    return contactId ? drafts.value[contactId] || '' : ''
  },
  set: (value: string) => {
    const contactId = chatStore.currentContact?.contactId
    if (contactId) {
      drafts.value[contactId] = value
      persistDrafts()
    }
  },
})
let contactsReady = false
const searchVisible = ref(false)
const applyVisible = ref(false)
const contactInfoVisible = ref(false)
const profileVisible = ref(false)
const groupEditorVisible = ref(false)
const editingGroup = ref<GroupInfo | null>(null)
const messageListRef = ref<HTMLElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const showNewMessages = ref(false)
const preservingHistoryScroll = ref(false)
const mediaPreviewVisible = ref(false)
const mediaPreviewUrl = ref('')
const mediaPreviewName = ref('')
const mediaPreviewKind = ref<'image' | 'video' | null>(null)

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
const hasContactFilter = computed(() => Boolean(chatStore.keyword.trim()) || chatStore.activeFilter !== 'all')

const connectionLabel = computed(() => {
  if (userStore.socketStatus === 'open') return '实时在线'
  if (userStore.socketStatus === 'connecting') return '连接中…'
  if (userStore.socketStatus === 'error') return '连接异常'
  if (userStore.socketStatus === 'closed') return '已断开'
  return '等待连接'
})

function scrollToBottom(behavior: ScrollBehavior = 'auto') {
  nextTick(() => {
    const node = messageListRef.value
    if (node) node.scrollTo({ top: node.scrollHeight, behavior })
    showNewMessages.value = false
  })
}

function draftStorageKey() {
  const userId = userStore.userInfo?.userId
  return userId ? `${DRAFT_KEY_PREFIX}${userId}` : ''
}

function loadDrafts() {
  drafts.value = {}
  const key = draftStorageKey()
  if (!key) return
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || '{}') as Record<string, unknown>
    drafts.value = Object.fromEntries(
      Object.entries(parsed).filter(([, value]) => typeof value === 'string'),
    ) as Record<string, string>
  } catch {
    localStorage.removeItem(key)
  }
}

function persistDrafts() {
  const key = draftStorageKey()
  if (!key) return
  const stored = Object.fromEntries(
    Object.entries(drafts.value).filter(([, value]) => value.length > 0),
  )
  if (Object.keys(stored).length) localStorage.setItem(key, JSON.stringify(stored))
  else localStorage.removeItem(key)
}

function routeContactId() {
  return typeof route.query.contact === 'string' ? route.query.contact : ''
}

function updateContactQuery(contactId: string | null) {
  const currentContactQuery = route.query.contact
  const nextContactQuery = contactId || undefined
  if (currentContactQuery === nextContactQuery) return
  const query = { ...route.query }
  if (nextContactQuery) query.contact = nextContactQuery
  else delete query.contact
  void router.replace({ query })
}

function isNearBottom(node: HTMLElement) {
  return node.scrollHeight - node.scrollTop - node.clientHeight < 96
}

function handleMessageScroll() {
  const node = messageListRef.value
  if (node && isNearBottom(node)) showNewMessages.value = false
}

function reconnectSocket() {
  userStore.disconnectSocket()
  userStore.connectSocket()
}

async function selectContact(contact: ContactInfo) {
  updateContactQuery(contact.contactId)
  if (chatStore.currentContact?.contactId === contact.contactId && !chatStore.messageError) return
  showNewMessages.value = false
  await chatStore.setCurrentContact(contact)
  scrollToBottom()
}

function clearSelectedContact() {
  chatStore.clearCurrentContact()
  updateContactQuery(null)
}

async function restoreContactFromRoute() {
  if (!contactsReady) return
  const contactId = routeContactId()
  if (!contactId) {
    if (chatStore.currentContact) chatStore.clearCurrentContact()
    return
  }
  const contact = chatStore.contacts.find((item) => item.contactId === contactId)
  if (contact) {
    await selectContact(contact)
    return
  }
  clearSelectedContact()
}

async function loadOlderMessages() {
  const node = messageListRef.value
  const previousHeight = node?.scrollHeight || 0
  const previousTop = node?.scrollTop || 0
  preservingHistoryScroll.value = true
  try {
    await chatStore.loadOlderMessages()
    await nextTick()
    if (node) node.scrollTop = previousTop + node.scrollHeight - previousHeight
  } finally {
    preservingHistoryScroll.value = false
  }
}

async function retryMessages() {
  const hadMessages = chatStore.messages.length > 0
  await chatStore.retryCurrentMessages()
  if (!hadMessages) scrollToBottom()
}

function retryOfflineSync() {
  void chatStore.retryOfflineSync()
}

async function sendText() {
  const contactId = chatStore.currentContact?.contactId
  const content = messageText.value
  if (!content.trim()) return
  messageText.value = ''
  try {
    await chatStore.sendMessage(content)
    scrollToBottom()
  } catch {
    if (chatStore.currentContact?.contactId === contactId && !messageText.value.trim()) {
      messageText.value = content
    }
    ElMessage.error('消息发送失败，请检查网络后重试')
  }
}

function handleEnter(event: KeyboardEvent) {
  if (event.shiftKey || event.isComposing || event.keyCode === 229) return
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

async function handleApplyProcessed() {
  chatStore.markApplyProcessed()
  await reloadContacts()
}

async function afterContactChanged(options: { removeCurrent?: boolean } = {}) {
  if (options.removeCurrent) chatStore.clearCurrentContact()
  await reloadContacts()
}

function editGroup(group: GroupInfo | null) {
  contactInfoVisible.value = false
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

async function handleProfileUpdated() {
  try {
    await userStore.refreshUserInfo()
  } catch {
    if (userStore.isLoggedIn) ElMessage.warning('资料已保存，但最新信息同步失败，请稍后刷新页面')
  }
}

function isImageFile(fileName?: string) {
  return /\.(png|jpe?g|gif|webp|bmp)$/i.test(fileName || '')
}

function isVideoFile(fileName?: string) {
  return /\.(mp4|webm|mov|m4v|avi)$/i.test(fileName || '')
}

function triggerDownload(url: string, fileName: string, revoke = true) {
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  if (revoke) window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function releaseMediaPreview() {
  if (mediaPreviewUrl.value) URL.revokeObjectURL(mediaPreviewUrl.value)
  mediaPreviewUrl.value = ''
  mediaPreviewName.value = ''
  mediaPreviewKind.value = null
}

function downloadPreview() {
  if (!mediaPreviewUrl.value) return
  triggerDownload(mediaPreviewUrl.value, mediaPreviewName.value || 'letchat-media', false)
}

async function downloadFile(message: ChatMessage) {
  if (!message.messageId || message.messageId.startsWith('local-')) {
    ElMessage.warning('文件还未上传完成，请稍后再试')
    return
  }
  try {
    const blob = await chatApi.downloadFile(message.messageId)
    const url = URL.createObjectURL(blob)
    const fileName = message.fileName || 'letchat-media'
    if (isImageFile(fileName) || isVideoFile(fileName)) {
      releaseMediaPreview()
      mediaPreviewUrl.value = url
      mediaPreviewName.value = fileName
      mediaPreviewKind.value = isImageFile(fileName) ? 'image' : 'video'
      mediaPreviewVisible.value = true
      return
    }
    triggerDownload(url, fileName)
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
  if (router.currentRoute.value.path !== '/login') await router.replace('/login')
}

onMounted(async () => {
  await userStore.refreshUserInfo().catch(() => null)
  if (!userStore.isLoggedIn) {
    await router.replace('/login')
    return
  }
  loadDrafts()
  chatStore.attachSocket()
  await chatStore.loadContacts()
  contactsReady = true
  await restoreContactFromRoute()
})

onBeforeUnmount(() => {
  contactsReady = false
  chatStore.detachSocket()
  userStore.disconnectSocket()
  releaseMediaPreview()
})

watch(
  () => userStore.userInfo?.userId,
  () => loadDrafts(),
)

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn || router.currentRoute.value.path === '/login') return
    chatStore.clearCurrentContact()
    void router.replace('/login')
  },
)

watch(
  () => route.query.contact,
  () => {
    void restoreContactFromRoute()
  },
)

watch(
  () => [chatStore.currentContact?.contactId || '', chatStore.messages.length] as const,
  ([contactId, messageCount], previous) => {
    const [previousContactId, previousMessageCount] = previous || ['', 0]
    if (contactId !== previousContactId) {
      showNewMessages.value = false
      if (contactsReady && !contactId && routeContactId()) updateContactQuery(null)
      scrollToBottom()
      return
    }
    if (
      preservingHistoryScroll.value ||
      chatStore.loadingOlderMessages ||
      messageCount <= previousMessageCount
    ) {
      return
    }
    const node = messageListRef.value
    if (!node || isNearBottom(node)) {
      scrollToBottom()
    } else {
      showNewMessages.value = true
    }
  },
  { flush: 'post' },
)
</script>

<style scoped>
.chat-shell {
  display: grid;
  grid-template-columns: 326px minmax(0, 1fr);
  gap: 12px;
  min-height: 100vh;
  height: 100dvh;
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
  overscroll-behavior: contain;
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

.connection-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--lc-muted);
  font-size: 11px;
  white-space: nowrap;
}

.connection-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--lc-soft);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--lc-soft) 13%, transparent);
}

.connection-status.is-open {
  color: var(--lc-accent);
}

.connection-status.is-open .connection-dot {
  background: var(--lc-accent);
  box-shadow: 0 0 0 3px var(--lc-accent-soft);
}

.connection-status.is-error,
.connection-status.is-closed {
  color: var(--lc-danger);
}

.connection-status.is-error .connection-dot,
.connection-status.is-closed .connection-dot {
  background: var(--lc-danger);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--lc-danger) 13%, transparent);
}

.connection-banner,
.sync-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
  margin: 10px 12px 0;
  padding: 8px 10px;
  border: 1px solid color-mix(in srgb, var(--lc-danger) 32%, var(--lc-line));
  border-radius: 10px;
  color: var(--lc-danger);
  background: color-mix(in srgb, var(--lc-danger) 8%, var(--lc-surface));
  font-size: 11px;
}

.sync-banner {
  border-color: color-mix(in srgb, var(--lc-brass) 32%, var(--lc-line));
  color: var(--lc-brass);
  background: var(--lc-brass-soft);
}

.connection-banner button,
.sync-banner button,
.message-error button {
  flex: 0 0 auto;
  padding: 4px 7px;
  border: 1px solid currentColor;
  border-radius: 7px;
  color: inherit;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 11px;
  font-weight: 600;
}

.connection-banner button:hover,
.sync-banner button:hover,
.message-error button:hover {
  background: color-mix(in srgb, currentColor 10%, transparent);
}

.connection-banner button:focus-visible,
.sync-banner button:focus-visible,
.message-error button:focus-visible,
.new-message-pill:focus-visible {
  outline: 2px solid var(--lc-focus);
  outline-offset: 2px;
}

.message-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin: 6px auto 12px;
  color: var(--lc-danger);
  font-size: 12px;
}

.new-message-pill {
  position: absolute;
  right: 22px;
  bottom: 86px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 11px;
  border: 1px solid var(--lc-accent);
  border-radius: 999px;
  color: var(--lc-on-accent);
  background: var(--lc-accent);
  box-shadow: var(--lc-shadow-soft);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
}

.new-message-pill:hover {
  background: var(--lc-accent-dark);
  transform: translateY(-1px);
}

.new-message-pill svg {
  width: 14px;
  height: 14px;
}

.media-preview-dialog :deep(.el-dialog__body) {
  display: grid;
  place-items: center;
  min-height: 180px;
  padding: 12px 20px 20px;
  background: var(--lc-surface-subtle);
}

.media-preview-image,
.media-preview-video {
  display: block;
  max-width: 100%;
  max-height: min(62vh, 620px);
  border-radius: 10px;
  object-fit: contain;
}

@media (max-width: 760px) {
  .connection-status {
    font-size: 10px;
  }

  .connection-banner,
  .sync-banner {
    margin-right: 10px;
    margin-left: 10px;
  }

  .new-message-pill {
    right: 12px;
    bottom: 98px;
  }
}
</style>
