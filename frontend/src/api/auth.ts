import request from '@/utils/request'
import type { User } from '@/types'

export const authApi = {
  login(data: { username: string; password: string }) {
    return request.post('/user/user/login', data)
  },
  register(data: { username: string; password: string; confirmPassword: string; email: string }) {
    return request.post('/user/user/register', data)
  },
  adminLogin(data: { username: string; password: string }) {
    return request.post('/admin/user/login', data)
  },
  logout() {
    return request.post('/logout')
  },
  getCurrentUser(): Promise<User> {
    return request.get('/user/user/me')
  },
  // 管理端获取当前用户信息
  getCurrentAdminUser(): Promise<User> {
    return request.get('/admin/user/me')
  }
}
