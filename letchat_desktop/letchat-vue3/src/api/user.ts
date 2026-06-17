import request from '@/utils/request'
import type { UserInfo, ApiResponse } from '@/types/api'

export const userApi = {
  // 获取用户信息
  getUserInfo: () => {
    return request.post<ApiResponse<UserInfo>>('/userInfo/getUserInfo')
  },

  // 保存用户信息
  saveUserInfo: (userInfo: Partial<UserInfo>) => {
    return request.post<ApiResponse>('/userInfo/saveUserInfo', null, {
      params: userInfo
    })
  },

  // 更新密码
  updatePassword: (password: string) => {
    return request.post<ApiResponse>('/userInfo/updatePassword', null, {
      params: { password }
    })
  },

  // 用户登出
  logout: () => {
    return request.post<ApiResponse>('/userInfo/logout')
  }
}