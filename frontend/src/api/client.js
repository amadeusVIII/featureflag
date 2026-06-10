import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_URL || '/api'

const apiClient = axios.create({
  baseURL: BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// REQUEST INTERCEPTOR — adds JWT token to every request automatically.
// This means we never have to manually add the Authorization header anywhere.
// The token is stored in localStorage under 'ff_token'.
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('ff_token')
    if (token) {
      // Bearer authentication: the server's JwtAuthFilter reads this header
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token is expired or invalid — force a fresh login
      localStorage.removeItem('ff_token')
      localStorage.removeItem('ff_user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default apiClient