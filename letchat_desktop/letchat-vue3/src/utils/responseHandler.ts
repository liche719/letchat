/**
 * 响应处理工具类
 * 用于统一处理后端各种格式的响应
 */

import type { ApiResponse } from '@/types/api'

export interface ProcessedResponse<T = any> {
  success: boolean
  message: string
  data?: T
  code?: number
}

/**
 * 统一处理后端响应格式
 * 支持多种响应格式：
 * 1. 标准格式: { status: 'success'|'error', info: string, data: T }
 * 2. 简写格式: { success: true|false, message: string }
 * 3. 直接数据: 直接返回数据
 * 4. 嵌套格式: { data: { status: string, message: string, data: T } }
 */
export function processResponse<T = any>(response: any): ProcessedResponse<T> {
  try {
    // 处理空响应
    if (!response) {
      return {
        success: false,
        message: '服务器无响应',
      }
    }

    // 情况1: 标准格式 { status, info, data }
    if (response.status && typeof response.status === 'string') {
      return {
        success: response.status === 'success',
        message: response.info || (response.status === 'success' ? '操作成功' : '操作失败'),
        data: response.data,
        code: response.code,
      }
    }

    // 情况2: 简写格式 { success, message }
    if (typeof response.success === 'boolean') {
      return {
        success: response.success,
        message: response.message || (response.success ? '操作成功' : '操作失败'),
        data: response.data,
        code: response.code,
      }
    }

    // 情况3: 嵌套格式 { data: { status, message, data } }
    if (response.data && typeof response.data === 'object') {
      const innerData = response.data
      if (innerData.status && typeof innerData.status === 'string') {
        return {
          success: innerData.status === 'success',
          message: innerData.message || innerData.info || (innerData.status === 'success' ? '操作成功' : '操作失败'),
          data: innerData.data,
          code: innerData.code,
        }
      }
      
      // 如果嵌套的是直接数据
      return {
        success: true,
        message: '操作成功',
        data: innerData,
      }
    }

    // 情况4: 直接返回数据（视为成功）
    if (typeof response === 'object' && Object.keys(response).length > 0) {
      return {
        success: true,
        message: '操作成功',
        data: response,
      }
    }

    // 情况5: 字符串响应
    if (typeof response === 'string') {
      return {
        success: true,
        message: response,
        data: response,
      }
    }

    // 默认情况
    return {
      success: true,
      message: '操作成功',
      data: response,
    }
  } catch (error) {
    console.error('响应处理错误:', error)
    return {
      success: false,
      message: '响应格式错误',
    }
  }
}

/**
 * 获取友好的错误消息
 */
export function getFriendlyErrorMessage(error: any): string {
  if (typeof error === 'string') {
    return error
  }

  if (error?.response) {
    // HTTP错误
    const status = error.response.status
    const data = error.response.data

    if (data) {
      // 尝试从响应数据中提取错误信息
      if (typeof data === 'string') {
        return data
      }
      
      if (data.info) {
        return data.info
      }
      
      if (data.message) {
        return data.message
      }
      
      if (data.error) {
        return data.error
      }
    }

    // 标准HTTP状态码消息
    const statusMessages: Record<number, string> = {
      400: '请求参数错误',
      401: '未授权，请重新登录',
      403: '权限不足',
      404: '请求的资源不存在',
      500: '服务器内部错误',
      502: '网关错误',
      503: '服务暂时不可用',
    }

    return statusMessages[status] || `请求失败 (${status})`
  }

  if (error?.message) {
    return error.message
  }

  if (error?.request) {
    return '网络连接失败，请检查网络'
  }

  return '操作失败，请稍后重试'
}

/**
 * 处理API响应并提取有用信息
 */
export async function handleApiResponse<T>(
  promise: Promise<any>,
  successMessage?: string
): Promise<{ success: boolean; message: string; data?: T }> {
  try {
    const response = await promise
    const processed = processResponse<T>(response)

    if (processed.success && successMessage) {
      processed.message = successMessage
    }

    return processed
  } catch (error) {
    console.error('API调用错误:', error)
    return {
      success: false,
      message: getFriendlyErrorMessage(error),
    }
  }
}