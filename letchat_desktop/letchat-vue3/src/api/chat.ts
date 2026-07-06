import request from '@/utils/request'
import type { ApiResponse, BackendChatMessage, PaginationResult } from '@/types/api'

export interface SendMessageParams {
  contactId: string
  messageContent: string
  messageType: number
  fileSize?: number
  fileName?: string
  fileType?: number
}

export const chatApi = {
  sendMessage(params: SendMessageParams) {
    return request.post<unknown, ApiResponse<BackendChatMessage>>('/chat/sendMessage', null, { params })
  },

  uploadFile(messageId: string, file: File, coverFile?: File) {
    const formData = new FormData()
    formData.append('messageId', messageId)
    formData.append('file', file)
    formData.append('coverFile', coverFile || file)

    return request.post<unknown, ApiResponse<null>>('/chat/uploadFile', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  loadChatMessage(contactId: string, pageNo = 1) {
    return request.post<unknown, ApiResponse<PaginationResult<BackendChatMessage>>>('/chat/loadChatMessage', null, {
      params: { contactId, pageNo },
    })
  },
}
