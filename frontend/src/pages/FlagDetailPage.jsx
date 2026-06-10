import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Play, Save, Trash2, AlertTriangle } from 'lucide-react'
import apiClient from '../api/client'
import { ToggleSwitch } from '../components/ToggleSwitch'
import { CacheBadge } from '../components/CacheBadge'

// ─── API helpers ──────────────────────────────────────────────────────────────
const fetchFlag      = (id) => apiClient.get(`/v1/admin/flags/${id}`).then((r) => r.data)
const fetchAuditLog  = (id) => apiClient.get(`/v1/admin/flags/${id}/audit`).then((r) => r.data)
const updateFlag     = ({ id, data }) => apiClient.put(`/v1/admin/flags/${id}`, data).then((r) => r.data)
const toggleFlag     = (id) => apiClient.patch(`/v1/admin/flags/${id}/toggle`).then((r) => r.data)
const deleteFlag     = (id) => apiClient.delete(`/v1/admin/flags/${id}`)

// Evaluate endpoint — this one returns X-Cache-Status header
// We pass the full response through so we can read the header
const evaluateFlag = ({ flagKey, userId, environment }) =>
  apiClient.get('/v1/flags/evaluate', {
    params: { key: flagKey, userId, environment },
  })

// ─── FlagDetailPage ───────────────────────────────────────────────────────────
export default function FlagDetailPage() {
  const { id }       = useParams()
  const navigate     = useNavigate()
  const queryClient  = useQueryClient()

  // Active tab: 'detail' | 'audit'
  const [tab, setTab] = useState('detail')

  // Evaluate demo state
  const [evalUserId, setEvalUserId]       = useState('user-123')
  const [evalResult, setEvalResult]       = useState(null)
  const [evalLoading, setEvalLoading]     = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  // Fetch the flag
  const { data: flag, isLoading } = useQuery({
    queryKey: ['flag', id],
    queryFn: () => fetchFlag(id),
  })

  // Fetch audit log (lazy — only runs when tab = 'audit')
  const { data: auditLog, isLoading: auditLoading } = useQuery({
    queryKey: ['audit', id],
    queryFn: () => fetchAuditLog(id),
    enabled: tab === 'audit',
  })

  // Edit form state — initialized from the flag when it loads
  const [editForm, setEditForm] = useState(null)

  // When flag data arrives, initialize the edit form
  if (flag && !editForm) {
    setEditForm({
      name:               flag.name,
      description:        flag.description || '',
      rolloutPercentage:  flag.rolloutPercentage,
    })
  }

  const updateMutation = useMutation({
    mutationFn: updateFlag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flag', id] })
      queryClient.invalidateQueries({ queryKey: ['flags'] })
    },
  })

  const toggleMutation = useMutation({
    mutationFn: toggleFlag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flag', id] })
      queryClient.invalidateQueries({ queryKey: ['flags'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteFlag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flags'] })
      navigate('/flags')
    },
  })

  // LIVE EVALUATE DEMO
  // This directly demonstrates the cache layer:
  // First call → MISS (shows amber badge, ~45ms)
  // Same call again → HIT (shows green badge, ~2ms)
  // Toggle the flag → next call is MISS again (cache invalidated!)
  const handleEvaluate = async () => {
    if (!flag) return
    setEvalLoading(true)
    try {
      const response = await evaluateFlag({
        flagKey:     flag.key,
        userId:      evalUserId,
        environment: flag.environment,
      })
      const cacheStatus = response.headers['x-cache-status'] || response.headers['X-Cache-Status']
      setEvalResult({
        ...response.data,
        cacheStatus: cacheStatus || 'MISS',
      })
    } catch (err) {
      setEvalResult({ error: err.message })
    } finally {
      setEvalLoading(false)
    }
  }

  if (isLoading) {
    return (
      <div className="p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-6 bg-slate-200 rounded w-48" />
          <div className="h-40 bg-slate-100 rounded-xl" />
        </div>
      </div>
    )
  }

  if (!flag) {
    return (
      <div className="p-6 text-center text-slate-500">
        Flag not found.{' '}
        <button onClick={() => navigate('/flags')} className="text-primary-600">
          Go back
        </button>
      </div>
    )
  }

  return (
    <div className="p-6 max-w-3xl mx-auto">

      {/* ── Header ── */}
      <div className="flex items-start justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/flags')}
            className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100
                       transition-colors duration-150"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-xl font-bold text-slate-900">{flag.name}</h1>
            <p className="text-slate-400 text-xs font-mono mt-0.5">{flag.key} · {flag.environment}</p>
          </div>
        </div>

        {/* Toggle — the demo centrepiece */}
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-500">
            {flag.enabled ? 'Enabled' : 'Disabled'}
          </span>
          <ToggleSwitch
            enabled={flag.enabled}
            loading={toggleMutation.isPending}
            onChange={() => toggleMutation.mutate(id)}
          />
        </div>
      </div>

      {/* ── Tabs ── */}
      <div className="flex gap-1 mb-5 border-b border-slate-200">
        {['detail', 'audit'].map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium capitalize border-b-2 -mb-px
                        transition-colors duration-150
                        ${tab === t
                          ? 'border-primary-600 text-primary-700'
                          : 'border-transparent text-slate-500 hover:text-slate-700'
                        }`}
          >
            {t}
          </button>
        ))}
      </div>

      {/* ─────────── DETAIL TAB ─────────── */}
      {tab === 'detail' && (
        <div className="space-y-5">

          {/* Edit form */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <h2 className="text-sm font-semibold text-slate-700 mb-4">Settings</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1.5">
                  Display Name
                </label>
                <input
                  type="text"
                  value={editForm?.name ?? ''}
                  onChange={(e) => setEditForm((f) => ({ ...f, name: e.target.value }))}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm
                             focus:outline-none focus:ring-2 focus:ring-primary-600
                             focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1.5">
                  Description
                </label>
                <textarea
                  value={editForm?.description ?? ''}
                  onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))}
                  rows={2}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm
                             resize-none focus:outline-none focus:ring-2 focus:ring-primary-600
                             focus:border-transparent"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-600 mb-1.5">
                  Rollout Percentage
                  <span className="ml-2 font-mono text-xs text-slate-400">
                    {editForm?.rolloutPercentage}%
                  </span>
                </label>
                <input
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={editForm?.rolloutPercentage ?? 0}
                  onChange={(e) => setEditForm((f) => ({ ...f, rolloutPercentage: Number(e.target.value) }))}
                  className="w-full accent-primary-600"
                />
              </div>
              <div className="flex justify-end">
                <button
                  onClick={() => updateMutation.mutate({ id, data: editForm })}
                  disabled={updateMutation.isPending}
                  className="inline-flex items-center gap-2 px-4 py-2 bg-primary-600
                             hover:bg-primary-700 text-white text-sm font-semibold
                             rounded-lg transition-colors duration-150
                             disabled:opacity-60"
                >
                  <Save className="h-4 w-4" />
                  {updateMutation.isPending ? 'Saving…' : 'Save Changes'}
                </button>
              </div>
            </div>
          </div>

          {/* ── LIVE EVALUATE DEMO ── */}
          {/* This is the most powerful interview demo section.
              Call it twice: first is MISS (amber), second is HIT (green).
              Then toggle the flag — next call is MISS again.
              This proves Pub/Sub invalidation is working. */}
          <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-5">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-sm font-semibold text-slate-700">Live Evaluate</h2>
                <p className="text-xs text-slate-400 mt-0.5">
                  Call twice to see MISS → HIT. Toggle the flag → MISS again.
                </p>
              </div>
            </div>

            <div className="flex gap-3 items-end">
              <div className="flex-1">
                <label className="block text-xs font-medium text-slate-600 mb-1.5">
                  User ID
                </label>
                <input
                  type="text"
                  value={evalUserId}
                  onChange={(e) => setEvalUserId(e.target.value)}
                  className="w-full px-3 py-2 rounded-lg border border-slate-300 text-sm
                             font-mono focus:outline-none focus:ring-2 focus:ring-primary-600
                             focus:border-transparent"
                />
              </div>
              <button
                onClick={handleEvaluate}
                disabled={evalLoading}
                className="inline-flex items-center gap-2 px-4 py-2 bg-emerald-600
                           hover:bg-emerald-700 text-white text-sm font-semibold
                           rounded-lg transition-colors duration-150
                           disabled:opacity-60 whitespace-nowrap"
              >
                <Play className="h-4 w-4" />
                {evalLoading ? 'Evaluating…' : 'Evaluate'}
              </button>
            </div>

            {/* Result display */}
            {evalResult && !evalResult.error && (
              <div className="mt-3 p-3 bg-slate-50 rounded-lg border border-slate-200">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    {/* enabled result */}
                    <span className={`
                      text-sm font-bold
                      ${evalResult.enabled ? 'text-emerald-600' : 'text-slate-500'}
                    `}>
                      {evalResult.enabled ? '✓ enabled' : '✗ disabled'}
                    </span>
                    {/* reason badge */}
                    <span className="text-xs font-mono bg-slate-200 px-2 py-0.5 rounded
                                     text-slate-600">
                      {evalResult.reason}
                    </span>
                  </div>
                  {/* THE CACHE BADGE — this is what makes caching visible */}
                  <CacheBadge
                    status={evalResult.cacheStatus}
                    latencyMs={evalResult.evaluationTimeMs}
                  />
                </div>
              </div>
            )}

            {evalResult?.error && (
              <p className="mt-2 text-xs text-red-600">{evalResult.error}</p>
            )}
          </div>

          {/* ── Danger zone ── */}
          <div className="bg-white rounded-xl border border-red-200 shadow-sm p-5">
            <h2 className="text-sm font-semibold text-red-700 mb-3">Danger Zone</h2>
            {!showDeleteConfirm ? (
              <button
                onClick={() => setShowDeleteConfirm(true)}
                className="inline-flex items-center gap-2 px-4 py-2 text-red-600
                           border border-red-200 hover:bg-red-50 text-sm font-medium
                           rounded-lg transition-colors duration-150"
              >
                <Trash2 className="h-4 w-4" />
                Delete Flag
              </button>
            ) : (
              <div className="flex items-center gap-3 p-3 bg-red-50 rounded-lg border
                              border-red-200">
                <AlertTriangle className="h-4 w-4 text-red-600 flex-shrink-0" />
                <p className="text-sm text-red-700 flex-1">
                  Delete <strong>{flag.key}</strong>? This cannot be undone.
                </p>
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className="text-sm text-slate-500 hover:text-slate-700 px-2"
                >
                  Cancel
                </button>
                <button
                  onClick={() => deleteMutation.mutate(id)}
                  disabled={deleteMutation.isPending}
                  className="px-3 py-1.5 bg-red-600 hover:bg-red-700 text-white
                             text-sm font-semibold rounded-lg transition-colors"
                >
                  {deleteMutation.isPending ? 'Deleting…' : 'Delete'}
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ─────────── AUDIT TAB ─────────── */}
      {tab === 'audit' && (
        <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
          {auditLoading ? (
            <div className="p-8 text-center text-slate-400 text-sm">Loading audit log…</div>
          ) : auditLog?.length === 0 ? (
            <div className="p-8 text-center text-slate-400 text-sm">No audit entries yet.</div>
          ) : (
            <div className="divide-y divide-slate-100">
              {/* Header */}
              <div className="grid grid-cols-[auto_1fr_auto] gap-4 px-5 py-2.5 bg-slate-50
                              text-xs font-semibold text-slate-500 uppercase tracking-wider">
                <span>Action</span>
                <span>Changed by</span>
                <span className="text-right">When</span>
              </div>

              {/* Rows */}
              {auditLog?.map((entry) => (
                <div key={entry.id}
                     className="grid grid-cols-[auto_1fr_auto] gap-4 items-center
                                px-5 py-3">
                  {/* Action badge */}
                  <span className={`
                    text-xs font-mono font-medium px-2 py-0.5 rounded
                    ${entry.action === 'CREATED' ? 'bg-emerald-50 text-emerald-700' :
                      entry.action === 'DELETED' ? 'bg-red-50 text-red-700'        :
                      entry.action === 'TOGGLED' ? 'bg-blue-50 text-blue-700'      :
                      'bg-slate-100 text-slate-600'}
                  `}>
                    {entry.action}
                  </span>

                  {/* Who changed it */}
                  <span className="text-xs text-slate-500 font-mono truncate">
                    {entry.changedBy}
                  </span>

                  {/* When */}
                  <span className="text-xs text-slate-400 font-mono text-right whitespace-nowrap">
                    {new Date(entry.createdAt).toLocaleString()}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}