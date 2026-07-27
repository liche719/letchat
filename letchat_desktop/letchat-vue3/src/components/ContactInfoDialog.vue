<template>
  <el-dialog
    v-model="visible"
    :title="isGroup ? '群聊详情' : '联系人详情'"
    width="500px"
    destroy-on-close
    @open="loadDetails"
  >
    <div v-if="contact" v-loading="loading" class="detail">
      <div class="hero">
        <div class="lc-avatar hero-avatar" :class="{ group: isGroup }">
          {{ contact.contactName.slice(0, 2).toUpperCase() }}
        </div>
        <h3>{{ contact.contactName }}</h3>
        <p v-if="isGroup">{{ groupDetail?.groupInfo.groupId }}</p>
        <p v-else>{{ contact.contactId }}</p>
      </div>

      <template v-if="isGroup">
        <div class="info-grid">
          <div><span>群成员</span><strong>{{ groupMembers.length || contact.memberCount || 0 }} 人</strong></div>
          <div><span>群主</span><strong>{{ ownerName }}</strong></div>
          <div class="wide"><span>群公告</span><strong>{{ groupDetail?.groupInfo.groupNotice || '暂无群公告' }}</strong></div>
        </div>

        <section class="members">
          <div class="section-title">
            <strong>群成员</strong>
            <span>{{ groupMembers.length }} 人</span>
          </div>
          <div v-if="groupMembers.length" class="member-list">
            <div v-for="member in groupMembers" :key="member.userId" class="member">
              <div class="lc-avatar member-avatar">{{ memberName(member).slice(0, 2).toUpperCase() }}</div>
              <span>{{ memberName(member) }}<em v-if="isOwner(member.userId)">群主</em></span>
              <button
                v-if="canManage && !isOwner(member.userId)"
                type="button"
                class="remove-member"
                @click="removeMember(member.userId, memberName(member))"
              >
                移出
              </button>
            </div>
          </div>
          <p v-else class="empty-members">暂时无法加载群成员。</p>
        </section>

        <div class="actions">
          <template v-if="canManage">
            <el-button @click="emit('edit-group', groupDetail?.groupInfo || null)">编辑群聊</el-button>
            <el-button @click="addMembers">添加成员</el-button>
            <el-button type="danger" plain @click="dissolveGroup">解散群聊</el-button>
          </template>
          <el-button v-else type="danger" plain @click="leaveGroup">退出群聊</el-button>
        </div>
      </template>

      <template v-else>
        <div class="info-grid">
          <div><span>类型</span><strong>好友</strong></div>
          <div><span>地区</span><strong>{{ userDetail?.areaName || '未设置' }}</strong></div>
          <div class="wide"><span>个性签名</span><strong>{{ userDetail?.personalSignature || '这个人还没有留下签名' }}</strong></div>
        </div>
        <div class="actions">
          <el-button @click="deleteContact">删除好友</el-button>
          <el-button type="danger" plain @click="blacklist">加入黑名单</el-button>
        </div>
      </template>
    </div>
  </el-dialog>

  <el-dialog v-model="memberPickerVisible" title="添加群成员" width="420px" append-to-body>
    <div class="picker-list">
      <label v-for="candidate in availableCandidates" :key="candidate.contactId" class="picker-item">
        <input v-model="selectedMemberIds" type="checkbox" :value="candidate.contactId" />
        <div class="lc-avatar member-avatar">{{ candidate.contactName.slice(0, 2).toUpperCase() }}</div>
        <span>{{ candidate.contactName }}</span>
      </label>
      <p v-if="!availableCandidates.length" class="empty-members">暂无可添加的好友</p>
    </div>
    <template #footer>
      <el-button @click="memberPickerVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedMemberIds.length" :loading="savingMembers" @click="confirmAddMembers">添加</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contactApi } from '@/api/contact'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import type { ContactInfo, GroupInfo, GroupInfoDetail, GroupMember, UserInfo } from '@/types/api'

const props = defineProps<{ modelValue: boolean; contact: ContactInfo | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  changed: []
  'edit-group': [group: GroupInfo | null]
}>()

const chatStore = useChatStore()
const userStore = useUserStore()
const visible = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const isGroup = computed(() => props.contact?.contactType === 'G')
const loading = ref(false)
const userDetail = ref<UserInfo | null>(null)
const groupDetail = ref<GroupInfoDetail | null>(null)
const groupMembers = computed(() => groupDetail.value?.userContactList || [])
const canManage = computed(() => groupDetail.value?.groupInfo.groupOwnerId === userStore.userInfo?.userId)
const availableCandidates = computed(() => chatStore.contacts.filter((item) => item.contactType === 'U' && !groupMembers.value.some((member) => member.userId === item.contactId)))
const memberPickerVisible = ref(false)
const selectedMemberIds = ref<string[]>([])
const savingMembers = ref(false)
const ownerName = computed(() => {
  const owner = groupMembers.value.find((member) => isOwner(member.userId))
  return owner ? memberName(owner) : groupDetail.value?.groupInfo.groupOwnerNickName || '群主'
})

