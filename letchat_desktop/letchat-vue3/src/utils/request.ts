import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResponse } from '@/types/api'

const TOKEN_KEY = 'letchat_token'
const USER_KEY = 'letchat_user'
const SESSION_EXPIRED_EVENT = 'letchat:session-expired'

declare module 'axios' {
  interface AxiosRequestConfig {
    silentError?: boolean
  }
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY) || localStorage.getItem('token')
  if (token) {
    config.headers.token = token
  }
  return config
})

function expireSession(message: string) {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem('token')
  localStorage.removeItem(USER_KEY)
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, { detail: { message } }))
  }
  if (router.currentRoute.value.path !== '/login') {
    void router.replace('/login')
  }
}

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const body = response.data
    if (!body || body.status === 'success') {
      return body as unknown as AxiosResponse
    }

    const sessionExpired = [401, 901].includes(body.code)
    if (sessionExpired) expireSession('登录状态已失效，请重新登录')

    if (!response.config.silentError && !sessionExpired) {
      ElMessage.error(body.info || '请求失败')
    }
    return Promise.reject(new Error(body.info || '请求失败'))
  },
  (error: AxiosError<{ info?: string; message?: string }>) => {
    const message = error.response?.data?.info || error.response?.data?.message || error.message || '网络连接异常'
    const sessionExpired = error.response?.status === 401
    if (sessionExpired) expireSession('登录状态已失效，请重新登录')
    if (!error.config?.silentError && !sessionExpired) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export { TOKEN_KEY }
export default request
