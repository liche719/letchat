export interface ProcessedResponse<T = unknown> {
  success: boolean
  message: string
  data?: T
  code?: number
}

export function processResponse<T = unknown>(response: unknown): ProcessedResponse<T> {
  if (!response) {
    return { success: false, message: '服务端无响应' }
  }

  if (typeof response === 'object') {
    const record = response as Record<string, unknown>
    if (typeof record.status === 'string') {
      return {
        success: record.status === 'success',
        message: String(record.info || (record.status === 'success' ? '操作成功' : '操作失败')),
        data: record.data as T,
        code: typeof record.code === 'number' ? record.code : undefined,
      }
    }

    if (typeof record.success === 'boolean') {
      return {
        success: record.success,
        message: String(record.message || (record.success ? '操作成功' : '操作失败')),
        data: record.data as T,
        code: typeof record.code === 'number' ? record.code : undefined,
      }
    }

    return {
      success: true,
      message: '操作成功',
      data: response as T,
    }
  }

  return {
    success: true,
    message: String(response),
    data: response as T,
  }
}

export function getFriendlyErrorMessage(error: unknown): string {
  if (typeof error === 'string') return error
  if (error instanceof Error) return error.message

  const maybe = error as { response?: { status?: number; data?: { info?: string; message?: string } }; message?: string }
  return maybe.response?.data?.info || maybe.response?.data?.message || maybe.message || '操作失败，请稍后重试'
}

export async function handleApiResponse<T>(
  promise: Promise<unknown>,
  successMessage?: string,
): Promise<ProcessedResponse<T>> {
  try {
    const processed = processResponse<T>(await promise)
    if (processed.success && successMessage) {
      processed.message = successMessage
    }
    return processed
  } catch (error) {
    return {
      success: false,
      message: getFriendlyErrorMessage(error),
    }
  }
}
