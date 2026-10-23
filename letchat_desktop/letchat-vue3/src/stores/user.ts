import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { userApi } from '@/api/user'
import { TOKEN_KEY } from '@/utils/request'
import type { UserInfo, WsMessage } from '@/types/api'

const USER_KEY = 'letchat_user'
const SESSION_EXPIRED_EVENT = 'letchat:session-expired'
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
  let socketConnectPromise: Promise<void> | null = null
  let socketRequestVersion = 0

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

  function handleSessionExpired(message = '登录状态已失效，请重新登录') {
    const hadSession = Boolean(token.value || userInfo.value)
    disconnectSocket()
    clearSession()
    if (hadSession) ElMessage.error(message)
  }

  function handleSessionExpiredEvent(event: Event) {
    const detail = (event as CustomEvent<{ message?: string }>).detail
    handleSessionExpired(detail?.message)
  }

  function notifySocketMessage(raw: string) {
    try {
      const message = JSON.parse(raw) as WsMessage
      if (Number(message.messageType) === 7) {
        handleSessionExpired('账号已在其他设备登录，请重新登录')
        return
      }
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

    const delay = reconnectAttempts >= 8 ? 60000 : Math.min(1000 * 2 ** reconnectAttempts, 20000)
    if (reconnectAttempts === 8) {
      ElMessage.warning('实时消息连接暂不可用，登录状态已保留，系统会继续重试')
    }
    reconnectAttempts += 1
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      void connectSocket()
    }, delay)
  }

  function resolveWebSocketUrl(baseUrl: string) {
    const rawUrl = baseUrl.trim() || '/socket.io'
    const url = new URL(rawUrl, window.location.href)
    if (url.protocol === 'http:') url.protocol = 'ws:'
    if (url.protocol === 'https:') url.protocol = 'wss:'
    return url.toString()
  }

  function disconnectSocket() {
    socketRequestVersion += 1
    socketConnectPromise = null
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

    if (socketConnectPromise) return socketConnectPromise
    const requestVersion = socketRequestVersion
    const connectionToken = token.value
    socketConnectPromise = openSocket(connectionToken, requestVersion).finally(() => {
      if (socketRequestVersion === requestVersion) socketConnectPromise = null
    })
    return socketConnectPromise
  }

  async function openSocket(connectionToken: string, requestVersion: number) {
    try {
      socketStatus.value = 'connecting'
      const response = await userApi.createWebSocketTicket()
      if (
        !token.value ||
        token.value !== connectionToken ||
        socketRequestVersion !== requestVersion
      ) return

      const wsBase = resolveWebSocketUrl(import.meta.env.VITE_WS_BASE_URL || '/socket.io')
      const separator = wsBase.includes('?') ? '&' : '?'
      const nextSocket = new WebSocket(
        `${wsBase}${separator}ticket=${encodeURIComponent(response.data.ticket)}`,
      )
      socket.value = nextSocket

      nextSocket.onopen = () => {
        if (socket.value !== nextSocket) return
        isOnline.value = true
        socketStatus.value = 'open'
        reconnectAttempts = 0
        startHeartbeat()
      }

      nextSocket.onmessage = (event) => {
        if (socket.value !== nextSocket) return
        notifySocketMessage(event.data)
      }

      nextSocket.onerror = () => {
        if (socket.value !== nextSocket) return
        isOnline.value = false
        socketStatus.value = 'error'
      }

      nextSocket.onclose = () => {
        if (socket.value !== nextSocket) return
        stopHeartbeat()
        isOnline.value = false
        socketStatus.value = 'closed'
        socket.value = null
        scheduleReconnect()
      }
    } catch {
      if (
        !token.value ||
        token.value !== connectionToken ||
        socketRequestVersion !== requestVersion
      ) return
      isOnline.value = false
      socketStatus.value = 'error'
      scheduleReconnect()
    }
  }

  async function login(email: string, password: string, checkCode: string, checkCodeKey: string) {
    const response = await authApi.login(email, password, checkCode, checkCodeKey)
    const user = response.data
    if (!user.token) {
      throw new Error('登录响应缺少 token')
    }
    persistSession(user.token, user)
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
    return authApi.getCheckCode()
  }

  if (typeof window !== 'undefined') {
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpiredEvent)
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
    handleSessionExpired,
  }
})
