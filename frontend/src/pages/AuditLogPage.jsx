import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Filter } from 'lucide-react'
import apiClient from '../api/client'

// Fetch global audit log — all entities, sorted by newest first
// The backend's AuditLogRepository returns all entries; we filter client-side.
// For a production system you'd add server-side filtering params to the API.
const fetchAllAudit = () =>
  apiClient.get('/v1/admin/audit').then((r) => r.data).catch(() => [])

const ACTION_COLORS = {
  CREATED: 'bg-emerald-50 text-emerald-700',
  UPDATED: 'bg-blue-50 text-blue-700',
  DELETED: 'bg-red-50 text-red-700',
  TOGGLED: 'bg-violet-50 text-violet-700',
}

export default function AuditLogPage() {
  const [filterAction, setFilterAction] = useState('ALL')
  const [filterEntity, setFilterEntity] = useState('ALL')

  const { data: entries = [], isLoading } = useQuery({
    queryKey: ['audit-global'],
    queryFn:  fetchAllAudit,
    // Audit log changes frequently — refresh every 30s
    refetchInterval: 30_000,
  })

  // Client-side filtering
  const filtered = entries.filter((e) => {
    const actionMatch  = filterAction === 'ALL' || e.action === filterAction
    const entityMatch  = filterEntity === 'ALL' || e.entityType === filterEntity
    return actionMatch && entityMatch
  })

  return (
    <div className="p-6 max-w-5xl mx-auto">

      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Audit Log</h1>
          <p className="text-slate-500 text-sm mt-0.5">
            Every flag and user change, in order.
          </p>
        </div>
        <span className="text-xs font-mono text-slate-400 bg-slate-100 px-2 py-1 rounded">
          Auto-refreshes every 30s
        </span>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3 mb-4">
        <Filter className="h-4 w-4 text-slate-400" />

        {/* Action filter */}
        <div className="flex rounded-lg border border-slate-200 bg-white overflow-hidden text-sm">
          {['ALL', 'CREATED', 'UPDATED', 'TOGGLED', 'DELETED'].map((a) => (
            <button
              key={a}
              onClick={() => setFilterAction(a)}
              className={`px-3 py-1.5 border-r border-slate-200 last:border-0
                          transition-colors duration-150
                          ${filterAction === a
                            ? 'bg-primary-600 text-white font-medium'
                            : 'text-slate-600 hover:bg-slate-50'
                          }`}
            >
              {a}
            </button>
          ))}
        </div>

        {/* Entity filter */}
        <div className="flex rounded-lg border border-slate-200 bg-white overflow-hidden text-sm">
          {['ALL', 'FLAG', 'USER'].map((e) => (
            <button
              key={e}
              onClick={() => setFilterEntity(e)}
              className={`px-3 py-1.5 border-r border-slate-200 last:border-0
                          transition-colors duration-150
                          ${filterEntity === e
                            ? 'bg-slate-800 text-white font-medium'
                            : 'text-slate-600 hover:bg-slate-50'
                          }`}
            >
              {e}
            </button>
          ))}
        </div>

        <span className="text-sm text-slate-400 ml-auto">
          {filtered.length} entries
        </span>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading && (
          <div className="p-8 text-center text-slate-400 text-sm animate-pulse">
            Loading audit log…
          </div>
        )}

        {!isLoading && filtered.length === 0 && (
          <div className="p-8 text-center text-slate-400 text-sm">
            No entries match the current filter.
          </div>
        )}

        {!isLoading && filtered.length > 0 && (
          <>
            {/* Table header */}
            <div className="grid grid-cols-[auto_auto_1fr_1fr_auto] gap-4 px-5 py-2.5
                            bg-slate-50 border-b border-slate-200 text-xs font-semibold
                            text-slate-500 uppercase tracking-wider">
              <span>Action</span>
              <span>Type</span>
              <span>Entity ID</span>
              <span>Changed By</span>
              <span className="text-right">When</span>
            </div>

            {/* Rows */}
            <div className="divide-y divide-slate-100">
              {filtered.map((entry, idx) => (
                <div
                  key={entry.id}
                  className="grid grid-cols-[auto_auto_1fr_1fr_auto] gap-4 items-center
                             px-5 py-3 hover:bg-slate-50 transition-colors duration-100
                             fade-in-up"
                  style={{ animationDelay: `${Math.min(idx, 10) * 20}ms` }}
                >
                  {/* Action */}
                  <span className={`
                    text-xs font-mono font-medium px-2 py-0.5 rounded whitespace-nowrap
                    ${ACTION_COLORS[entry.action] ?? 'bg-slate-100 text-slate-600'}
                  `}>
                    {entry.action}
                  </span>

                  {/* Entity type */}
                  <span className="text-xs font-mono text-slate-500 bg-slate-100
                                   px-2 py-0.5 rounded whitespace-nowrap">
                    {entry.entityType}
                  </span>

                  {/* Entity ID — links to flag detail if type is FLAG */}
                  {entry.entityType === 'FLAG' ? (
                    <Link
                      to={`/flags/${entry.entityId}`}
                      className="text-xs font-mono text-primary-600 hover:text-primary-700
                                 truncate"
                    >
                      {entry.entityId}
                    </Link>
                  ) : (
                    <span className="text-xs font-mono text-slate-400 truncate">
                      {entry.entityId}
                    </span>
                  )}

                  {/* Changed by */}
                  <span className="text-xs font-mono text-slate-500 truncate">
                    {entry.changedBy}
                  </span>

                  {/* Timestamp */}
                  <span className="text-xs font-mono text-slate-400 text-right whitespace-nowrap">
                    {new Date(entry.createdAt).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}