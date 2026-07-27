/**
 * 上传配置工具
 * 统一处理所有文件上传的token和配置
 */

import { computed } from 'vue'

interface AdditionalUploadData {
  headers?: Record<string, string>
  data?: Record<string, unknown>
}

/**
 * 获取上传请求头配置
 */
export const useUploadHeaders = () => {
  return computed(() => ({
    token: localStorage.getItem('token') || ''
  }))
}

/**
 * 获取完整的上传配置
 */
export const getUploadConfig = () => {
  return {
    headers: useUploadHeaders().value,
    withCredentials: true,
    timeout: 30000 // 30秒超时
  }
}

/**
 * 构建带token的完整请求配置
 */
export const buildUploadOptions = (additionalData: AdditionalUploadData = {}) => {
  return {
    headers: {
      token: localStorage.getItem('token') || '',
      ...additionalData.headers
    },
    data: {
      ...additionalData.data
    },
    withCredentials: true,
    timeout: 30000
  }
}

/**
 * 检查token是否存在
 */
export const hasValidToken = () => {
  return !!localStorage.getItem('token')
}

/**
 * 获取上传URL
 */
export const getUploadUrl = (endpoint: string) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${baseUrl}${endpoint}`
}
