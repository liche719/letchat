<template>
  <el-dialog v-model="visible" title="添加联系人" width="520px" destroy-on-close class="contact-search-dialog">
    <div class="search-panel">
      <div class="search-line">
        <el-input
          v-model="keyword"
          size="large"
          placeholder="输入用户 ID、群 ID 或邮箱"
          clearable
          :prefix-icon="Search"
          @keyup.enter="search"
        />
        <el-button type="primary" size="large" :loading="loading" @click="search">搜索</el-button>
      </div>

      <div v-if="result" class="result-row">
        <div class="lc-avatar result-avatar">{{ resultName.slice(0, 2).toUpperCase() }}</div>
        <div class="result-main">
          <strong>{{ resultName }}</strong>
          <span>{{ result.contactId }}</span>
          <small>{{ result.areaName || '未设置地区' }} · {{ result.statusName || statusLabel }}</small>
        </div>
        <el-button type="primary" plain :disabled="result.status === 1" @click="openApply">
          {{ result.status === 1 ? '已添加' : '申请' }}
        </el-button>
      </div>

      <div v-else-if="searched" class="empty-state compact">
        <div class="mini-icon"><Search /></div>
        <strong>没有找到联系人</strong>
        <span>请检查 ID 是否完整，群聊通常以 G 开头，用户通常以 U 开头。</span>
      </div>

      <div v-else class="empty-state">
        <div class="mini-icon"><Plus /></div>
        <strong>搜索用户或群聊</strong>
        <span>输入完整 ID 后可以发送好友申请或入群申请。</span>
      </div>
    </div>

    <el-dialog v-model="applyVisible" title="验证信息" width="420px" append-to-body>
      <el-input
        v-model="applyInfo"
        type="textarea"
        :rows="4"
        maxlength="120"
        show-word-limit
        placeholder="简单介绍一下你是谁，方便对方通过申请"
      />
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="submitApply">发送</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { contactApi } from '@/api/contact'
import type { SearchResult } from '@/types/api'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'friend-added': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const keyword = ref('')
const result = ref<SearchResult | null>(null)
const searched = ref(false)
const loading = ref(false)
const applyVisible = ref(false)
const applyInfo = ref('你好，我想添加你为联系人。')

const resultName = computed(() => result.value?.nickName || result.value?.contactName || result.value?.contactId || '')
const statusLabel = computed(() => (result.value?.status === 1 ? '已是联系人' : '可申请'))

async function search() {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  loading.value = true
  try {
    const response = await contactApi.searchContact(keyword.value.trim())
    result.value = response.data || null
    searched.value = true
  } finally {
    loading.value = false
  }
}

function openApply() {
  if (!result.value) return
  applyVisible.value = true
}

async function submitApply() {
  if (!result.value) return
  loading.value = true
  try {
    await contactApi.applyAdd(result.value.contactId, applyInfo.value)
    ElMessage.success('申请已发送')
    applyVisible.value = false
    visible.value = false
    emit('friend-added')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.search-panel {
  display: grid;
  gap: 16px;
}

.search-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px;
  gap: 10px;
}

.result-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 13px;
  padding: 14px;
  border: 1px solid var(--lc-line);
  border-radius: 12px;
  background: var(--lc-surface-subtle);
}

.result-avatar {
  width: 48px;
  height: 48px;
  border-radius: 14px;
}

.result-main {
  min-width: 0;
}

.result-main strong,
.result-main span,
.result-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-main strong {
  font-size: 15px;
  font-weight: 850;
}

.result-main span {
  margin-top: 3px;
  color: var(--lc-muted);
  font-size: 12px;
}

.result-main small {
  margin-top: 4px;
  color: var(--lc-soft);
}

.empty-state {
  display: grid;
  justify-items: center;
  gap: 8px;
  min-height: 148px;
  padding: 26px 24px;
  border: 1px dashed var(--lc-line-strong);
  border-radius: 12px;
  color: var(--lc-muted);
  text-align: center;
  background: linear-gradient(180deg, rgba(248, 250, 248, 0.82), rgba(255, 255, 255, 0.74));
}

.empty-state.compact {
  min-height: 126px;
}

.mini-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  color: var(--lc-accent);
  background: var(--lc-accent-soft);
}

.mini-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.empty-state strong {
  color: var(--lc-text);
  font-size: 14px;
  font-weight: 850;
}

.empty-state span {
  max-width: 300px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
