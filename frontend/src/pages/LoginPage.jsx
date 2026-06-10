import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Zap, AlertCircle, CheckCircle } from 'lucide-react'
import apiClient from '../api/client'
import { useAuth } from '../hooks/useAuth'

export default function LoginPage() {
  // 'login' | 'register' — controls which form is shown
  const [tab, setTab]           = useState('login')

  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [success, setSuccess]   = useState('')
  const [loading, setLoading]   = useState(false)

  const { login } = useAuth()
  const navigate  = useNavigate()
  const location  = useLocation()

  const from = location.state?.from?.pathname || '/flags'

  // Switching tabs resets all form state so fields don't bleed across
  const switchTab = (next) => {
    setTab(next)
    setEmail('')
    setPassword('')
    setError('')
    setSuccess('')
  }

  const handleLogin = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await apiClient.post('/v1/auth/login', { email, password })
      login(response.data)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err.response?.status === 401
        ? 'Invalid email or password.'
        : 'Unable to reach the server. Is it running?')
    } finally {
      setLoading(false)
    }
  }

  const handleRegister = async (e) => {
    e.preventDefault()
    setError('')
    setSuccess('')
    setLoading(true)
    try {
      // POST /api/v1/auth/register — returns { accessToken, email, role }
      // Same shape as login, so we can log the user straight in after registering.
      const response = await apiClient.post('/v1/auth/register', { email, password })
      login(response.data)
      navigate('/flags', { replace: true })
    } catch (err) {
      if (err.response?.status === 409 || err.response?.status === 400) {
        // 409 = email already taken, 400 = validation failure (e.g. password too short)
        setError(err.response.data?.message || 'Registration failed. Check your inputs.')
      } else {
        setError('Unable to reach the server. Is it running?')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center px-4">
      <div className="w-full max-w-sm">

        {/* Brand */}
        <div className="text-center mb-8">
          <div className="inline-flex h-12 w-12 items-center justify-center
                          rounded-xl bg-primary-600 mb-4">
            <Zap className="h-6 w-6 text-white" />
          </div>
          <h1 className="text-2xl font-bold text-slate-900">FeatureFlag Admin</h1>
          <p className="text-slate-500 text-sm mt-1">
            {tab === 'login' ? 'Sign in to manage your flags' : 'Create a new account'}
          </p>
        </div>

        {/* Card */}
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">

          {/* Tab switcher */}
          <div className="flex border-b border-slate-200">
            {[
              { key: 'login',    label: 'Sign in'  },
              { key: 'register', label: 'Register' },
            ].map(({ key, label }) => (
              <button
                key={key}
                onClick={() => switchTab(key)}
                className={`flex-1 py-3 text-sm font-medium transition-colors duration-150
                  ${tab === key
                    ? 'text-primary-600 border-b-2 border-primary-600 bg-white'
                    : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
                  }`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="p-6">
            {/* Feedback banners */}
            {error && (
              <div className="flex items-center gap-2 p-3 bg-red-50 rounded-lg
                              text-red-700 text-sm border border-red-200 mb-4">
                <AlertCircle className="h-4 w-4 flex-shrink-0" />
                {error}
              </div>
            )}
            {success && (
              <div className="flex items-center gap-2 p-3 bg-emerald-50 rounded-lg
                              text-emerald-700 text-sm border border-emerald-200 mb-4">
                <CheckCircle className="h-4 w-4 flex-shrink-0" />
                {success}
              </div>
            )}

            <form onSubmit={tab === 'login' ? handleLogin : handleRegister}
                  className="space-y-4">

              {/* Email — shared by both forms */}
              <div>
                <label htmlFor="email"
                       className="block text-sm font-medium text-slate-700 mb-1.5">
                  Email
                </label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@example.com"
                  className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm
                             text-slate-900 placeholder:text-slate-400 bg-white
                             focus:outline-none focus:ring-2 focus:ring-primary-600
                             focus:border-transparent transition-shadow"
                />
              </div>

              {/* Password — shared by both forms */}
              <div>
                <label htmlFor="password"
                       className="block text-sm font-medium text-slate-700 mb-1.5">
                  Password
                  {tab === 'register' && (
                    // Hint so the user knows the 8-char minimum before submitting
                    <span className="ml-1.5 text-xs text-slate-400 font-normal">
                      (min. 8 characters)
                    </span>
                  )}
                </label>
                <input
                  id="password"
                  type="password"
                  autoComplete={tab === 'login' ? 'current-password' : 'new-password'}
                  required
                  minLength={tab === 'register' ? 8 : undefined}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm
                             text-slate-900 placeholder:text-slate-400 bg-white
                             focus:outline-none focus:ring-2 focus:ring-primary-600
                             focus:border-transparent transition-shadow"
                />
              </div>

              {/* Submit */}
              <button
                type="submit"
                disabled={loading}
                className="w-full bg-primary-600 hover:bg-primary-700 text-white
                           font-semibold text-sm py-2.5 rounded-lg
                           transition-colors duration-150
                           disabled:opacity-60 disabled:cursor-not-allowed
                           focus:outline-none focus:ring-2 focus:ring-primary-600
                           focus:ring-offset-2 mt-1"
              >
                {loading
                  ? (tab === 'login' ? 'Signing in…' : 'Creating account…')
                  : (tab === 'login' ? 'Sign in'     : 'Create account')
                }
              </button>
            </form>
          </div>
        </div>

      </div>
    </div>
  )
}