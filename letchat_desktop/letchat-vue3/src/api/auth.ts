import request from '@/utils/request'
import type { ApiResponse, CheckCode, SysSetting, UserInfo } from '@/types/api'

function unwrapCheckCode(response: ApiResponse<CheckCode> | { data: ApiResponse<CheckCode> }): CheckCode {
  const payload = response.data
  const captcha = typeof (payload as CheckCode).checkCode === 'string'
    ? (payload as CheckCode)
    : (payload as ApiResponse<CheckCode>).data

  if (!captcha?.checkCode || !captcha.checkCodeKey) {
    throw new Error('验证码响应不完整')
  }
  return captcha
}

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

  async getCheckCode(): Promise<CheckCode> {
    const response = await request.post<unknown, ApiResponse<CheckCode>>('/account/checkCode', null, { silentError: true })
    return unwrapCheckCode(response)
  },

  getSysSetting() {
    return request.post<unknown, ApiResponse<SysSetting>>('/account/getSysSetting')
  },
}
