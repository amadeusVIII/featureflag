import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Layout }         from './components/Layout'
import { ProtectedRoute } from './components/ProtectedRoute'
import LoginPage          from './pages/LoginPage'
import FlagsPage          from './pages/FlagsPage'
import FlagDetailPage     from './pages/FlagDetailPage'
import CreateFlagPage     from './pages/CreateFlagPage'
import AuditLogPage       from './pages/AuditLogPage'

// AdminRoute wraps routes that only ADMIN users may access.
// If a VIEWER types /create directly in the address bar they land here —
// we redirect them to /flags rather than showing an error page.
// This is the second layer of protection; Layout hides the nav link (first layer).
function AdminRoute({ children }) {
  const user = (() => {
    try { return JSON.parse(localStorage.getItem('ff_user')) } catch { return null }
  })()

  if (user?.role !== 'ADMIN') {
    return <Navigate to="/flags" replace />
  }
  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>

        {/* Public route — no auth, no layout */}
        <Route path="/login" element={<LoginPage />} />

        {/* Authenticated routes — all wrapped in Layout */}
        <Route
          element={
            <ProtectedRoute>
              <Layout />
            </ProtectedRoute>
          }
        >
          {/* Default redirect: / → /flags */}
          <Route index element={<Navigate to="/flags" replace />} />

          {/* Flag list */}
          <Route path="flags"       element={<FlagsPage />} />

          {/* Flag detail / edit */}
          <Route path="flags/:id"   element={<FlagDetailPage />} />

          {/* Create new flag — ADMIN only */}
          <Route path="create" element={
            <AdminRoute>
              <CreateFlagPage />
            </AdminRoute>
          } />

          {/* Global audit log */}
          <Route path="audit"       element={<AuditLogPage />} />
        </Route>

        {/* Catch-all: any unknown URL → /flags */}
        <Route path="*" element={<Navigate to="/flags" replace />} />

      </Routes>
    </BrowserRouter>
  )
}