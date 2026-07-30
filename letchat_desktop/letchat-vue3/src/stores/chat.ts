import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { chatApi } from '@/api/chat'
import { contactApi } from '@/api/contact'
import { useUserStore } from '@/stores/user'
import type {
  BackendChatMessage,
  BackendContact,
  ChatMessage,
  ContactInfo,
  ContactKind,
  WsInitData,
  WsMessage,
} from '@/types/api'

const TEXT_MESSAGE = 2
const MEDIA_MESSAGE = 5
const INIT_MESSAGE = 0
const CURSOR_PREFIX = 'letchat_cursor'

function toContactKind(type: unknown): ContactKind {
  if (type === 'G' || type === 'GROUP' || type === 1 || type === '1') return 'G'
  return 'U'
}

function contactTypeCode(kind: ContactKind) {
  return kind === 'G' ? 1 : 0
}

function normalizeTime(time?: number | string) {
  if (typeof time === 'number') return time
  if (typeof time === 'string') {
    const parsed = new Date(time).getTime()
    return Number.isNaN(parsed) ? Date.now() : parsed
  }
  return Date.now()
}

function safeText(value?: string) {
  return value?.trim() || ''
}

export const useChatStore = defineStore('chat', () => {
  const userStore = useUserStore()
  const contacts = ref<ContactInfo[]>([])
  const currentContact = ref<ContactInfo | null>(null)
  const messages = ref<ChatMessage[]>([])
  const loadingContacts = ref(false)
  const loadingMessages = ref(false)
  const loadingOlderMessages = ref(false)
  const hasMoreMessages = ref(false)
  const currentMessagePage = ref(1)
  const sending = ref(false)
  const pendingApplyCount = ref(0)
  const keyword = ref('')
  const activeFilter = ref<'all' | 'U' | 'G'>('all')

  let removeSocketListener: (() => void) | null = null

  const filteredContacts = computed(() => {
    const text = keyword.value.trim().toLowerCase()
    return contacts.value.filter((contact) => {
      const matchFilter = activeFilter.value === 'all' || contact.contactType === activeFilter.value
      const matchText =
        !text ||
        contact.contactName.toLowerCase().includes(text) ||
        contact.contactId.toLowerCase().includes(text)
      return matchFilter && matchText
    })
  })

  const unreadTotal = computed(() => contacts.value.reduce((sum, item) => sum + (item.unreadCount || 0), 0))

  function avatarSeed(name: string) {
    return safeText(name).slice(0, 2).toUpperCase() || 'LC'
  }

  function normalizeContact(raw: BackendContact): ContactInfo {
    const kind = toContactKind(raw.contactType)
    return {
      userId: raw.userId,
      contactId: raw.contactId,
      contactName: raw.contactName || raw.contactId,
      contactType: kind,
      contactTypeCode: contactTypeCode(kind),
      status: raw.status,
      sex: raw.sex,
      createTime: raw.createTime,
      lastUpdateTime: raw.lastUpdateTime,
      lastMessage: raw.lastMessage || '',
      lastReceiveTime: raw.lastReceiveTime,
      unreadCount: 0,
      memberCount: raw.memberCount,
    }
  }

  function resolveConversationId(raw: BackendChatMessage) {
    const selfId = userStore.userInfo?.userId
    if (raw.contactType === 1) return raw.contactId || ''
    if (raw.sendUserId && raw.sendUserId !== selfId) return raw.sendUserId
    return raw.contactId || raw.sendUserId || ''
  }

  function normalizeMessage(raw: BackendChatMessage, status: ChatMessage['sendStatus'] = 'received'): ChatMessage {
    const contactId = resolveConversationId(raw)
    return {
      messageId: String(raw.messageId || `ws-${raw.sendTime || Date.now()}-${Math.random().toString(16).slice(2)}`),
      clientMessageId: raw.clientMessageId,
      sequenceNo: raw.sequenceNo,
      sessionId: raw.sessionId,
      contactId,
      rawContactId: raw.contactId,
      contactName: raw.contactName,
      contactType: raw.contactType,
      senderId: raw.sendUserId || '',
      senderName: raw.sendUserNickName || raw.sendUserId || '未知用户',
      messageContent: raw.messageContent || '',
      messageType: raw.messageType,
      sendTime: normalizeTime(raw.sendTime),
      sendStatus: status,
      status: raw.status,
      fileSize: raw.fileSize,
      fileName: raw.fileName,
      fileType: raw.fileType,
      extendData: raw.extendData,
    }
  }

  function sortContacts() {
    contacts.value.sort((a, b) => normalizeTime(b.lastReceiveTime) - normalizeTime(a.lastReceiveTime))
  }

  function upsertContact(next: ContactInfo) {
    const index = contacts.value.findIndex((item) => item.contactId === next.contactId)
    if (index >= 0) {
      contacts.value[index] = { ...contacts.value[index], ...next }
    } else {
      contacts.value.push(next)
    }
    sortContacts()
  }

  function updateContactSummary(message: ChatMessage, countUnread: boolean) {
    const index = contacts.value.findIndex((item) => item.contactId === message.contactId)
    if (index < 0) return

    const contact = contacts.value[index]
    contact.lastMessage = message.messageType === MEDIA_MESSAGE ? message.fileName || '[媒体文件]' : message.messageContent
    contact.lastReceiveTime = message.sendTime
    if (countUnread && currentContact.value?.contactId !== contact.contactId) {
      contact.unreadCount = (contact.unreadCount || 0) + 1
    }
    sortContacts()
  }

  function upsertMessage(message: ChatMessage) {
    const currentId = currentContact.value?.contactId
    if (!currentId || message.contactId !== currentId) {
      updateContactSummary(message, true)
      return
    }

    const index = messages.value.findIndex((item) => {
      if (message.localId && item.localId === message.localId) return true
      if (message.clientMessageId && item.clientMessageId === message.clientMessageId) return true
      return item.messageId === message.messageId
    })

    if (index >= 0) {
      messages.value[index] = { ...messages.value[index], ...message }
    } else {
      messages.value.push(message)
    }
    messages.value.sort((a, b) => {
      if (a.sequenceNo != null && b.sequenceNo != null) return a.sequenceNo - b.sequenceNo
      return a.sendTime - b.sendTime
    })
    updateContactSummary(message, false)
  }

  async function loadContacts() {
    loadingContacts.value = true
    try {
      const [friends, groups] = await Promise.all([contactApi.loadContact('U'), contactApi.loadContact('G')])
      const previousContacts = new Map(contacts.value.map((item) => [item.contactId, item]))
      contacts.value = [...(friends.data || []), ...(groups.data || [])].map((raw) => {
        const contact = normalizeContact(raw)
        const previous = previousContacts.get(contact.contactId)
        contact.lastMessage = contact.lastMessage || previous?.lastMessage || ''
        contact.lastReceiveTime = contact.lastReceiveTime || previous?.lastReceiveTime
        contact.unreadCount = previous?.unreadCount || 0
        return contact
      })
      sortContacts()
      void syncOfflineMessages()
    } finally {
      loadingContacts.value = false
    }
  }

  async function loadMessages(contactId: string, pageNo = 1) {
    loadingMessages.value = true
    try {
      const response = await chatApi.loadChatMessage(contactId, pageNo)
      const list = (response.data?.list || []).map((item) => normalizeMessage(item, 'received'))
      list.sort((a, b) => a.sendTime - b.sendTime)
      const merged = pageNo === 1 ? list : [...list, ...messages.value]
      messages.value = Array.from(new Map(merged.map((item) => [item.messageId, item])).values())
      currentMessagePage.value = pageNo
      hasMoreMessages.value = pageNo < (response.data?.pageTotal || pageNo)
      if (pageNo === 1 && list.length > 0) {
        updateContactSummary(list[list.length - 1], false)
      }
      return list
    } finally {
      loadingMessages.value = false
    }
  }

  async function loadOlderMessages() {
    if (!currentContact.value || loadingMessages.value || loadingOlderMessages.value || !hasMoreMessages.value) return []
    loadingOlderMessages.value = true
    try {
      return await loadMessages(currentContact.value.contactId, currentMessagePage.value + 1)
    } finally {
      loadingOlderMessages.value = false
    }
  }

  async function setCurrentContact(contact: ContactInfo) {
    currentContact.value = contact
    contact.unreadCount = 0
    await loadMessages(contact.contactId)
    const latestSequence = messages.value.reduce((max, message) => Math.max(max, message.sequenceNo || 0), 0)
    if (latestSequence > 0) {
      saveCursor(contact.contactId, latestSequence)
      void chatApi.acknowledgeRead(contact.contactId, latestSequence)
    }
  }

  function clearCurrentContact() {
    currentContact.value = null
    messages.value = []
    currentMessagePage.value = 1
    hasMoreMessages.value = false
  }

  async function sendMessage(content: string, retryClientMessageId?: string) {
    if (!currentContact.value) return null
    const text = content.trim()
    if (!text) return null

    sending.value = true
    const localId = retryClientMessageId || `local-${Date.now()}-${Math.random().toString(16).slice(2)}`
    const optimistic: ChatMessage = {
      messageId: localId,
      localId,
      contactId: currentContact.value.contactId,
      contactType: currentContact.value.contactTypeCode,
      senderId: userStore.userInfo?.userId || '',
      senderName: userStore.displayName,
      messageContent: text,
      messageType: TEXT_MESSAGE,
      sendTime: Date.now(),
      sendStatus: 'sending',
    }
    upsertMessage(optimistic)

    try {
      const response = await chatApi.sendMessage({
        contactId: currentContact.value.contactId,
        messageContent: text,
        messageType: TEXT_MESSAGE,
        clientMessageId: localId,
      })
      const saved = normalizeMessage(response.data, 'sent')
      saved.localId = localId
      upsertMessage(saved)
      return saved
    } catch (error) {
      upsertMessage({ ...optimistic, sendStatus: 'failed' })
      throw error
    } finally {
      sending.value = false
    }
  }

  async function sendFileMessage(file: File) {
    if (!currentContact.value) return null
    sending.value = true
    const localId = `local-file-${Date.now()}-${Math.random().toString(16).slice(2)}`
    const optimistic: ChatMessage = {
      messageId: localId,
      localId,
      contactId: currentContact.value.contactId,
      contactType: currentContact.value.contactTypeCode,
      senderId: userStore.userInfo?.userId || '',
      senderName: userStore.displayName,
      messageContent: file.name,
      messageType: MEDIA_MESSAGE,
      sendTime: Date.now(),
      sendStatus: 'sending',
      fileName: file.name,
      fileSize: file.size,
      fileType: MEDIA_MESSAGE,
    }
    upsertMessage(optimistic)

    try {
      const response = await chatApi.sendMessage({
        contactId: currentContact.value.contactId,
        messageContent: file.name,
        messageType: MEDIA_MESSAGE,
        clientMessageId: localId,
        fileSize: file.size,
        fileName: file.name,
        fileType: MEDIA_MESSAGE,
      })
      const messageId = String(response.data.messageId)
      await chatApi.uploadFile(messageId, file)
      const saved = normalizeMessage(response.data, 'sent')
      saved.localId = localId
      upsertMessage(saved)
      return saved
    } catch (error) {
      upsertMessage({ ...optimistic, sendStatus: 'failed' })
      throw error
    } finally {
      sending.value = false
    }
  }

  function mergeInitData(data: WsInitData) {
    pendingApplyCount.value = data.applyCount || 0
    data.chatSessionUserList?.forEach((session) => upsertContact(normalizeContact(session)))
    data.chatMessagesList?.forEach((raw) => {
      const message = normalizeMessage(raw, 'received')
      if (currentContact.value?.contactId === message.contactId) {
        upsertMessage(message)
      } else {
        updateContactSummary(message, true)
      }
    })
  }

  function handleSocketMessage(payload: WsMessage) {
    if (!payload || typeof payload.messageType !== 'number') return
    if (payload.messageType === INIT_MESSAGE) {
      mergeInitData((payload.extendData || {}) as WsInitData)
      return
    }

    const message = normalizeMessage(payload, payload.sendUserId === userStore.userInfo?.userId ? 'sent' : 'received')
    upsertMessage(message)
    acknowledgeMessage(message)
  }

  function cursorKey(contactId: string) {
    return `${CURSOR_PREFIX}:${userStore.userInfo?.userId || 'anonymous'}:${contactId}`
  }

  function readCursor(contactId: string) {
    const value = Number(localStorage.getItem(cursorKey(contactId)) || 0)
    return Number.isFinite(value) && value > 0 ? value : 0
  }

  function saveCursor(contactId: string, sequenceNo: number) {
    const next = Math.max(readCursor(contactId), sequenceNo)
    localStorage.setItem(cursorKey(contactId), String(next))
  }

  function acknowledgeMessage(message: ChatMessage) {
    if (!message.sequenceNo || !message.contactId) return
    saveCursor(message.contactId, message.sequenceNo)
    void chatApi.acknowledgeDelivered(message.contactId, message.sequenceNo)
    if (currentContact.value?.contactId === message.contactId) {
      void chatApi.acknowledgeRead(message.contactId, message.sequenceNo)
    }
  }

  async function syncOfflineMessages() {
    await Promise.allSettled(
      contacts.value.map(async (contact) => {
        const response = await chatApi.syncMessages(contact.contactId, readCursor(contact.contactId))
        for (const raw of response.data || []) {
          const message = normalizeMessage(
            raw,
            raw.sendUserId === userStore.userInfo?.userId ? 'sent' : 'received',
          )
          upsertMessage(message)
          acknowledgeMessage(message)
        }
      }),
    )
  }

  function attachSocket() {
    if (removeSocketListener) return
    removeSocketListener = userStore.addSocketListener(handleSocketMessage)
    userStore.connectSocket()
  }

  function detachSocket() {
    removeSocketListener?.()
    removeSocketListener = null
  }

  function retryMessage(message: ChatMessage) {
    if (message.messageType === TEXT_MESSAGE) {
      return sendMessage(message.messageContent, message.clientMessageId || message.localId)
    }
    ElMessage.warning('媒体消息请重新选择文件后发送')
    return Promise.resolve(null)
  }

  return {
    contacts,
    currentContact,
    messages,
    loadingContacts,
    loadingMessages,
    loadingOlderMessages,
    hasMoreMessages,
    sending,
    pendingApplyCount,
    keyword,
    activeFilter,
    filteredContacts,
    unreadTotal,
    avatarSeed,
    loadContacts,
    loadMessages,
    loadOlderMessages,
    setCurrentContact,
    clearCurrentContact,
    sendMessage,
    sendFileMessage,
    retryMessage,
    attachSocket,
    detachSocket,
  }
})
