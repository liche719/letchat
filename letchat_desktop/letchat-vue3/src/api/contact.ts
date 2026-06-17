import request from '@/utils/request'
import type { Contact, Group, ApiResponse, FriendApply, SearchResult } from '@/types/api'

export const contactApi = {
  // 获取联系人列表
  getContactList: (contactType: string = '') => {
    return request.post<ApiResponse<Contact[]>>('/contact/loadContact', null, {
      params: { contactType }
    })
  },

  // 搜索联系人
  searchContact: (contactId: string) => {
    return request.post<ApiResponse<SearchResult>>('/contact/search', null, {
      params: { contactId }
    })
  },

  // 申请添加联系人
  applyAddContact: (contactId: string, applyInfo: string = '') => {
    return request.post<ApiResponse>('/contact/applyAdd', null, {
      params: { contactId, applyInfo }
    })
  },

  // 加载好友申请列表
  loadApplyList: (pageNo: number = 1) => {
    return request.post<ApiResponse<FriendApply[]>>('/contact/loadApply', null, {
      params: { pageNo }
    })
  },

  // 处理好友申请
  dealWithApply: (applyId: number, status: number) => {
    return request.post<ApiResponse>('/contact/dealWithApply', null, {
      params: { applyId, status }
    })
  },

  // 获取联系人信息
  getContactInfo: (contactId: string) => {
    return request.post<ApiResponse<Contact>>('/contact/getContactInfo', null, {
      params: { contactId }
    })
  },

  // 获取联系人用户信息
  getContactUserInfo: (contactId: string) => {
    return request.post<ApiResponse<Contact>>('/contact/getContactUserInfo', null, {
      params: { contactId }
    })
  },

  // 删除联系人
  delContact: (contactId: string) => {
    return request.post<ApiResponse>('/contact/delContact', null, {
      params: { contactId }
    })
  },

  // 加入黑名单
  addContactBlack: (contactId: string) => {
    return request.post<ApiResponse>('/contact/addContact2BlackList', null, {
      params: { contactId }
    })
  },

  // 移除黑名单
  removeContactBlack: (contactId: string) => {
    return request.post<ApiResponse>('/contact/removeContactBlack', null, {
      params: { contactId }
    })
  },

  // 获取群组列表
  getGroupList: () => {
    return request.post<ApiResponse<Group[]>>('/contact/loadContact', null, {
      params: { contactType: 'G' }
    })
  },

  // 创建群组
  createGroup: (groupName: string, groupNotice?: string) => {
    return request.post<ApiResponse>('/group/saveGroup', null, {
      params: { groupName, groupNotice }
    })
  },

  // 解散群组
  dissolutionGroup: (groupId: string) => {
    return request.post<ApiResponse>('/contact/dissolutionGroup', null, {
      params: { groupId }
    })
  },

  // 退出群组
  leaveGroup: (groupId: string) => {
    return request.post<ApiResponse>('/contact/leaveGroup', null, {
      params: { groupId }
    })
  }
}