import { useState, useCallback } from 'react'

// useAuth manages authentication state.
// We store the JWT token and user info in localStorage so it survives
// page refreshes. In a production system you'd use HttpOnly cookies,
// but for a portfolio project localStorage is acceptable.
export function useAuth() {
  // Read initial state from localStorage on first render
  const [token, setToken] = useState(() => localStorage.getItem('ff_token'))
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('ff_user')
    return stored ? JSON.parse(stored) : null
  })

  // Called after a successful POST /auth/login
  // Stores the token and user info, which triggers a re-render
  const login = useCallback((authResponse) => {
    const { accessToken, email, role } = authResponse
    localStorage.setItem('ff_token', accessToken)
    localStorage.setItem('ff_user', JSON.stringify({ email, role }))
    setToken(accessToken)
    setUser({ email, role })
  }, [])

  // Clears everything and redirects to login
  const logout = useCallback(() => {
    localStorage.removeItem('ff_token')
    localStorage.removeItem('ff_user')
    setToken(null)
    setUser(null)
  }, [])

  return {
    token,
    user,
    isAuthenticated: !!token,
    isAdmin: user?.role === 'ADMIN',
    login,
    logout,
  }
}