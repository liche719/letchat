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
const CONTACT_APPLY_MESSAGE = 4
const FILE_UPLOAD_MESSAGE = 6
const ADD_FRIEND_MESSAGE = 1
const CONTACT_NAME_UPDATE_MESSAGE = 10
const ADD_FRIEND_SELF_MESSAGE = 13
const CONTACT_REMOVED_MESSAGE = 14
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
    const numeric = Number(time)
    if (Number.isFinite(numeric)) return numeric
    const parsed = new Date(time).getTime()
    return Number.isNaN(parsed) ? Date.now() : parsed
  }
  return Date.now()
}

function isActiveContact(raw: BackendContact) {
  return raw.status == null || Number(raw.status) === 1
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
  const contactsError = ref('')
  const loadingMessages = ref(false)
  const messageError = ref('')
  const loadingOlderMessages = ref(false)
  const hasMoreMessages = ref(false)
  const currentMessagePage = ref(1)
  const sending = ref(false)
  let pendingSendCount = 0
  const offlineSyncError = ref('')
  const pendingApplyCount = ref(0)
  const keyword = ref('')
  const activeFilter = ref<'all' | 'U' | 'G'>('all')

  let removeSocketListener: (() => void) | null = null
  const summarizedMessageKeys = new Set<string>()
  let stateOwnerId: string | null | undefined

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
    const kind = toContactKind(raw.contactType ?? raw.contactId?.slice(0, 1))
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
    const contactKind = toContactKind(raw.contactType ?? raw.contactId?.slice(0, 1))
    if (contactKind === 'G') return raw.contactId || ''
    if (raw.sendUserId && raw.sendUserId !== selfId) return raw.sendUserId
    return raw.contactId || raw.sendUserId || ''
  }

  function normalizeMessage(raw: BackendChatMessage, status: ChatMessage['sendStatus'] = 'received'): ChatMessage {
    const contactId = resolveConversationId(raw)
    const contactType = contactTypeCode(toContactKind(raw.contactType ?? raw.contactId?.slice(0, 1)))
    return {
      messageId: String(raw.messageId || `ws-${raw.sendTime || Date.now()}-${Math.random().toString(16).slice(2)}`),
      clientMessageId: raw.clientMessageId,
      sequenceNo: raw.sequenceNo,
      sessionId: raw.sessionId,
      contactId,
      rawContactId: raw.contactId,
      contactName: raw.contactName,
      contactType,
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
      memberCount: raw.memberCount,
      extendData: raw.extendData,
    }
  }

  function sortContacts() {
    contacts.value.sort((a, b) => {
      const aTime = a.lastReceiveTime == null ? 0 : normalizeTime(a.lastReceiveTime)
      const bTime = b.lastReceiveTime == null ? 0 : normalizeTime(b.lastReceiveTime)
      return bTime - aTime
    })
  }

  function findMessageIndex(message: ChatMessage) {
    return messages.value.findIndex((item) => {
      if (item.contactId !== message.contactId) return false
      if (message.localId && item.localId === message.localId) return true
      if (message.clientMessageId && item.clientMessageId === message.clientMessageId) return true
      return item.messageId === message.messageId
    })
  }

  function messageKey(message: ChatMessage) {
    return `${message.contactId}:${message.clientMessageId || message.messageId}`
  }

  function mergeMessages(existing: ChatMessage[], incoming: ChatMessage[]) {
    const merged = [...existing]
    for (const message of incoming) {
      const index = merged.findIndex((item) => {
        if (message.localId && item.localId === message.localId) return true
        if (message.clientMessageId && item.clientMessageId === message.clientMessageId) return true
        return item.messageId === message.messageId
      })
      if (index >= 0) {
        merged[index] = { ...merged[index], ...message, localId: message.localId || merged[index].localId }
      } else {
        merged.push(message)
      }
    }
    return merged.sort((a, b) => {
      if (a.sequenceNo != null && b.sequenceNo != null) return a.sequenceNo - b.sequenceNo
      return a.sendTime - b.sendTime
    })
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
    if (index < 0) return false

    const contact = contacts.value[index]
    const messageTime = normalizeTime(message.sendTime)
    const contactTime = contact.lastReceiveTime == null ? 0 : normalizeTime(contact.lastReceiveTime)
    const preview = message.messageType === MEDIA_MESSAGE ? message.fileName || '[媒体文件]' : message.messageContent
    if (preview && (messageTime >= contactTime || !contact.lastMessage)) {
      contact.lastMessage = preview
      contact.lastReceiveTime = message.sendTime
    }
    if (
      countUnread &&
      message.senderId !== userStore.userInfo?.userId &&
      currentContact.value?.contactId !== contact.contactId
    ) {
      contact.unreadCount = (contact.unreadCount || 0) + 1
    }
    sortContacts()
    return true
  }

  function upsertMessage(message: ChatMessage, countUnread = true, updateSummary = true) {
    const currentId = currentContact.value?.contactId
    const index = findMessageIndex(message)
    const key = messageKey(message)
    if (!currentId || message.contactId !== currentId) {
      if (message.contactType === 1 && message.memberCount != null) {
        const contact = contacts.value.find((item) => item.contactId === message.contactId)
        if (contact) contact.memberCount = message.memberCount
      }
      if (updateSummary && !summarizedMessageKeys.has(key)) {
        if (updateContactSummary(message, countUnread)) summarizedMessageKeys.add(key)
      }
      return
    }

    if (index >= 0) {
      messages.value[index] = { ...messages.value[index], ...message, localId: message.localId || messages.value[index].localId }
    } else {
      messages.value.push(message)
    }
    messages.value = mergeMessages([], messages.value)
    if (updateSummary && updateContactSummary(message, false)) summarizedMessageKeys.add(key)
    if (message.contactType === 1 && message.memberCount != null) {
      const contact = contacts.value.find((item) => item.contactId === message.contactId)
      if (contact) contact.memberCount = message.memberCount
    }
  }

  let contactsRequestVersion = 0
  let conversationVersion = 0
  let messageRequestVersion = 0
  let messageErrorPage = 1

  function resetStateForUser() {
    const nextOwnerId = userStore.userInfo?.userId || null
    if (stateOwnerId === nextOwnerId) return
    stateOwnerId = nextOwnerId
    contactsRequestVersion += 1
    conversationVersion += 1
    messageRequestVersion += 1
    contacts.value = []
    currentContact.value = null
    messages.value = []
    currentMessagePage.value = 1
    hasMoreMessages.value = false
    loadingContacts.value = false
    contactsError.value = ''
    loadingMessages.value = false
    messageError.value = ''
    loadingOlderMessages.value = false
    pendingApplyCount.value = 0
    sending.value = false
    pendingSendCount = 0
    offlineSyncError.value = ''
    messageErrorPage = 1
    summarizedMessageKeys.clear()
  }

  async function loadContacts() {
    resetStateForUser()
    const requestVersion = ++contactsRequestVersion
    loadingContacts.value = true
    contactsError.value = ''
    offlineSyncError.value = ''
    try {
      const [friends, groups] = await Promise.all([contactApi.loadContact('U'), contactApi.loadContact('G')])
      if (requestVersion !== contactsRequestVersion) return
      const previousContacts = new Map(contacts.value.map((item) => [item.contactId, item]))
      contacts.value = [...(friends.data || []), ...(groups.data || [])]
        .filter(isActiveContact)
        .map((raw) => {
          const contact = normalizeContact(raw)
          const previous = previousContacts.get(contact.contactId)
          contact.lastMessage = contact.lastMessage || previous?.lastMessage || ''
          contact.lastReceiveTime = contact.lastReceiveTime || previous?.lastReceiveTime
          contact.unreadCount = previous?.unreadCount || 0
          return contact
        })
      sortContacts()
      const currentId = currentContact.value?.contactId
      if (currentId) {
        const refreshedContact = contacts.value.find((item) => item.contactId === currentId)
        if (refreshedContact) currentContact.value = refreshedContact
        else clearCurrentContact()
      }
      try {
        await syncOfflineMessages(requestVersion)
      } catch {
        if (requestVersion === contactsRequestVersion) {
          offlineSyncError.value = '部分离线消息同步失败，请重试'
        }
      }
    } catch {
      if (requestVersion === contactsRequestVersion) {
        contactsError.value = '联系人加载失败，请重试'
      }
    } finally {
      if (requestVersion === contactsRequestVersion) loadingContacts.value = false
    }
  }

  async function loadMessages(contactId: string, pageNo = 1) {
    const requestVersion = ++messageRequestVersion
    loadingMessages.value = true
    if (currentContact.value?.contactId === contactId) {
      messageError.value = ''
    }
    try {
      const response = await chatApi.loadChatMessage(contactId, pageNo)
      const list = (response.data?.list || []).map((item) =>
        normalizeMessage(item, item.sendUserId === userStore.userInfo?.userId ? 'sent' : 'received'),
      )
      list.sort((a, b) => a.sendTime - b.sendTime)
      if (requestVersion !== messageRequestVersion || currentContact.value?.contactId !== contactId) return list
      messages.value = mergeMessages(pageNo === 1 ? messages.value : messages.value, list)
      list.forEach((message) => summarizedMessageKeys.add(messageKey(message)))
      currentMessagePage.value = pageNo
      hasMoreMessages.value = pageNo < (response.data?.pageTotal || pageNo)
      if (pageNo === 1 && list.length > 0) {
        updateContactSummary(list[list.length - 1], false)
      }
      return list
    } catch {
      if (requestVersion === messageRequestVersion && currentContact.value?.contactId === contactId) {
        messageError.value = pageNo === 1 ? '消息加载失败，请重试' : '更早消息加载失败，请重试'
        messageErrorPage = pageNo
      }
      return []
    } finally {
      if (requestVersion === messageRequestVersion) loadingMessages.value = false
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
    conversationVersion += 1
    const selectionVersion = conversationVersion
    currentContact.value = contact
    contact.unreadCount = 0
    messages.value = []
    messageError.value = ''
    messageErrorPage = 1
    currentMessagePage.value = 1
    hasMoreMessages.value = false
    await loadMessages(contact.contactId)
    if (selectionVersion !== conversationVersion || currentContact.value?.contactId !== contact.contactId) return
    const latestSequence = messages.value.reduce((max, message) => Math.max(max, message.sequenceNo || 0), 0)
    if (latestSequence > 0) {
      saveCursor(contact.contactId, latestSequence)
      void chatApi.acknowledgeRead(contact.contactId, latestSequence)
    }
  }

  function clearCurrentContact() {
    conversationVersion += 1
    messageRequestVersion += 1
    currentContact.value = null
    messages.value = []
    messageError.value = ''
    messageErrorPage = 1
    currentMessagePage.value = 1
    hasMoreMessages.value = false
    loadingMessages.value = false
    loadingOlderMessages.value = false
  }

  async function sendMessage(content: string, retryClientMessageId?: string) {
    if (!currentContact.value) return null
    const text = content.trim()
    if (!text) return null

    const contact = currentContact.value
    const ownerId = userStore.userInfo?.userId
    const isCurrentOwner = () => Boolean(ownerId && ownerId === userStore.userInfo?.userId)
    pendingSendCount += 1
    sending.value = true
    const localId = retryClientMessageId || `local-${Date.now()}-${Math.random().toString(16).slice(2)}`
    const optimistic: ChatMessage = {
      messageId: localId,
      localId,
      clientMessageId: localId,
      contactId: contact.contactId,
      contactType: contact.contactTypeCode,
      senderId: userStore.userInfo?.userId || '',
      senderName: userStore.displayName,
      messageContent: text,
      messageType: TEXT_MESSAGE,
      sendTime: Date.now(),
      sendStatus: 'sending',
    }
    if (isCurrentOwner()) upsertMessage(optimistic)

    try {
      const response = await chatApi.sendMessage({
        contactId: contact.contactId,
        messageContent: text,
        messageType: TEXT_MESSAGE,
        clientMessageId: localId,
      })
      const saved = normalizeMessage(response.data, 'sent')
      saved.localId = localId
      if (isCurrentOwner()) upsertMessage(saved)
      return saved
    } catch (error) {
      if (isCurrentOwner()) upsertMessage({ ...optimistic, sendStatus: 'failed' })
      throw error
    } finally {
      pendingSendCount = Math.max(0, pendingSendCount - 1)
      sending.value = pendingSendCount > 0
    }
  }

  async function sendFileMessage(file: File) {
    if (!currentContact.value) return null
    const contact = currentContact.value
    const ownerId = userStore.userInfo?.userId
    const isCurrentOwner = () => Boolean(ownerId && ownerId === userStore.userInfo?.userId)
    pendingSendCount += 1
    sending.value = true
    const localId = `local-file-${Date.now()}-${Math.random().toString(16).slice(2)}`
    const optimistic: ChatMessage = {
      messageId: localId,
      localId,
      clientMessageId: localId,
      contactId: contact.contactId,
      contactType: contact.contactTypeCode,
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
    if (isCurrentOwner()) upsertMessage(optimistic)

    try {
      const response = await chatApi.sendMessage({
        contactId: contact.contactId,
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
      if (isCurrentOwner()) upsertMessage(saved)
      return saved
    } catch (error) {
      if (isCurrentOwner()) upsertMessage({ ...optimistic, sendStatus: 'failed' })
      throw error
    } finally {
      pendingSendCount = Math.max(0, pendingSendCount - 1)
      sending.value = pendingSendCount > 0
    }
  }

  function mergeInitData(data: WsInitData) {
    pendingApplyCount.value = data.applyCount || 0
    data.chatSessionUserList?.forEach((session) => {
      if (isActiveContact(session)) upsertContact(normalizeContact(session))
    })
    data.chatMessagesList?.forEach((raw) => {
      const message = normalizeMessage(
        raw,
        raw.sendUserId === userStore.userInfo?.userId ? 'sent' : 'received',
      )
      upsertMessage(message)
      acknowledgeMessage(message)
    })
  }

  function removeContactLocally(contactId: string) {
    contacts.value = contacts.value.filter((contact) => contact.contactId !== contactId)
    if (currentContact.value?.contactId === contactId) clearCurrentContact()
  }

  function handleSocketMessage(payload: WsMessage) {
    if (!payload || payload.messageType == null) return
    const messageType = Number(payload.messageType)
    if (!Number.isFinite(messageType)) return
    const normalizedPayload = { ...payload, messageType }
    if (messageType === INIT_MESSAGE) {
      mergeInitData((payload.extendData || {}) as WsInitData)
      return
    }

    if (messageType === CONTACT_APPLY_MESSAGE) {
      pendingApplyCount.value += 1
      return
    }

    const message = normalizeMessage(
      normalizedPayload,
      payload.sendUserId === userStore.userInfo?.userId ? 'sent' : 'received',
    )
    if (messageType === FILE_UPLOAD_MESSAGE) {
      const uploadedMessage = { ...message, messageType: MEDIA_MESSAGE, sendStatus: 'sent' as const }
      upsertMessage(uploadedMessage, false)
      acknowledgeMessage(uploadedMessage)
      return
    }

    if (messageType === CONTACT_NAME_UPDATE_MESSAGE && message.contactName) {
      const contact = contacts.value.find((item) => item.contactId === message.contactId)
      if (contact) contact.contactName = message.contactName
      if (currentContact.value?.contactId === message.contactId) {
        currentContact.value = { ...currentContact.value, contactName: message.contactName }
      }
      upsertMessage(message, false, false)
      return
    }

    if (messageType === CONTACT_REMOVED_MESSAGE) {
      removeContactLocally(message.contactId)
      ElMessage.warning(message.messageContent || '对方已将你移出联系人')
      void loadContacts()
      return
    }

    upsertMessage(message)
    acknowledgeMessage(message)

    const selfId = userStore.userInfo?.userId
    const affectsCurrentUser =
      messageType === 8 ||
      ((messageType === 11 || messageType === 12) && String(message.extendData || '') === selfId)
    if (affectsCurrentUser) {
      removeContactLocally(message.contactId)
      ElMessage.warning(message.messageContent || '你已离开该群聊')
    }

    if ([ADD_FRIEND_MESSAGE, ADD_FRIEND_SELF_MESSAGE, 8, 9, 11, 12].includes(messageType)) {
      void loadContacts()
    }
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
    if (!contacts.value.some((contact) => contact.contactId === message.contactId)) return
    saveCursor(message.contactId, message.sequenceNo)
    void chatApi.acknowledgeDelivered(message.contactId, message.sequenceNo)
    if (currentContact.value?.contactId === message.contactId) {
      void chatApi.acknowledgeRead(message.contactId, message.sequenceNo)
    }
  }

  async function syncOfflineMessages(expectedRequestVersion = contactsRequestVersion) {
    const ownerId = userStore.userInfo?.userId
    const results = await Promise.allSettled(
      contacts.value.map(async (contact) => {
        if (expectedRequestVersion !== contactsRequestVersion || ownerId !== userStore.userInfo?.userId) return
        let cursor = readCursor(contact.contactId)
        while (true) {
          if (expectedRequestVersion !== contactsRequestVersion || ownerId !== userStore.userInfo?.userId) return
          const response = await chatApi.syncMessages(contact.contactId, cursor)
          const batch = response.data || []
          if (batch.length === 0) break
          let nextCursor = cursor
          for (const raw of batch) {
            if (expectedRequestVersion !== contactsRequestVersion || ownerId !== userStore.userInfo?.userId) return
            const message = normalizeMessage(
              raw,
              raw.sendUserId === userStore.userInfo?.userId ? 'sent' : 'received',
            )
            upsertMessage(message)
            acknowledgeMessage(message)
            nextCursor = Math.max(nextCursor, message.sequenceNo || 0)
          }
          if (nextCursor <= cursor || batch.length < 200) break
          cursor = nextCursor
        }
      }),
    )
    if (results.some((result) => result.status === 'rejected')) {
      throw new Error('offline message sync failed')
    }
  }

  function attachSocket() {
    if (removeSocketListener) return
    resetStateForUser()
    removeSocketListener = userStore.addSocketListener(handleSocketMessage)
    userStore.connectSocket()
  }

  function detachSocket() {
    removeSocketListener?.()
    removeSocketListener = null
  }

  function retryMessage(message: ChatMessage) {
    if (currentContact.value?.contactId !== message.contactId) {
      ElMessage.warning('请先打开原会话后再重试')
      return Promise.resolve(null)
    }
    if (message.messageType === TEXT_MESSAGE) {
      return sendMessage(message.messageContent, message.clientMessageId || message.localId)
    }
    ElMessage.warning('媒体消息请重新选择文件后发送')
    return Promise.resolve(null)
  }

  function markApplyProcessed() {
    pendingApplyCount.value = Math.max(0, pendingApplyCount.value - 1)
  }

  async function retryCurrentMessages() {
    if (!currentContact.value) return []
    return loadMessages(currentContact.value.contactId, messageErrorPage)
  }

  async function retryOfflineSync() {
    if (!contacts.value.length) return
    offlineSyncError.value = ''
    try {
      await syncOfflineMessages()
    } catch {
      offlineSyncError.value = '部分离线消息同步失败，请重试'
    }
  }

  return {
    contacts,
    currentContact,
    messages,
    loadingContacts,
    contactsError,
    loadingMessages,
    messageError,
    loadingOlderMessages,
    hasMoreMessages,
    sending,
    offlineSyncError,
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
    retryCurrentMessages,
    retryOfflineSync,
    retryMessage,
    markApplyProcessed,
    attachSocket,
    detachSocket,
  }
})
