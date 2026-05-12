import axios from 'axios'
import { Message } from '@arco-design/web-vue'
import router from '@/router'

const request = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 30000
})

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 兼容两种返回格式：1) { code: 1, data: ... } 2) 直接返回数据对象
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 1) {
        return res.data
      }
      Message.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg || '请求失败'))
    }
    // 直接返回数据（没有 code 字段包装的情况）
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      // 只有不在登录页时才跳转
      if (!router.currentRoute.value.path.startsWith('/login')) {
        router.push('/login')
      }
    } else {
      Message.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
