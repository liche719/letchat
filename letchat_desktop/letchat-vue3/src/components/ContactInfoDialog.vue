<template>
  <el-dialog
    v-model="visible"
    :title="contact?.contactType === 'G' ? '群组详情' : '联系人详情'"
    width="400px"
    destroy-on-close
  >
    <div class="contact-info" v-if="contact">
      <div class="avatar-section">
        <el-avatar :src="contact.avatar || defaultAvatar" :size="80" />
        <h3>{{ contact.contactName }}</h3>
      </div>
      
      <div class="info-section" v-if="contactDetails">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">
            {{ contact.contactId }}
          </el-descriptions-item>
          
          <template v-if="contact.contactType === 'U'">
            <el-descriptions-item label="邮箱">
              {{ contactDetails.email }}
            </el-descriptions-item>
            <el-descriptions-item label="昵称">
              {{ contactDetails.nickName }}
            </el-descriptions-item>
            <el-descriptions-item label="性别">
              {{ contactDetails.sex === 1 ? '男' : '女' }}
            </el-descriptions-item>
            <el-descriptions-item label="地区">
              {{ contactDetails.areaName || '未知' }}
            </el-descriptions-item>
            <el-descriptions-item label="个性签名">
              {{ contactDetails.personalSignature || '暂无' }}
            </el-descriptions-item>
          </template>
          
          <template v-else>
            <el-descriptions-item label="群公告">
              {{ contactDetails.groupNotice || '暂无' }}
            </el-descriptions-item>
            <el-descriptions-item label="成员数">
              {{ contactDetails.memberCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(contactDetails.createTime) }}
            </el-descriptions-item>
          </template>
        </el-descriptions>
      </div>
      
      <div class="actions" v-if="contact.contactType === 'U'">
        <el-button type="danger" @click="handleDeleteContact">
          删除好友
        </el-button>
        <el-button type="warning" @click="handleAddToBlacklist">
          加入黑名单
        </el-button>
      </div>
      
      <div class="actions" v-else>
        <el-button type="danger" @click="handleLeaveGroup">
          退出群聊
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import type { ContactInfo } from '@/types/api'

interface Props {
  modelValue: boolean
  contact: ContactInfo | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
const contactDetails = ref<any>(null)
const loading = ref(false)

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 获取联系人详情
  const loadContactDetails = async () => {
    if (!props.contact) return
    
    loading.value = true
    try {
      const endpoint = props.contact.contactType === 'U' 
        ? '/contact/getContactUserInfo' 
        : '/contact/getGroupInfo'
      
      const response = await request.post(endpoint, null, {
        params: { contactId: props.contact.contactId }
      })
      contactDetails.value = response.data
    } catch (error) {
      console.error('获取联系人详情失败:', error)
    } finally {
      loading.value = false
    }
  }

// 删除好友
const handleDeleteContact = async () => {
  if (!props.contact || props.contact.contactType !== 'U') return
  
  try {
    await ElMessageBox.confirm('确定要删除该好友吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await request.post('/contact/delContact', null, {
        params: { contactId: props.contact.contactId }
      })
    ElMessage.success('删除成功')
    visible.value = false
    // 通知父组件刷新联系人列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 加入黑名单
const handleAddToBlacklist = async () => {
  if (!props.contact || props.contact.contactType !== 'U') return
  
  try {
    await ElMessageBox.confirm('确定要将该好友加入黑名单吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await request.post('/contact/addContact2BlackList', null, {
        params: { contactId: props.contact.contactId }
      })
    ElMessage.success('加入黑名单成功')
    visible.value = false
    // 通知父组件刷新联系人列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

// 退出群聊
const handleLeaveGroup = async () => {
  if (!props.contact || props.contact.contactType !== 'G') return
  
  try {
    await ElMessageBox.confirm('确定要退出该群聊吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await request.post('/contact/leaveGroup', null, {
        params: { groupId: props.contact.contactId }
      })
    ElMessage.success('退出群聊成功')
    visible.value = false
    // 通知父组件刷新联系人列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('退出失败')
    }
  }
}

const formatDate = (date?: string) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

// 监听联系人变化，加载详情
watch(() => props.contact, (newContact) => {
  if (newContact && visible.value) {
    loadContactDetails()
  }
}, { immediate: true })
</script>

<style scoped>
.contact-info {
  text-align: center;
}

.avatar-section {
  margin-bottom: 20px;
}

.avatar-section h3 {
  margin: 10px 0 0;
  color: #333;
}

.info-section {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>