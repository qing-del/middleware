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
    await fetchUserInfo()
    return user.value
  }

  async function fetchUserInfo() {
    if (!token.value) return null
    try {
      const userInfo = await authApi.getCurrentUser()
      setUser(userInfo)
      return userInfo
    } catch {
      logout()
      return null
    }
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
    router.push('/login')
  }

  return {
    token,
    user,
    isAuthenticated,
    isAdmin,
    login,
    adminLogin,
    fetchUserInfo,
    logout
  }
})
