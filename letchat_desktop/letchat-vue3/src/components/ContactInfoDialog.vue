<template>
  <el-dialog v-model="visible" :title="contact?.contactType === 'G' ? '群聊详情' : '联系人详情'" width="460px" destroy-on-close @open="loadDetails">
    <div v-if="contact" v-loading="loading" class="detail">
      <div class="hero">
        <div class="lc-avatar hero-avatar" :class="{ group: contact.contactType === 'G' }">
          {{ contact.contactName.slice(0, 2).toUpperCase() }}
        </div>
        <h3>{{ contact.contactName }}</h3>
        <p>{{ contact.contactId }}</p>
      </div>

      <div class="info-grid">
        <div>
          <span>类型</span>
          <strong>{{ contact.contactType === 'G' ? '群聊' : '好友' }}</strong>
        </div>
        <div>
          <span>{{ contact.contactType === 'G' ? '成员数' : '地区' }}</span>
          <strong>{{ contact.contactType === 'G' ? groupDetail?.memberCount || contact.memberCount || 0 : userDetail?.areaName || '未设置' }}</strong>
        </div>
        <div class="wide">
          <span>{{ contact.contactType === 'G' ? '公告' : '个性签名' }}</span>
          <strong>{{ contact.contactType === 'G' ? groupDetail?.groupNotice || '暂无公告' : userDetail?.personalSignature || '这个人还没有留下签名' }}</strong>
        </div>
      </div>

      <div class="actions">
        <template v-if="contact.contactType === 'U'">
          <el-button @click="deleteContact">删除好友</el-button>
          <el-button type="danger" plain @click="blacklist">加入黑名单</el-button>
        </template>
        <el-button v-else type="danger" plain @click="leaveGroup">退出群聊</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contactApi } from '@/api/contact'
import type { ContactInfo, GroupInfo, UserInfo } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
  contact: ContactInfo | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const userDetail = ref<UserInfo | null>(null)
const groupDetail = ref<GroupInfo | null>(null)

async function loadDetails() {
  if (!props.contact) return
  loading.value = true
  try {
    if (props.contact.contactType === 'G') {
      const response = await contactApi.getGroupInfo(props.contact.contactId)
      groupDetail.value = response.data
    } else {
      const response = await contactApi.getContactUserInfo(props.contact.contactId)
      userDetail.value = response.data
    }
  } finally {
    loading.value = false
  }
}

async function deleteContact() {
  if (!props.contact) return
  await ElMessageBox.confirm('确定删除这个好友吗？', '删除联系人', { type: 'warning' })
  await contactApi.deleteContact(props.contact.contactId)
  ElMessage.success('已删除好友')
  visible.value = false
  emit('changed')
}

async function blacklist() {
  if (!props.contact) return
  await ElMessageBox.confirm('确定把这个好友加入黑名单吗？', '加入黑名单', { type: 'warning' })
  await contactApi.addContactToBlacklist(props.contact.contactId)
  ElMessage.success('已加入黑名单')
  visible.value = false
  emit('changed')
}

async function leaveGroup() {
  if (!props.contact) return
  await ElMessageBox.confirm('确定退出这个群聊吗？', '退出群聊', { type: 'warning' })
  await contactApi.leaveGroup(props.contact.contactId)
  ElMessage.success('已退出群聊')
  visible.value = false
  emit('changed')
}
</script>

<style scoped>
.detail {
  padding: 4px 0 0;
}

.hero {
  display: grid;
  place-items: center;
  padding: 18px 0 22px;
  text-align: center;
}

.hero-avatar {
  width: 76px;
  height: 76px;
  border-radius: 24px;
  font-size: 22px;
}

.hero-avatar.group {
  background: linear-gradient(145deg, #253c47, #0f8f76);
}

.hero h3 {
  margin: 14px 0 4px;
  font-size: 22px;
  font-weight: 900;
}

.hero p {
  margin: 0;
  color: var(--lc-muted);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.info-grid div {
  min-height: 76px;
  padding: 12px;
  border: 1px solid var(--lc-line);
  border-radius: 14px;
  background: var(--lc-panel);
}

.info-grid .wide {
  grid-column: 1 / -1;
}

.info-grid span,
.info-grid strong {
  display: block;
}

.info-grid span {
  color: var(--lc-soft);
  font-size: 12px;
}

.info-grid strong {
  margin-top: 8px;
  font-weight: 850;
  word-break: break-word;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}
</style>
