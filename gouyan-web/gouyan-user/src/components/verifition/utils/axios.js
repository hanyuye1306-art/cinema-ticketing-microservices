import axios from 'axios';

axios.defaults.baseURL = process.env.VUE_APP_API_BASE_URL || 'http://127.0.0.1:8181';

const service = axios.create({
  timeout: 40000,
  headers: {
    'X-Requested-With': 'XMLHttpRequest',
    'Content-Type': 'application/json; charset=UTF-8'
  },
})
service.interceptors.request.use(
  config => {
    return config
  },
  error => {
    Promise.reject(error)
  }
)

// response interceptor
service.interceptors.response.use(
  response => {
    const res = response.data;
    return res
  },
  error => {
    // Keep the captcha component's response contract on network/HTTP failures.
    return { repCode: 'SERVICE_UNAVAILABLE', repMsg: '图形验证服务暂不可用，请刷新重试' }
  }
)
export default service
