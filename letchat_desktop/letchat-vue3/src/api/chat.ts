import request from '@/utils/request'
import type { ChatMessage, ApiResponse } from '@/types/api'

export const chatApi = {
  // 发送消息
  sendMessage: (message: Message) => {
    return request.post<ApiResponse>('/chat/sendMessage', null, {
      params: message
    })
  },

  // 上传文件
  uploadFile: (file: File, coverFile: File, messageId: string) => {
    const formData = new FormData()
    formData.append('messageId', messageId)
    formData.append('file', file)
    formData.append('coverFile', coverFile)
    return request.post<ApiResponse>('/uploadFile', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 加载消息
  loadMessage: (contactId: string, contactType: number, lastRecieveTime?: string) => {
    const params: any = { contactId, contactType }
    if (lastRecieveTime) {
      params.lastRecieveTime = lastRecieveTime
    }
    return request.post<ApiResponse<Message[]>>('/chat/loadMessage', null, {
      params
    })
  },

  // 下载文件
  downloadFile: (fileId: string, showCover: boolean = false) => {
    const formData = new FormData()
    formData.append('fileId', fileId)
    formData.append('showCover', showCover.toString())
    return request.post('/chat/downloadFile', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      responseType: 'blob'
    })
  }
}