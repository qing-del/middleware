import request from '@/utils/request'
import type { User } from '@/types'

export const authApi = {
  login(data: { username: string; password: string }) {
    return request.post('/user/user/login', data)
  },
  adminLogin(data: { username: string; password: string }) {
    return request.post('/admin/user/login', data)
  },
  getCurrentUser(): Promise<User> {
    return request.get('/user/user/me')
  }
}
