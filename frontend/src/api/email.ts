import request from '@/utils/request'
import type { EmailSendParams, EmailResult, EmailStatus } from '@/types'

export const emailApi = {
  sendCustomEmail(data: EmailSendParams): Promise<EmailResult> {
    return request.post('/admin/email/send', data)
  },

  resendActivation(): Promise<string> {
    return request.post('/user/email/resend-activation')
  },

  getEmailStatus(): Promise<EmailStatus> {
    return request.get('/user/email/status')
  },

  activateAccount(token: string): Promise<string> {
    return request.get(`/user/user/active/${token}`)
  },

  verifyCode(code: string): Promise<string> {
    return request.post('/user/user/active-code', { code })
  }
}
