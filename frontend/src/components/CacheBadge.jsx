// CacheBadge displays the X-Cache-Status header value (HIT or MISS)
// and evaluation time from the last flag evaluation call.
//
// This makes the caching layer VISIBLE in the UI — an interviewer can
// see with their own eyes that the second call is served from cache.
// It is the single most impressive thing to show in a live demo.

export function CacheBadge({ status, latencyMs }) {
  if (!status) return null

  const isHit = status === 'HIT'

  return (
    <span className={`
      inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-mono font-medium
      ${isHit
        ? 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200'
        : 'bg-amber-50 text-amber-700 ring-1 ring-amber-200'
      }
    `}>
      {/* Pulsing dot: green for HIT (cache is warm), amber for MISS (DB query) */}
      <span className={`
        h-1.5 w-1.5 rounded-full pulse-dot
        ${isHit ? 'bg-emerald-500' : 'bg-amber-500'}
      `} />
      {status}
      {latencyMs !== undefined && (
        <span className="text-slate-500">{latencyMs}ms</span>
      )}
    </span>
  )
}