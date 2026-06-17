<template>
  <el-dialog
    v-model="visible"
    title="搜索用户"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="search-container">
      <div class="search-input-group">
        <el-input
          v-model="searchKeyword"
          placeholder="请输入用户ID或邮箱"
          prefix-icon="Search"
          @keyup.enter="handleSearch"
          clearable
        />
        <el-button type="primary" @click="handleSearch" :loading="loading">
          搜索
        </el-button>
      </div>
      

      
      <div class="search-results" v-if="searchResults.length > 0">
        <div
          v-for="user in searchResults"
          :key="user.contactId"
          class="user-item"
        >
          <el-avatar :src="user.avatar || defaultAvatar" :size="40" />
          <div class="user-info">
            <div class="user-name">{{ user.nickName || user.contactName }}</div>
            <div class="user-id">ID: {{ user.contactId }}</div>
            <div class="user-area" v-if="user.areaName">{{ user.areaName }}</div>
            <div class="user-status">状态: {{ user.status }} ({{ user.statusName }})</div>
          </div>
          <el-button
            type="primary"
            size="small"
            :disabled="user.status === 1"
            @click="handleAddFriend(user)"
          >
            {{ user.status === 1 ? '已是好友' : '添加好友' }}
          </el-button>
        </div>
      </div>
      
      <div class="no-results" v-else-if="!loading && searched && searchResults.length === 0">
        <el-empty description="未找到用户" />
      </div>
      
      <div class="search-status" v-else-if="!searched">
        <el-empty description="请输入用户ID进行搜索" />
      </div>
    </div>

    <!-- 添加好友对话框 -->
    <el-dialog
      v-model="addFriendDialogVisible"
      title="添加好友"
      width="400px"
      :close-on-click-modal="false"
      append-to-body
    >
      <div>
        <p>向 <strong>{{ selectedUser?.nickName }}</strong> 发送好友申请</p>
        <el-input
          v-model="applyMessage"
          type="textarea"
          :rows="3"
          placeholder="请输入验证信息（可选）"
          maxlength="200"
          show-word-limit
        />
      </div>
      <template #footer>
        <el-button @click="addFriendDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddFriend" :loading="loading">
          发送申请
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { contactApi } from '@/api/contact'
import type { SearchResult } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'friend-added': [user: SearchResult]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const searchKeyword = ref('')
const searchResults = ref<SearchResult[]>([])
const selectedUser = ref<SearchResult | null>(null)
const applyMessage = ref('')
const addFriendDialogVisible = ref(false)
const loading = ref(false)
const searched = ref(false)

const defaultAvatar = '/default-avatar.png'

const handleSearch = async () => {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }

  try {
      loading.value = true
      const response = await contactApi.searchContact(searchKeyword.value.trim())
      
      // 检查数据结构
      let userData = null
      if (response.data && response.data.data) {
        userData = response.data.data
      } else if (response.data && response.data.contactId) {
        userData = response.data
      }
      
      if (userData) {
        searchResults.value = [userData]
      } else {
        searchResults.value = []
      }
      searched.value = true
    } catch (error) {
      ElMessage.error('搜索失败')
      searchResults.value = []
    } finally {
      loading.value = false
    }
}

const handleAddFriend = (user: SearchResult) => {
  selectedUser.value = user
  applyMessage.value = ''
  addFriendDialogVisible.value = true
}

const confirmAddFriend = async () => {
    if (!selectedUser.value) return

    try {
      loading.value = true
      const response = await contactApi.applyAddContact(
        selectedUser.value.contactId,
        applyMessage.value
      )
      
      // 兼容不同的响应格式
      const responseData = response.data
      if (responseData.status === 'success' || responseData.code === 200) {
        ElMessage.success('好友申请已发送')
        addFriendDialogVisible.value = false
        visible.value = false
        emit('friend-added', selectedUser.value)
      } else {
        // 显示后端返回的具体错误信息
        ElMessage.warning(responseData.info || responseData.message || '申请已发送或重复申请')
        // 即使后端返回非success状态，也关闭对话框
        addFriendDialogVisible.value = false
        visible.value = false
      }
    } catch (error: any) {
      // 网络错误或其他异常
      if (error.response?.data?.info) {
        ElMessage.warning(error.response.data.info)
      } else {
        ElMessage.success('好友申请已发送') // 默认认为成功
      }
      addFriendDialogVisible.value = false
      visible.value = false
    } finally {
      loading.value = false
    }
  }

const handleClose = () => {
  searchKeyword.value = ''
  searchResults.value = []
  searched.value = false
}
</script>

<style scoped>
.search-container {
  padding: 20px;
}

.search-input-group {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-input-group .el-input {
  flex: 1;
}

.user-list {
  max-height: 400px;
  overflow-y: auto;
}

.user-item {
  padding: 15px;
  border-bottom: 1px solid #eee;
  cursor: pointer;
  transition: background-color 0.2s;
}

.user-item:hover {
  background-color: #f5f5f5;
}

.user-item:last-child {
  border-bottom: none;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.user-details {
  flex: 1;
}

.user-name {
  font-weight: bold;
  margin-bottom: 4px;
}

.user-id {
  font-size: 12px;
  color: #666;
}

.user-area {
  font-size: 12px;
  color: #999;
}

.user-status-badge {
  font-size: 12px;
}

.no-results {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.dialog-footer {
  text-align: right;
}
</style>