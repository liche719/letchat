import request from '@/utils/request'
import type { ApiResponse, UserInfo } from '@/types/api'

export const userApi = {
  getUserInfo() {
    return request.post<unknown, ApiResponse<UserInfo>>('/userInfo/getUserInfo')
  },

  saveUserInfo(userInfo: Partial<UserInfo>) {
    const formData = new FormData()
    Object.entries(userInfo).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        formData.append(key, String(value))
      }
    })

    return request.post<unknown, ApiResponse<UserInfo>>('/userInfo/saveUserInfo', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  updatePassword(password: string) {
    return request.post<unknown, ApiResponse<null>>('/userInfo/updatePassword', null, {
      params: { password },
    })
  },

  logout() {
    return request.post<unknown, ApiResponse<string>>('/userInfo/logout')
  },
}
