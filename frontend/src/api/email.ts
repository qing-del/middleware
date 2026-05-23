import request from '@/utils/request'
import type { ApiResponse, EmailSendParams, EmailResult, EmailStatus } from '@/types'

export const emailApi = {
  sendCustomEmail(data: EmailSendParams): Promise<ApiResponse<EmailResult>> {
    return request.post('/admin/email/send', data)
  },

  resendActivation(): Promise<ApiResponse<string>> {
    return request.post('/user/email/resend-activation')
  },

  getEmailStatus(): Promise<ApiResponse<EmailStatus>> {
    return request.get('/user/email/status')
  },

  activateAccount(token: string): Promise<ApiResponse<string>> {
    return request.get(`/user/user/active/${token}`)
  },

  verifyCode(code: string): Promise<ApiResponse<string>> {
    return request.post('/user/user/active-code', { code })
  }
}
