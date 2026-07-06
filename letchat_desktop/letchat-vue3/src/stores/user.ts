import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { TOKEN_KEY } from '@/utils/request'
import type { UserInfo, WsMessage } from '@/types/api'

const USER_KEY = 'letchat_user'
type Timer = ReturnType<typeof setTimeout>
type SocketListener = (message: WsMessage) => void

function readStoredUser() {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(readStoredUser())
  const socket = ref<WebSocket | null>(null)
  const isOnline = ref(false)
  const socketStatus = ref<'idle' | 'connecting' | 'open' | 'closed' | 'error'>('idle')

  const isLoggedIn = computed(() => Boolean(token.value))
  const displayName = computed(() => userInfo.value?.nickName || userInfo.value?.userId || 'LetChat 用户')

  const listeners = new Set<SocketListener>()
  let reconnectAttempts = 0
  let reconnectTimer: Timer | null = null
  let heartbeatTimer: Timer | null = null

  function persistSession(nextToken: string, nextUser: UserInfo) {
    token.value = nextToken
    userInfo.value = nextUser
    localStorage.setItem(TOKEN_KEY, nextToken)
    localStorage.setItem('token', nextToken)
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
  }

  function clearSession() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem('token')
    localStorage.removeItem(USER_KEY)
  }

  function notifySocketMessage(raw: string) {
    try {
      const message = JSON.parse(raw) as WsMessage
      listeners.forEach((listener) => listener(message))
    } catch (error) {
      console.warn('WebSocket 消息解析失败', error, raw)
    }
  }

  function addSocketListener(listener: SocketListener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socket.value?.readyState === WebSocket.OPEN) {
        socket.value.send('heart')
      }
    }, 25000)
  }

  function scheduleReconnect() {
    if (!token.value || reconnectTimer || socketStatus.value === 'open') return

    const maxAttempts = 8
    if (reconnectAttempts >= maxAttempts) {
      socketStatus.value = 'closed'
      ElMessage.warning('WebSocket 重连失败，请重新登录')
      clearSession()
      return
    }

    const delay = Math.min(1000 * 2 ** reconnectAttempts, 20000)
    reconnectAttempts += 1
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connectSocket()
    }, delay)
  }

  function disconnectSocket() {
    stopHeartbeat()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }

    if (socket.value) {
      socket.value.onclose = null
      socket.value.close(1000, 'client logout')
      socket.value = null
    }

    isOnline.value = false
    socketStatus.value = 'closed'
    reconnectAttempts = 0
  }

  function connectSocket() {
    if (!token.value) return
    if (socket.value?.readyState === WebSocket.OPEN || socket.value?.readyState === WebSocket.CONNECTING) return

    const wsBase = import.meta.env.VITE_WS_BASE_URL || 'ws://localhost:7071/ws'
    const separator = wsBase.includes('?') ? '&' : '?'
    socketStatus.value = 'connecting'
    socket.value = new WebSocket(`${wsBase}${separator}token=${encodeURIComponent(token.value)}`)

    socket.value.onopen = () => {
      isOnline.value = true
      socketStatus.value = 'open'
      reconnectAttempts = 0
      startHeartbeat()
    }

    socket.value.onmessage = (event) => {
      notifySocketMessage(event.data)
    }

    socket.value.onerror = () => {
      isOnline.value = false
      socketStatus.value = 'error'
    }

    socket.value.onclose = (event) => {
      stopHeartbeat()
      isOnline.value = false
      socketStatus.value = 'closed'
      socket.value = null
      if (event.code !== 1000) {
        scheduleReconnect()
      }
    }
  }

  async function login(email: string, password: string, checkCode: string, checkCodeKey: string) {
    const response = await authApi.login(email, password, checkCode, checkCodeKey)
    const user = response.data
    if (!user.token) {
      throw new Error('登录响应缺少 token')
    }
    persistSession(user.token, user)
    connectSocket()
    return user
  }

  async function logout() {
    try {
      if (token.value) {
        await userApi.logout()
      }
    } catch {
      // 退出时后端 token 可能已经过期，本地仍要清理。
    } finally {
      disconnectSocket()
      clearSession()
    }
  }

  async function refreshUserInfo() {
    const response = await userApi.getUserInfo()
    userInfo.value = { ...userInfo.value, ...response.data }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    return userInfo.value
  }

  async function updateUserInfo(payload: Partial<UserInfo>) {
    const response = await userApi.saveUserInfo(payload)
    userInfo.value = { ...userInfo.value, ...response.data }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    return userInfo.value
  }

  async function getCheckCode() {
    const response = await authApi.getCheckCode()
    return response.data
  }

  if (token.value) {
    connectSocket()
  }

  return {
    token,
    userInfo,
    socket,
    isOnline,
    socketStatus,
    isLoggedIn,
    displayName,
    login,
    logout,
    refreshUserInfo,
    updateUserInfo,
    connectSocket,
    disconnectSocket,
    addSocketListener,
    getCheckCode,
  }
})
