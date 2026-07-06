export interface ApiResponse<T = unknown> {
  status: 'success' | 'error'
  code: number
  info: string
  data: T
}

export interface PaginationResult<T> {
  totalCount: number
  pageSize: number
  pageNo: number
  pageTotal: number
  list: T[]
}

export interface CheckCode {
  checkCode: string
  checkCodeKey: string
}

export interface UserInfo {
  userId: string
  email?: string
  nickName: string
  sex?: number
  joinType?: number
  personalSignature?: string
  areaCode?: string
  areaName?: string
  token?: string
  admin?: boolean
  contactStatus?: number
  avatar?: string
  status?: number
  createTime?: string
  lastLoginTime?: string
  lastOffTime?: number
  onlineType?: number
}

export type ContactKind = 'U' | 'G'

export interface ContactInfo {
  userId?: string
  contactId: string
  contactName: string
  contactType: ContactKind
  contactTypeCode: number
  status?: number
  sex?: number
  createTime?: string
  lastUpdateTime?: string
  lastMessage?: string
  lastReceiveTime?: number | string
  unreadCount?: number
  memberCount?: number
  avatar?: string
}

export interface BackendContact {
  userId?: string
  contactId: string
  contactName?: string
  contactType: number | string
  status?: number
  sex?: number
  createTime?: string
  lastUpdateTime?: string
  lastMessage?: string
  lastReceiveTime?: number | string
  memberCount?: number
}

export interface GroupInfo {
  groupId: string
  groupName: string
  groupOwnerId?: string
  groupOwnerNickName?: string
  createTime?: string
  groupNotice?: string
  joinType?: number
  status?: number
  memberCount?: number
}

export interface SearchResult {
  contactId: string
  contactType: ContactKind | 'USER' | 'GROUP' | string
  nickName?: string
  contactName?: string
  status?: number
  statusName?: string
  sex?: number
  areaName?: string
  avatar?: string
}

export interface FriendApply {
  applyId: number
  applyUserId: string
  receiveUserId?: string
  contactType?: number
  contactId: string
  lastApplyTime?: number
  status: number
  applyInfo?: string
  contactName?: string
  statusName?: string
  applicantAvatar?: string
}

export type MessageSendStatus = 'sending' | 'sent' | 'failed' | 'received'

export interface BackendChatMessage {
  messageId?: number | string
  sessionId?: string
  messageType: number
  messageContent?: string
  sendUserId?: string
  sendUserNickName?: string
  sendTime?: number
  contactId?: string
  contactName?: string
  contactType?: number
  fileSize?: number
  fileName?: string
  fileType?: number
  status?: number
  extendData?: unknown
  memberCount?: number
}

export interface ChatMessage {
  messageId: string
  localId?: string
  sessionId?: string
  contactId: string
  rawContactId?: string
  contactName?: string
  contactType?: number
  senderId: string
  senderName: string
  messageContent: string
  messageType: number
  sendTime: number
  sendStatus: MessageSendStatus
  status?: number
  fileSize?: number
  fileName?: string
  fileType?: number
  extendData?: unknown
}

export interface WsInitData {
  chatSessionUserList?: BackendContact[]
  chatMessagesList?: BackendChatMessage[]
  applyCount?: number
}

export interface WsMessage extends BackendChatMessage {
  extendData?: WsInitData | unknown
}

export interface SysSetting {
  [key: string]: unknown
}
