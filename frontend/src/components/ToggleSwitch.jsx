// ToggleSwitch — the visual hero of the entire project.
// When an admin flips this toggle, the sequence is:
//   1. PATCH /admin/flags/{id}/toggle is called
//   2. Spring Boot writes the new state to PostgreSQL
//   3. FlagChangePublisher publishes to Redis "flag-updates" channel
//   4. ALL running instances receive the Pub/Sub message
//   5. ALL instances delete their Redis cache key
//   6. Next evaluation is a fresh DB read
//
// In an interview demo, you flip this and immediately show Redis Commander
// to prove the cache key disappeared on all instances simultaneously.

export function ToggleSwitch({ enabled, onChange, disabled = false, loading = false }) {
  return (
    <button
      role="switch"
      aria-checked={enabled}
      disabled={disabled || loading}
      onClick={() => onChange(!enabled)}
      className={`
        relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full
        border-2 border-transparent transition-colors duration-200 ease-in-out
        focus:outline-none focus:ring-2 focus:ring-primary-600 focus:ring-offset-2
        disabled:cursor-not-allowed disabled:opacity-50
        ${enabled ? 'bg-primary-600' : 'bg-slate-200'}
      `}
    >
      {/* The sliding knob */}
      <span
        aria-hidden="true"
        className={`
          toggle-knob pointer-events-none inline-block h-5 w-5 rounded-full
          bg-white shadow ring-0
          ${enabled ? 'translate-x-5' : 'translate-x-0'}
          ${loading ? 'opacity-60' : ''}
        `}
      />
    </button>
  )
}