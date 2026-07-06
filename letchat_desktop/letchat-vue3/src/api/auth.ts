import request from '@/utils/request'
import type { ApiResponse, CheckCode, SysSetting, UserInfo } from '@/types/api'

export const authApi = {
  login(email: string, password: string, checkCode: string, checkCodeKey: string) {
    return request.post<unknown, ApiResponse<UserInfo>>('/account/login', null, {
      params: { email, password, checkCode, checkCodeKey },
    })
  },

  register(email: string, password: string, nickName: string, checkCode: string, checkCodeKey: string) {
    return request.post<unknown, ApiResponse<null>>('/account/register', null, {
      params: { email, password, nickName, checkCode, checkCodeKey },
    })
  },

  getCheckCode() {
    return request.post<unknown, ApiResponse<CheckCode>>('/account/checkCode', null, { silentError: true })
  },

  getSysSetting() {
    return request.post<unknown, ApiResponse<SysSetting>>('/account/getSysSetting')
  },
}
