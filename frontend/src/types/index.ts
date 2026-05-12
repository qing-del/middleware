export interface User {
  id: number
  username: string
  nickname: string
  email?: string
  roleId: number
}

export interface LoginData {
  username: string
  password: string
}

export interface MenuItem {
  id: string
  label: string
  icon: string
  active?: boolean
}

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}
