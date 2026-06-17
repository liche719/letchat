import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ContactInfo, ChatMessage, GroupInfo } from '@/types/api'
import request from '@/utils/request'
import { useUserStore } from './user'

export const useChatStore = defineStore('chat', () => {
  const userStore = useUserStore()
  
  // 状态
  const contacts = ref<ContactInfo[]>([])
  const currentContact = ref<ContactInfo | null>(null)
  const messages = ref<ChatMessage[]>([])
  const myGroups = ref<GroupInfo[]>([])

  // 计算属性
  const unreadTotal = computed(() => 
    contacts.value.reduce((total, contact) => total + (contact.unreadCount || 0), 0)
  )

  // 加载联系人列表
  const loadContacts = async () => {
    try {
      const [friendsResponse, groupsResponse] = await Promise.all([
        request.post('/contact/loadContact', null, { params: { contactType: 'U' } }),
        request.post('/contact/loadContact', null, { params: { contactType: 'G' } })
      ])
      
      const friends = friendsResponse.data || []
      const groups = groupsResponse.data || []
      
      contacts.value = [...friends, ...groups]
    } catch (error) {
      console.error('加载联系人失败:', error)
    }
  }

  // 加载我的群组
  const loadMyGroups = async () => {
    try {
      const response = await request.post('/contact/loadContact', null, { params: { contactType: 'G' } })
      myGroups.value = response.data || []
    } catch (error) {
      console.error('加载群组失败:', error)
    }
  }

  // 搜索联系人
  const searchContacts = async (keyword: string) => {
    try {
      const response = await request.post('/contact/search', null, { params: { contactId: keyword } })
      return response.data || []
    } catch (error) {
      console.error('搜索联系人失败:', error)
      return []
    }
  }

  // 加载聊天记录
  const loadMessages = async (contactId: string, pageNo: number = 1) => {
    try {
      const response = await request.post('/chat/loadChatMessage', null, {
        params: { contactId, contactType: 1, pageNo }
      })
      
      const messageList = response.data?.list || []
      
      // 映射后端字段到前端字段
      const mappedMessages = messageList.map((msg: any) => ({
        messageId: msg.messageId?.toString() || '',
        contactId: msg.contactId || '',
        senderId: msg.sendUserId || '',
        senderName: msg.sendUserNickName || '',
        senderAvatar: '', // 后端没有返回头像，使用默认值
        messageContent: msg.messageContent || '',
        messageType: msg.messageType || 2,
        sendTime: msg.sendTime ? new Date(msg.sendTime).toISOString() : new Date().toISOString(),
        sendStatus: 2, // 已发送状态
        fileName: msg.fileName || '',
        fileSize: msg.fileSize || 0,
        fileType: msg.fileType || 2,
        fileUrl: msg.fileUrl || '',
        coverUrl: msg.coverUrl || ''
      }))
      
      // 按发送时间升序排序（最新消息在最后）
      const sortedMessages = mappedMessages.sort((a: ChatMessage, b: ChatMessage) => 
        new Date(a.sendTime).getTime() - new Date(b.sendTime).getTime()
      )
      
      if (pageNo === 1) {
        messages.value = sortedMessages
      } else {
        messages.value.unshift(...sortedMessages)
      }
      
      return sortedMessages
    } catch (error) {
      console.error('加载聊天记录失败:', error)
      return []
    }
  }

  // 发送消息
  const sendMessage = async (content: string, messageType: number = 2) => {
    if (!currentContact.value) return

    // 创建临时消息对象
    const tempMessage: ChatMessage = {
      messageId: Date.now().toString(),
      contactId: currentContact.value.contactId,
      senderId: userStore.userInfo?.userId || '',
      senderName: userStore.userInfo?.nickName || '',
      senderAvatar: userStore.userInfo?.avatar || '',
      messageContent: content,
      messageType,
      sendTime: new Date().toISOString(),
      sendStatus: 1, // 发送中
      fileName: '',
      fileSize: 0,
      fileType: 2,
      fileUrl: '',
      coverUrl: ''
    }

    // 先添加到本地消息列表
    if (currentContact.value && currentContact.value.contactId === tempMessage.contactId) {
      messages.value.push(tempMessage)
    }

    try {
      const response = await request.post('/chat/sendMessage', null, {
        params: {
          contactId: currentContact.value.contactId,
          messageContent: content,
          messageType,
          fileSize: 0,
          fileName: '',
          fileType: 2
        }
      })

      // 更新消息状态为已发送
      const sentMessage = messages.value.find(m => m.messageId === tempMessage.messageId)
      if (sentMessage) {
        sentMessage.sendStatus = 2
        sentMessage.messageId = response.data.messageId
      }

      return response.data
    } catch (error) {
      // 更新消息状态为发送失败
      const failedMessage = messages.value.find(m => m.messageId === tempMessage.messageId)
      if (failedMessage) {
        failedMessage.sendStatus = 3
      }
      console.error('发送消息失败:', error)
      throw error
    }
  }

  // 发送媒体消息
  const sendMediaMessage = async (file: File, messageType: number = 5) => {
    if (!currentContact.value) return

    // 创建临时消息对象
    const tempMessage: ChatMessage = {
      messageId: Date.now().toString(),
      contactId: currentContact.value.contactId,
      senderId: userStore.userInfo?.userId || '',
      senderName: userStore.userInfo?.nickName || '',
      senderAvatar: userStore.userInfo?.avatar || '',
      messageContent: '',
      messageType,
      sendTime: new Date().toISOString(),
      sendStatus: 1, // 发送中
      fileName: file.name,
      fileSize: file.size,
      fileType: messageType,
      fileUrl: '',
      coverUrl: ''
    }

    // 先添加到本地消息列表
    if (currentContact.value && currentContact.value.contactId === tempMessage.contactId) {
      messages.value.push(tempMessage)
    }

    try {
      // 1. 先发送消息获取messageId
      const messageResponse = await request.post('/chat/sendMessage', null, {
        params: {
          contactId: currentContact.value.contactId,
          messageContent: '',
          messageType,
          fileSize: file.size,
          fileName: file.name,
          fileType: messageType
        }
      })

      const messageId = messageResponse.data.messageId

      // 2. 上传文件
      const formData = new FormData()
      formData.append('messageId', messageId)
      formData.append('file', file)
      formData.append('coverFile', file) // 暂时使用相同文件作为coverFile
      
      await request.post('/chat/uploadFile', formData)

      // 更新消息状态为已发送
      const sentMessage = messages.value.find(m => m.messageId === tempMessage.messageId)
      if (sentMessage) {
        sentMessage.sendStatus = 2
        sentMessage.messageId = messageId
        sentMessage.fileUrl = `/chat/downloadFile/${file.name}`
      }

      return messageResponse.data
    } catch (error) {
      // 更新消息状态为发送失败
      const failedMessage = messages.value.find(m => m.messageId === tempMessage.messageId)
      if (failedMessage) {
        failedMessage.sendStatus = 3
      }
      console.error('发送媒体消息失败:', error)
      throw error
    }
  }

  // 发送文件消息（兼容Chat.vue中的调用方式）
  const sendFileMessage = async ({ contactId, file, messageType }: {
    contactId: string
    file: File
    messageType: number
  }) => {
    if (!currentContact.value || currentContact.value.contactId !== contactId) return
    return sendMediaMessage(file, messageType)
  }

  // 设置当前联系人
  const setCurrentContact = async (contact: ContactInfo) => {
    currentContact.value = contact
    if (contact) {
      await loadMessages(contact.contactId)
    }
  }

  // 接收WebSocket消息
  const receiveMessage = (message: ChatMessage) => {
    // 如果消息是当前聊天对象的，添加到消息列表
    if (currentContact.value && message.contactId === currentContact.value.contactId) {
      messages.value.push(message)
    }
    
    // 更新联系人未读数
    const contact = contacts.value.find(c => c.contactId === message.contactId)
    if (contact) {
      contact.lastMessage = message.messageContent
      contact.lastMessageTime = message.sendTime
      if (!currentContact.value || currentContact.value.contactId !== message.contactId) {
        contact.unreadCount = (contact.unreadCount || 0) + 1
      }
    }
  }

  // 清空未读数
  const clearUnreadCount = (contactId: string) => {
    const contact = contacts.value.find(c => c.contactId === contactId)
    if (contact) {
      contact.unreadCount = 0
    }
  }

  return {
    contacts,
    currentContact,
    messages,
    myGroups,
    unreadTotal,
    loadContacts,
    loadMyGroups,
    searchContacts,
    loadMessages,
    sendMessage,
    sendMediaMessage,
    sendFileMessage,
    setCurrentContact,
    receiveMessage,
    clearUnreadCount
  }
})