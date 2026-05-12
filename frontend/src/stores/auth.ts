import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { User } from '@/types'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value ? user.value.roleId <= 2 : false)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUser(newUser: User) {
    user.value = newUser
  }

  async function login(credentials: { username: string; password: string }) {
    const res = await authApi.login(credentials)
    setToken(res as unknown as string)
    await fetchUserInfo()
    return user.value
  }

  async function adminLogin(credentials: { username: string; password: string }) {
    const res = await authApi.adminLogin(credentials)
    setToken(res as unknown as string)
    await fetchAdminUserInfo()
    return user.value
  }

  async function register(data: { username: string; password: string; confirmPassword: string; email: string }) {
    return await authApi.register(data)
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    try {
      const userInfo = await authApi.getCurrentUser()
      setUser(userInfo)
      return userInfo
    } catch (error) {
      // 只在 401 时自动 logout，其他错误抛出让调用方处理
      if (error?.response?.status === 401) {
        logout()
        return null
      }
      throw error
    }
  }

  async function fetchAdminUserInfo() {
    if (!token.value) return null
    try {
      const userInfo = await authApi.getCurrentAdminUser()
      setUser(userInfo)
      return userInfo
    } catch (error) {
      // 只在 401 时自动 logout，其他错误抛出让调用方处理
      if (error?.response?.status === 401) {
        logout()
        return null
      }
      throw error
    }
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    router.push('/login')
  }

  async function refreshCurrentUserInfo() {
    if (isAdmin.value) {
      return fetchAdminUserInfo()
    }
    return fetchUserInfo()
  }

  return {
    token,
    user,
    isAuthenticated,
    isAdmin,
    login,
    adminLogin,
    register,
    fetchUserInfo,
    fetchAdminUserInfo,
    refreshCurrentUserInfo,
    logout
  }
})
