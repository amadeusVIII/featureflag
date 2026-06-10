import { Navigate, useLocation } from 'react-router-dom'

// ProtectedRoute wraps any page that requires authentication.
// If the user has no token, we redirect them to /login and remember
// where they were trying to go (state.from) so we can send them back
// after a successful login.
export function ProtectedRoute({ children }) {
  const token = localStorage.getItem('ff_token')
  const location = useLocation()

  if (!token) {
    // state.from lets LoginPage redirect back here after successful login
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return children
}