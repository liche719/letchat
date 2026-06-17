import request from '@/utils/request'
import type { LoginResponse, ApiResponse } from '@/types/api'

export const authApi = {
  // 用户登录
  login: (email: string, password: string, checkCode: string, checkCodeKey: string) => {
    return request.post<ApiResponse<LoginResponse>>('/account/login', null, {
      params: { email, password, checkCode, checkCodeKey }
    })
  },

  // 用户注册
  register: (email: string, password: string, nickName: string, checkCode: string, checkCodeKey: string) => {
    return request.post<ApiResponse>('/account/register', null, {
      params: { email, password, nickName, checkCode, checkCodeKey }
    })
  },
  
  // 获取验证码 - 修改后
  getCheckCode: () => {
    return request.post<ApiResponse<{ checkCode: string; checkCodeKey: string }>>('/account/checkCode')
  }
}