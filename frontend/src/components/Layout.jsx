import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Flag, List, Plus, LogOut, Zap } from 'lucide-react'
import { useAuth } from '../hooks/useAuth'

// Layout is the persistent shell for all authenticated pages.
// It renders a dark sidebar on the left and the page content on the right.
// Using <Outlet /> from React Router means the active page renders in the
// content area without remounting the sidebar on every navigation.
export function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  // adminOnly: true means the item is hidden entirely for VIEWER role.
  // Hiding it here is a UX concern — the route itself is also guarded
  // in App.jsx so a VIEWER cannot reach /create by typing the URL directly.
  const navItems = [
    { to: '/flags',  icon: Flag,  label: 'Flags',    adminOnly: false },
    { to: '/create', icon: Plus,  label: 'New Flag', adminOnly: true  },
    { to: '/audit',  icon: List,  label: 'Audit Log',adminOnly: false },
  ].filter(item => !item.adminOnly || user?.role === 'ADMIN')

  return (
    <div className="flex h-screen bg-slate-50 overflow-hidden">

      {/* ──────────── SIDEBAR ──────────── */}
      <aside className="w-60 flex-shrink-0 bg-slate-900 flex flex-col">

        {/* Brand mark */}
        <div className="px-6 py-5 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div className="h-7 w-7 rounded-md bg-primary-600 flex items-center justify-center">
              <Zap className="h-4 w-4 text-white" />
            </div>
            <div>
              <p className="text-white font-semibold text-sm leading-tight">FeatureFlag</p>
              <p className="text-slate-400 text-xs font-mono">admin</p>
            </div>
          </div>
        </div>

        {/* Navigation links */}
        <nav className="flex-1 px-3 py-4 space-y-0.5">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `
                flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium
                transition-colors duration-150
                ${isActive
                  ? 'bg-slate-800 text-white'
                  : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
                }
              `}
            >
              <Icon className="h-4 w-4 flex-shrink-0" />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* User info + logout at bottom */}
        <div className="px-3 py-4 border-t border-slate-800">
          <div className="px-3 py-2 mb-1">
            <p className="text-slate-300 text-xs font-medium truncate">{user?.email}</p>
            <p className="text-slate-500 text-xs font-mono uppercase tracking-wider mt-0.5">{user?.role}</p>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-sm
                       text-slate-400 hover:text-slate-200 hover:bg-slate-800/50
                       transition-colors duration-150"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </div>
      </aside>

      {/* ──────────── MAIN CONTENT ──────────── */}
      <main className="flex-1 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  )
}