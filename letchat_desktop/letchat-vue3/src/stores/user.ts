import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { UserInfo } from '@/types/api'
import request from '@/utils/request'
import { authApi } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { handleApiResponse } from '@/utils/responseHandler'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const socket = ref<WebSocket | null>(null)
  const isOnline = ref(false)

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)

  // 登录
  const login = async (email: string, password: string, checkCode: string, checkCodeKey: string) => {
    const response = await authApi.login(email, password, checkCode, checkCodeKey)
    
    token.value = response.data.token
    userInfo.value = response.data.userInfo
    localStorage.setItem('token', token.value)
    
    // 连接WebSocket并启动连接检查器
    connectSocket()
    startConnectionChecker()
    
    return response
  }

  // 登出
  const logout = async () => {
    try {
      await request.post('/userInfo/logout')
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      // 断开WebSocket并停止连接检查器
      disconnectSocket()
      stopConnectionChecker()
      
      // 清除本地存储
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      isOnline.value = false
    }
  }

  // 获取用户信息
  const getUserInfo = async () => {
    const response = await request.post('/userInfo/getUserInfo', {}, { headers: { 'Content-Type': 'multipart/form-data' } })
    userInfo.value = response.data
    return response.data
  }

  // 更新用户信息
  const updateUserInfo = async (userData: Partial<UserInfo>) => {
    const formData = new FormData()
    
    // 添加用户信息
    Object.keys(userData).forEach(key => {
      const value = userData[key as keyof UserInfo]
      if (value !== undefined && value !== null) {
        formData.append(key, value.toString())
      }
    })

    const result = await handleApiResponse(
      request.post('/userInfo/saveUserInfo', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }),
      '个人信息更新成功！'
    )

    if (result.success) {
      // 更新本地用户信息
      if (result.data) {
        userInfo.value = { ...userInfo.value, ...result.data }
      }
      return result.data
    } else {
      throw new Error(result.message)
    }
  }

  // 连接WebSocket
  let reconnectAttempts = 0
  let reconnectTimer: NodeJS.Timeout | null = null
  let heartbeatInterval: NodeJS.Timeout | null = null
  let connectionChecker: NodeJS.Timeout | null = null

  // 启动连接状态检查器
  const startConnectionChecker = () => {
    if (connectionChecker) {
      clearInterval(connectionChecker)
    }
    
    connectionChecker = setInterval(() => {
      if (token.value) {
        // 有token但没有连接，尝试连接
        if (!socket.value || socket.value.readyState === WebSocket.CLOSED) {
          console.log('检测到token但WebSocket未连接，尝试连接...')
          connectSocket()
        }
        // 连接已断开，尝试重连（确保没有重连定时器在运行）
        else if (socket.value.readyState === WebSocket.CLOSED && !reconnectTimer && !isOnline.value) {
          console.log('WebSocket已断开，尝试重连...')
          scheduleReconnect()
        }
      } else {
        // 没有token，清理所有连接
        if (socket.value) {
          disconnectSocket()
        }
      }
    }, 3000) // 每3秒检查一次
  }

  // 停止连接状态检查器
  const stopConnectionChecker = () => {
    if (connectionChecker) {
      clearInterval(connectionChecker)
      connectionChecker = null
    }
  }

  // 启动心跳
  const startHeartbeat = () => {
    // 先清理之前的心跳定时器
    if (heartbeatInterval) {
      clearInterval(heartbeatInterval)
      heartbeatInterval = null
    }

    // 只有连接打开时才启动心跳
    if (socket.value && socket.value.readyState === WebSocket.OPEN) {
      heartbeatInterval = setInterval(() => {
        if (socket.value && socket.value.readyState === WebSocket.OPEN) {
          socket.value.send('heart')
          console.log('Heartbeat sent')
        } else {
          // 如果连接断开，清理心跳定时器
          if (heartbeatInterval) {
            clearInterval(heartbeatInterval)
            heartbeatInterval = null
          }
        }
      }, 4000)
    }
  }

  // 停止心跳
  const stopHeartbeat = () => {
    if (heartbeatInterval) {
      clearInterval(heartbeatInterval)
      heartbeatInterval = null
    }
  }

  const connectSocket = () => {
    if (!token.value) {
      console.log('没有token，无法连接WebSocket')
      return
    }

    // 如果已经连接，不再重复连接
    if (socket.value && socket.value.readyState === WebSocket.OPEN) {
      console.log('WebSocket已连接，无需重复连接')
      return
    }

    // 清除之前的重连定时器
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }

    // 使用原生WebSocket格式：ws://localhost:5051/ws?token=xxx
    const wsUrl = `ws://localhost:7071/ws?token=${token.value}`
    socket.value = new WebSocket(wsUrl)

    socket.value.onopen = () => {
      console.log('WebSocket connected')
      isOnline.value = true
      reconnectAttempts = 0 // 重置重连次数
      
      // 连接成功后立即开始心跳
      startHeartbeat()
    }

    socket.value.onclose = (event) => {
      console.log('WebSocket disconnected:', event.code, event.reason)
      isOnline.value = false
      
      // 清理心跳定时器
      stopHeartbeat()

      // 如果不是正常关闭，尝试重连
      if (event.code !== 1000) { // 1000 表示正常关闭
        scheduleReconnect()
      }
    }

    socket.value.onerror = (error) => {
      console.error('WebSocket connection error:', error)
      isOnline.value = false
    }

    socket.value.onmessage = (event) => {
      console.log('WebSocket message received:', event.data)
    }
  }

  // 重连机制
  const scheduleReconnect = () => {
    // 避免重复重连
    if (reconnectTimer) return

    // 如果已经连接成功，不需要重连
    if (isOnline.value) return

    // 如果没有token，直接返回登录页
    if (!token.value) {
      console.log('没有token，跳转到登录页')
      // 这里不直接跳转，让路由守卫处理
      return
    }

    // 指数退避重连策略
    const maxReconnectAttempts = 10
    const baseDelay = 1000 // 1秒
    const maxDelay = 30000 // 30秒

    if (reconnectAttempts < maxReconnectAttempts) {
      const delay = Math.min(baseDelay * Math.pow(2, reconnectAttempts), maxDelay)
      reconnectAttempts++

      console.log(`WebSocket重连中... 第${reconnectAttempts}次，${delay}ms后重试`)
      
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        // 再次检查是否已连接
        if (token.value && !isOnline.value) {
          connectSocket()
        }
      }, delay)
    } else {
      console.error('WebSocket重连失败，已达到最大重连次数，清除token并跳转登录页')
      // 达到最大重连次数，清除token
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
    }
  }

  // 手动断开连接（用于登出）
  const disconnectSocket = () => {
    if (socket.value) {
      // 设置正常关闭标志
      socket.value.onclose = (event) => {
        console.log('WebSocket正常断开连接')
        isOnline.value = false
        stopHeartbeat()
      }
      socket.value.close(1000, '用户登出')
      socket.value = null
    }
    
    // 清理重连定时器
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    
    reconnectAttempts = 0
  }

  // 获取验证码
  const getCheckCode = async () => {
    const response = await request.post('/account/checkCode', {}, { headers: { 'Content-Type': 'multipart/form-data' } })
    return response.data
  }

  // 初始化时启动连接检查器
  if (token.value) {
    connectSocket()
    startConnectionChecker()
  } else {
    // 没有token时也启动检查器，确保状态正确
    startConnectionChecker()
  }

  return {
    token,
    userInfo,
    socket,
    isOnline,
    isLoggedIn,
    login,
    logout,
    getUserInfo,
    updateUserInfo,
    connectSocket,
    disconnectSocket,
    getCheckCode
  }
})