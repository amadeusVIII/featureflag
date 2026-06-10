import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Search, Plus, ChevronLeft, ChevronRight, Edit2 } from 'lucide-react'
import apiClient from '../api/client'
import { ToggleSwitch } from '../components/ToggleSwitch'

// ─── API helpers ──────────────────────────────────────────────────────────────
// These are plain functions that call the backend.
// React Query calls them and caches the results.

const fetchFlags = ({ environment, page, size }) =>
  apiClient
    .get('/v1/admin/flags', { params: { environment, page, size } })
    .then((res) => res.data)

const toggleFlag = (id) =>
  apiClient.patch(`/v1/admin/flags/${id}/toggle`).then((res) => res.data)

// ─── Environments ─────────────────────────────────────────────────────────────
const ENVIRONMENTS = ['production', 'staging', 'development']

// ─── FlagsPage ────────────────────────────────────────────────────────────────
export default function FlagsPage() {
  const queryClient = useQueryClient()

  // Local state for filter controls
  const [environment, setEnvironment] = useState('production')
  const [search, setSearch]           = useState('')
  const [page, setPage]               = useState(0)
  const PAGE_SIZE = 15

  // useQuery fetches the flag list and caches it.
  // The array [flags, environment, page] is the cache key — if environment or
  // page changes, React Query automatically refetches.
  const { data, isLoading, isError } = useQuery({
    queryKey: ['flags', environment, page],
    queryFn: () => fetchFlags({ environment, page, size: PAGE_SIZE }),
    // keepPreviousData: true means the old page is shown while the new one loads
    placeholderData: (prev) => prev,
  })

  // useMutation for toggling a flag.
  // onSuccess: we invalidate the cache key so the flag list refetches
  // automatically, showing the updated enabled/disabled state.
  const toggleMutation = useMutation({
    mutationFn: toggleFlag,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flags', environment] })
    },
  })

  // Client-side search filter (applied after the server query)
  const flags = data?.content ?? []
  const filteredFlags = search
    ? flags.filter(
        (f) =>
          f.key.toLowerCase().includes(search.toLowerCase()) ||
          f.name.toLowerCase().includes(search.toLowerCase())
      )
    : flags

  const totalPages = data?.totalPages ?? 1

  return (
    <div className="p-6 max-w-6xl mx-auto">

      {/* ── Page header ── */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Feature Flags</h1>
          <p className="text-slate-500 text-sm mt-0.5">
            {data?.totalElements ?? 0} flags in {environment}
          </p>
        </div>
        <Link
          to="/create"
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700
                     text-white text-sm font-semibold px-4 py-2.5 rounded-lg
                     transition-colors duration-150"
        >
          <Plus className="h-4 w-4" />
          New Flag
        </Link>
      </div>

      {/* ── Filter bar ── */}
      <div className="flex items-center gap-3 mb-4">
        {/* Environment tabs */}
        <div className="flex rounded-lg border border-slate-200 bg-white overflow-hidden">
          {ENVIRONMENTS.map((env) => (
            <button
              key={env}
              onClick={() => { setEnvironment(env); setPage(0) }}
              className={`
                px-3 py-1.5 text-sm font-medium border-r border-slate-200 last:border-0
                transition-colors duration-150
                ${environment === env
                  ? 'bg-primary-600 text-white'
                  : 'text-slate-600 hover:bg-slate-50'
                }
              `}
            >
              {env}
            </button>
          ))}
        </div>

        {/* Search input */}
        <div className="relative flex-1 max-w-xs">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search flags…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 rounded-lg border border-slate-200
                       text-sm text-slate-900 placeholder:text-slate-400 bg-white
                       focus:outline-none focus:ring-2 focus:ring-primary-600
                       focus:border-transparent"
          />
        </div>
      </div>

      {/* ── Flag table ── */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading && (
          // Skeleton loader while flags are being fetched
          <div className="divide-y divide-slate-100">
            {[...Array(5)].map((_, i) => (
              <div key={i} className="px-5 py-4 flex items-center gap-4 animate-pulse">
                <div className="h-4 bg-slate-100 rounded w-40" />
                <div className="h-4 bg-slate-100 rounded w-24 ml-auto" />
                <div className="h-5 w-10 bg-slate-100 rounded-full" />
              </div>
            ))}
          </div>
        )}

        {isError && (
          <div className="px-5 py-8 text-center text-red-600 text-sm">
            Failed to load flags. Is the API running?
          </div>
        )}

        {!isLoading && !isError && filteredFlags.length === 0 && (
          <div className="px-5 py-12 text-center text-slate-400 text-sm">
            No flags found. <Link to="/create" className="text-primary-600 font-medium">Create one?</Link>
          </div>
        )}

        {!isLoading && !isError && filteredFlags.length > 0 && (
          <>
            {/* Table header */}
            <div className="grid grid-cols-[1fr_auto_auto_auto_auto] gap-4 px-5 py-2.5
                            bg-slate-50 border-b border-slate-200 text-xs font-semibold
                            text-slate-500 uppercase tracking-wider">
              <span>Flag</span>
              <span className="text-right">Type</span>
              <span className="text-right">Rollout</span>
              <span className="text-right">Status</span>
              <span></span>
            </div>

            {/* Table rows */}
            <div className="divide-y divide-slate-100">
              {filteredFlags.map((flag, idx) => (
                <div
                  key={flag.id}
                  className="grid grid-cols-[1fr_auto_auto_auto_auto] gap-4
                             items-center px-5 py-3.5 hover:bg-slate-50
                             transition-colors duration-100 fade-in-up"
                  style={{ animationDelay: `${idx * 30}ms` }}
                >
                  {/* Key + name */}
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-slate-900 truncate">
                      {flag.name}
                    </p>
                    <p className="text-xs font-mono text-slate-400 mt-0.5 truncate">
                      {flag.key}
                    </p>
                  </div>

                  {/* Flag type badge */}
                  <span className="text-xs font-mono text-slate-500 bg-slate-100
                                   px-2 py-0.5 rounded">
                    {flag.flagType}
                  </span>

                  {/* Rollout percentage */}
                  <div className="text-right">
                    <span className="text-sm font-semibold text-slate-700 font-mono">
                      {flag.rolloutPercentage}%
                    </span>
                  </div>

                  {/* Toggle switch — THE DEMO MOMENT */}
                  {/* Flipping this triggers the full Pub/Sub cache invalidation chain */}
                  <ToggleSwitch
                    enabled={flag.enabled}
                    loading={toggleMutation.isPending &&
                             toggleMutation.variables === flag.id}
                    onChange={() => toggleMutation.mutate(flag.id)}
                  />

                  {/* Edit link */}
                  <Link
                    to={`/flags/${flag.id}`}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700
                               hover:bg-slate-100 transition-colors duration-150"
                    title="Edit flag"
                  >
                    <Edit2 className="h-3.5 w-3.5" />
                  </Link>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* ── Pagination ── */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-slate-500">
            Page {page + 1} of {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium
                         rounded-lg border border-slate-200 bg-white text-slate-700
                         hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed
                         transition-colors duration-150"
            >
              <ChevronLeft className="h-4 w-4" /> Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm font-medium
                         rounded-lg border border-slate-200 bg-white text-slate-700
                         hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed
                         transition-colors duration-150"
            >
              Next <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}