function isOwner(userId: string) {
  return userId === groupDetail.value?.groupInfo.groupOwnerId
}

function memberName(member: GroupMember) {
  return member.contactName || member.userId
}

async function loadDetails() {
  if (!props.contact) return
  loading.value = true
  try {
    if (isGroup.value) {
      const response = await contactApi.getGroupInfo4chat(props.contact.contactId)
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

async function addMembers() {
  if (!availableCandidates.value.length) {
    ElMessage.info('暂无可添加的好友')
    return
  }
  selectedMemberIds.value = []
  memberPickerVisible.value = true
}

async function confirmAddMembers() {
  if (!props.contact || !selectedMemberIds.value.length) return
  savingMembers.value = true
  try {
    await contactApi.addOrRemoveGroupUser(props.contact.contactId, selectedMemberIds.value, 0)
    ElMessage.success('成员已添加')
    memberPickerVisible.value = false
    await loadDetails()
    emit('changed')
  } finally {
    savingMembers.value = false
  }
}

async function removeMember(userId: string, name: string) {
  if (!props.contact) return
  await ElMessageBox.confirm(`确定将“${name}”移出群聊吗？`, '移出成员', { type: 'warning' })
  await contactApi.addOrRemoveGroupUser(props.contact.contactId, [userId], 1)
  ElMessage.success('已移出成员')
  await loadDetails()
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

async function dissolveGroup() {
  if (!props.contact) return
  await ElMessageBox.confirm('解散后所有成员将无法继续使用这个群聊，确定解散吗？', '解散群聊', { type: 'warning', confirmButtonText: '确认解散' })
  await contactApi.dissolutionGroup(props.contact.contactId)
  ElMessage.success('群聊已解散')
  visible.value = false
  emit('changed')
}
</script>

<style scoped>
.detail { padding-top: 2px; }
.hero { display: grid; place-items: center; padding: 14px 0 24px; text-align: center; }
.hero-avatar { width: 72px; height: 72px; font-size: 20px; }
.hero-avatar.group { border-color: var(--lc-brass); background: var(--lc-brass); box-shadow: 0 0 0 5px var(--lc-brass-soft); }
.hero h3 { margin: 16px 0 3px; color: var(--lc-text); font-size: 22px; font-weight: 600; }
.hero p { margin: 0; color: var(--lc-muted); font-size: 12px; }
.info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid var(--lc-line); border-left: 1px solid var(--lc-line); border-radius: 14px; overflow: hidden; }
.info-grid div { min-height: 78px; padding: 13px; border-right: 1px solid var(--lc-line); border-bottom: 1px solid var(--lc-line); background: var(--lc-panel); }
.info-grid .wide { grid-column: 1 / -1; }
.info-grid span, .info-grid strong { display: block; }
.info-grid span, .section-title span { color: var(--lc-soft); font-size: 12px; }
.info-grid strong { margin-top: 8px; color: var(--lc-text); font-weight: 600; line-height: 1.55; word-break: break-word; }
.members { margin-top: 18px; }
.section-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; }
.section-title strong { color: var(--lc-text); font-size: 14px; }
.member-list { max-height: 190px; overflow-y: auto; border: 1px solid var(--lc-line); border-radius: 12px; }
.member { display: flex; align-items: center; gap: 9px; min-height: 47px; padding: 7px 10px; border-bottom: 1px solid var(--lc-line); }
.member:last-child { border-bottom: 0; }
.member-avatar { width: 30px; height: 30px; font-size: 10px; }
.member span { flex: 1; overflow: hidden; color: var(--lc-text); text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.member em { margin-left: 6px; color: var(--lc-accent); font-size: 11px; font-style: normal; }
.remove-member { border: 0; color: var(--lc-danger); background: transparent; cursor: pointer; font-size: 12px; }
.empty-members { margin: 0; color: var(--lc-muted); font-size: 13px; }
.picker-list { display: grid; max-height: 330px; overflow-y: auto; }
.picker-item { display: flex; align-items: center; gap: 10px; min-height: 50px; padding: 8px 4px; border-bottom: 1px solid var(--lc-line); color: var(--lc-text); cursor: pointer; }
.picker-item:last-child { border-bottom: 0; }
.picker-item input { accent-color: var(--lc-accent); }
.picker-item span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
@media (max-width: 420px) { .info-grid { grid-template-columns: 1fr; } .info-grid .wide { grid-column: auto; } }
</style>
