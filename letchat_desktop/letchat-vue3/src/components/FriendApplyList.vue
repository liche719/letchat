<template>
  <el-dialog
    v-model="visible"
    title="好友申请"
    width="500px"
    :close-on-click-modal="false"
  >
    <div class="apply-list">
      <!-- 调试信息 -->
      <div v-if="debugInfo" class="debug-info">
        <h4>调试信息：</h4>
        <pre>{{ debugInfo }}</pre>
      </div>
      
      <div v-if="applyList.length === 0" class="no-applies">
        <el-empty description="暂无好友申请" />
      </div>
      
      <div v-else class="apply-items">
        <div
          v-for="apply in applyList"
          :key="apply.applyId"
          class="apply-item"
        >
          <el-avatar :src="apply.applicantAvatar || defaultAvatar" :size="40" />
          <div class="apply-info">
            <div class="applicant-name">{{ apply.contactName || apply.applicantName || apply.applyUserId }}</div>
            <div class="apply-message" v-if="apply.applyInfo">
              {{ apply.applyInfo }}
            </div>
            <div class="apply-time">{{ formatTime(apply.lastApplyTime || apply.createTime) }}</div>
          </div>
          <div class="apply-actions">
            <el-button
              v-if="apply.status === 0"
              type="success"
              size="small"
              @click="handleAccept(apply)"
              :loading="loadingIds.includes(apply.applyId)"
            >
              同意
            </el-button>
            <el-button
              v-if="apply.status === 0"
              type="danger"
              size="small"
              @click="handleReject(apply)"
              :loading="loadingIds.includes(apply.applyId)"
            >
              拒绝
            </el-button>
            <el-tag v-if="apply.status === 1" type="success">已同意</el-tag>
            <el-tag v-if="apply.status === 2" type="danger">已拒绝</el-tag>
            <el-tag v-if="apply.status === 3" type="warning">已拉黑</el-tag>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { contactApi } from '@/api/contact'
import type { FriendApply } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'apply-processed': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const applyList = ref<FriendApply[]>([])
const loadingIds = ref<number[]>([])
const debugInfo = ref('')

const defaultAvatar = '/default-avatar.png'

const loadApplyList = async () => {
  try {
    debugInfo.value = '开始加载好友申请...'
    const response = await contactApi.loadApplyList(1)
    debugInfo.value += '\n完整响应: ' + JSON.stringify(response.data, null, 2)
    
    // 检查数据结构 - 兼容直接返回和嵌套返回
    let responseData = response.data
    let friendApplies: FriendApply[] = []
    
    // 情况1：直接返回分页对象
    if (responseData && responseData.list && Array.isArray(responseData.list)) {
      friendApplies = responseData.list
      debugInfo.value += '\n使用直接分页数据，长度: ' + friendApplies.length
    }
    // 情况2：标准API响应格式
    else if (responseData && responseData.data) {
      const data = responseData.data
      if (Array.isArray(data)) {
        friendApplies = data
        debugInfo.value += '\n使用标准数组数据，长度: ' + friendApplies.length
      } else if (data && data.list && Array.isArray(data.list)) {
        friendApplies = data.list
        debugInfo.value += '\n使用标准分页数据，长度: ' + friendApplies.length
      }
    }
    // 情况3：直接返回数组
    else if (Array.isArray(responseData)) {
      friendApplies = responseData
      debugInfo.value += '\n使用直接数组数据，长度: ' + friendApplies.length
    }
    
    debugInfo.value += '\n第一条数据: ' + JSON.stringify(friendApplies[0] || null, null, 2)
    applyList.value = friendApplies
    debugInfo.value += '\n最终列表长度: ' + applyList.value.length
  } catch (error: any) {
    debugInfo.value += '\n错误: ' + (error.message || error)
    ElMessage.error('加载好友申请失败')
    applyList.value = []
  }
}

const handleAccept = async (apply: FriendApply) => {
  loadingIds.value.push(apply.applyId)
  try {
    const response = await contactApi.dealWithApply(apply.applyId, 1)
    if (response.data.status === 'success') {
      ElMessage.success('已同意好友申请')
      apply.status = 1
      emit('apply-processed')
    } else {
      ElMessage.error(response.data.info || '操作失败')
    }
  } catch (error) {
    ElMessage.error('同意好友申请失败')
  } finally {
    loadingIds.value = loadingIds.value.filter(id => id !== apply.applyId)
  }
}

const handleReject = async (apply: FriendApply) => {
  loadingIds.value.push(apply.applyId)
  try {
    const response = await contactApi.dealWithApply(apply.applyId, 2)
    if (response.data.status === 'success') {
      ElMessage.success('已拒绝好友申请')
      apply.status = 2
    } else {
      ElMessage.error(response.data.info || '操作失败')
    }
  } catch (error) {
    ElMessage.error('拒绝好友申请失败')
  } finally {
    loadingIds.value = loadingIds.value.filter(id => id !== apply.applyId)
  }
}

const formatTime = (time: string | number | undefined) => {
  if (!time) return ''
  
  let date: Date
  if (typeof time === 'number') {
    // 处理时间戳
    date = new Date(time)
  } else {
    // 处理字符串时间
    date = new Date(time)
  }
  
  // 检查日期是否有效
  if (isNaN(date.getTime())) {
    return ''
  }
  
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  if (diff < 60000) {
    return '刚刚'
  } else if (diff < 3600000) {
    return Math.floor(diff / 60000) + '分钟前'
  } else if (diff < 86400000) {
    return Math.floor(diff / 3600000) + '小时前'
  } else {
    return date.toLocaleDateString()
  }
}

// 监听对话框打开事件
const handleOpen = () => {
  loadApplyList()
}

onMounted(() => {
  loadApplyList()
})

// 监听visible变化
watch(visible, (newVal) => {
  if (newVal) {
    loadApplyList()
  }
})
</script>

<style scoped>
.apply-list {
  max-height: 400px;
  overflow-y: auto;
}

.debug-info {
  background-color: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  margin-bottom: 10px;
  max-height: 200px;
  overflow-y: auto;
}

.debug-info h4 {
  margin: 0 0 5px 0;
  font-size: 12px;
  color: #666;
}

.debug-info pre {
  margin: 0;
  font-size: 11px;
  color: #333;
  white-space: pre-wrap;
}

.apply-item {
  display: flex;
  align-items: flex-start;
  padding: 15px;
  border-bottom: 1px solid #eee;
}

.apply-info {
  flex: 1;
  margin-left: 12px;
}

.applicant-name {
  font-weight: bold;
  font-size: 14px;
  margin-bottom: 4px;
}

.apply-message {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
  word-break: break-all;
}

.apply-time {
  font-size: 12px;
  color: #999;
}

.apply-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.no-applies {
  padding: 40px 0;
}
</style>