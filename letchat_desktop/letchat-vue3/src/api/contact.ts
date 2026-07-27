import request from '@/utils/request'
import type {
  ApiResponse,
  BackendContact,
  FriendApply,
  GroupInfo,
  GroupInfoDetail,
  PaginationResult,
  SearchResult,
  UserInfo,
} from '@/types/api'

export const contactApi = {
  loadContact(contactType: 'U' | 'G') {
    return request.post<unknown, ApiResponse<BackendContact[]>>('/contact/loadContact', null, {
      params: { contactType },
    })
  },

  searchContact(contactId: string) {
    return request.post<unknown, ApiResponse<SearchResult>>('/contact/search', null, {
      params: { contactId },
    })
  },

  applyAdd(contactId: string, applyInfo = '') {
    return request.post<unknown, ApiResponse<number>>('/contact/applyAdd', null, {
      params: { contactId, applyInfo },
    })
  },

  loadApply(pageNo = 1) {
    return request.post<unknown, ApiResponse<PaginationResult<FriendApply>>>('/contact/loadApply', null, {
      params: { pageNo },
    })
  },

  dealWithApply(applyId: number, status: number) {
    return request.post<unknown, ApiResponse<null>>('/contact/dealWithApply', null, {
      params: { applyId, status },
    })
  },

  getContactInfo(contactId: string) {
    return request.post<unknown, ApiResponse<UserInfo>>('/contact/getContactInfo', null, {
      params: { contactId },
    })
  },

  getContactUserInfo(contactId: string) {
    return request.post<unknown, ApiResponse<UserInfo>>('/contact/getContactUserInfo', null, {
      params: { contactId },
    })
  },

  deleteContact(contactId: string) {
    return request.post<unknown, ApiResponse<null>>('/contact/delContact', null, {
      params: { contactId },
    })
  },

  addContactToBlacklist(contactId: string) {
    return request.post<unknown, ApiResponse<null>>('/contact/addContact2BlackList', null, {
      params: { contactId },
    })
  },

  getGroupInfo(groupId: string) {
    return request.post<unknown, ApiResponse<GroupInfo>>('/group/getGroupInfo', null, {
      params: { groupId },
    })
  },

  getGroupInfo4chat(groupId: string) {
    return request.post<unknown, ApiResponse<GroupInfoDetail>>('/group/getGroupInfo4chat', null, {
      params: { groupId },
    })
  },

  saveGroup(formData: FormData) {
    return request.post<unknown, ApiResponse<null>>('/group/saveGroup', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  addOrRemoveGroupUser(groupId: string, selectContacts: string[], opType: 0 | 1) {
    return request.post<unknown, ApiResponse<null>>('/group/addOrRemoveGroupUser', null, {
      params: { groupId, selectContacts: selectContacts.join(','), opType },
    })
  },

  leaveGroup(groupId: string) {
    return request.post<unknown, ApiResponse<null>>('/group/leaveGroup', null, {
      params: { groupId },
    })
  },

  dissolutionGroup(groupId: string) {
    return request.post<unknown, ApiResponse<null>>('/group/dissolutionGroup', null, {
      params: { groupId },
    })
  },
}
