// API响应类型定义
export interface ApiResponse<T = any> {
  status: 'success' | 'error'
  code: number
  info: string
  data: T
}

// 用户信息类型
export interface UserInfo {
  userId: string
  email: string
  nickName: string
  joinType: number
  sex: number
  password: string
  personalSignature: string
  status: number
  createTime: string
  lastLoginTime: string
  areaName: string
  areaCode: string
  lastOffTime: number
  onlineType: number
}

// 登录响应
export interface LoginResponse {
  token: string
  userInfo: UserInfo
}

// 联系人信息
export interface ContactInfo {
  contactId: string
  contactName: string
  contactType: 'U' | 'G' // U:用户 G:群组
  avatar: string
  lastMessage?: string
  lastMessageTime?: string
  unreadCount?: number
}

// 群组信息
export interface GroupInfo {
  groupId: string
  groupName: string
  groupNotice: string
  joinType: number
  memberCount: number
  avatar: string
  createTime: string
}

// 消息类型
export interface ChatMessage {
  messageId: string
  contactId: string
  senderId: string
  senderName: string
  senderAvatar: string
  messageContent: string
  messageType: number
  sendTime: string
  fileName?: string
  fileSize?: number
  fileType?: number
  fileUrl?: string
  coverUrl?: string
}

// 好友申请
export interface FriendApply {
  applyId: number
  applicantId?: string
  applyUserId?: string
  applicantName?: string
  contactName?: string
  applicantAvatar?: string
  applyInfo: string
  status: number // 0:待处理 1:同意 2:拒绝 3:拉黑
  createTime?: string
  lastApplyTime?: string
  statusName?: string
}

// 版本更新信息
export interface UpdateInfo {
  id: number
  version: string
  updateList: string[]
  size: number | null
  fileName: string
  fileType: string | null
  outerLink: string
}

// WebSocket消息类型
export interface WsMessage {
  type: number
  message?: string
  data?: any
}

// 搜索联系人结果
export interface SearchResult {
  contactId: string
  contactName?: string
  nickName?: string
  contactType?: 'USER' | 'GROUP' | 'U' | 'G'
  avatar?: string
  status: number
  statusName?: string
  sex?: number
  areaName?: string
  isFriend?: boolean
}

// 系统设置
export interface SysSetting {
  // 根据实际需求添加系统设置字段
  [key: string]: any
}