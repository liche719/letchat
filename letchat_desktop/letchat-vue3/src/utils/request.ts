import axios, { type AxiosError, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResponse } from '@/types/api'

const TOKEN_KEY = 'letchat_token'

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

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const body = response.data
    if (!body || body.status === 'success') {
      return body as unknown as AxiosResponse
    }

    if ([401, 901].includes(body.code)) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('token')
      router.push('/login')
    }

    if (!response.config.silentError) {
      ElMessage.error(body.info || '请求失败')
    }
    return Promise.reject(new Error(body.info || '请求失败'))
  },
  (error: AxiosError<{ info?: string; message?: string }>) => {
    const message = error.response?.data?.info || error.response?.data?.message || error.message || '网络连接异常'
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('token')
      router.push('/login')
    }
    if (!error.config?.silentError) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export { TOKEN_KEY }
export default request
