<template>
  <el-dialog v-model="visible" title="好友申请" width="560px" @open="loadList">
    <div v-loading="loading" class="apply-list">
      <div v-for="item in list" :key="item.applyId" class="apply-row">
        <div class="lc-avatar apply-avatar">{{ (item.contactName || item.applyUserId).slice(0, 2).toUpperCase() }}</div>
        <div class="apply-main">
          <strong>{{ item.contactName || item.applyUserId }}</strong>
          <span>{{ item.applyInfo || '对方没有填写验证信息' }}</span>
          <small>{{ formatTime(item.lastApplyTime) }}</small>
        </div>
        <div class="actions">
          <template v-if="item.status === 0">
            <el-button size="small" type="primary" @click="deal(item.applyId, 1)">同意</el-button>
            <el-button size="small" @click="deal(item.applyId, 2)">拒绝</el-button>
          </template>
          <el-tag v-else :type="statusType(item.status)">{{ item.statusName || statusText(item.status) }}</el-tag>
        </div>
      </div>

      <el-empty v-if="!loading && list.length === 0" description="暂无好友申请" />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { contactApi } from '@/api/contact'
import type { FriendApply } from '@/types/api'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'apply-processed': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const list = ref<FriendApply[]>([])

async function loadList() {
  loading.value = true
  try {
    const response = await contactApi.loadApply(1)
    list.value = response.data?.list || []
  } finally {
    loading.value = false
  }
}

async function deal(applyId: number, status: number) {
  await contactApi.dealWithApply(applyId, status)
  ElMessage.success(status === 1 ? '已同意申请' : '已拒绝申请')
  await loadList()
  emit('apply-processed')
}

function statusText(status: number) {
  return ({ 1: '已同意', 2: '已拒绝', 3: '已拉黑' } as Record<number, string>)[status] || '已处理'
}

function statusType(status: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'info'
  return 'warning'
}

function formatTime(value?: number) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

watch(visible, (open) => {
  if (open) loadList()
})
</script>

<style scoped>
.apply-list {
  min-height: 180px;
}

.apply-row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--lc-line);
}

.apply-row:last-child {
  border-bottom: 0;
}

.apply-avatar {
  width: 46px;
  height: 46px;
  border-radius: 15px;
}

.apply-main {
  min-width: 0;
}

.apply-main strong,
.apply-main span,
.apply-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.apply-main strong {
  font-weight: 900;
}

.apply-main span {
  margin-top: 3px;
  color: var(--lc-muted);
}

.apply-main small {
  margin-top: 3px;
  color: var(--lc-soft);
}

.actions {
  display: flex;
  gap: 8px;
}
</style>
