export interface User {
  id?: number
  username: string
  nickname: string | null
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

export interface EmailSendParams {
  userId?: number
  roleId?: number
  subject: string
  body: string
  templateName?: string
}

export interface EmailResult {
  successCount: number
  failCount: number
  message: string
}

export interface EmailStatus {
  email: string | null
  username: string
  isActive: boolean
}
