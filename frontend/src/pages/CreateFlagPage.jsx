import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, AlertCircle } from 'lucide-react'
import apiClient from '../api/client'

// ─── API helper ───────────────────────────────────────────────────────────────
const createFlag = (data) =>
  apiClient.post('/v1/admin/flags', data).then((res) => res.data)

// ─── CreateFlagPage ───────────────────────────────────────────────────────────
export default function CreateFlagPage() {
  const navigate     = useNavigate()
  const queryClient  = useQueryClient()

  // Form state — mirrors the CreateFlagRequest DTO on the server
  const [form, setForm] = useState({
    key:                '',
    name:               '',
    description:        '',
    environment:        'production',
    enabled:            false,   // always start disabled — best practice
    rolloutPercentage:  0,
    flagType:           'BOOLEAN',
  })

  const [errors, setErrors] = useState({})

  // Validation runs before sending to the server.
  // We validate here AND the server validates with @Valid.
  // Two layers: client for UX, server for security.
  const validate = () => {
    const e = {}
    if (!form.key.trim())  e.key  = 'Key is required'
    if (!form.name.trim()) e.name = 'Name is required'

    // Key must be kebab-case — lowercase letters, numbers, hyphens
    if (form.key && !/^[a-z0-9-]+$/.test(form.key)) {
      e.key = 'Key must be lowercase letters, numbers, and hyphens only (e.g. dark-mode)'
    }

    if (form.rolloutPercentage < 0 || form.rolloutPercentage > 100) {
      e.rolloutPercentage = 'Must be between 0 and 100'
    }

    return e
  }

  const mutation = useMutation({
    mutationFn: createFlag,
    onSuccess: (newFlag) => {
      // Invalidate flag list cache so FlagsPage shows the new flag immediately
      queryClient.invalidateQueries({ queryKey: ['flags'] })
      // Navigate to the detail page of the new flag
      navigate(`/flags/${newFlag.id}`)
    },
  })

  const handleSubmit = (e) => {
    e.preventDefault()
    const validationErrors = validate()
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }
    setErrors({})
    mutation.mutate(form)
  }

  const set = (field) => (e) => {
    const value = e.target.type === 'checkbox' ? e.target.checked :
                  e.target.type === 'number'   ? Number(e.target.value) :
                  e.target.value
    setForm((prev) => ({ ...prev, [field]: value }))
    // Clear the error for this field as soon as the user types
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: undefined }))
  }

  return (
    <div className="p-6 max-w-2xl mx-auto">

      {/* Page header */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate(-1)}
          className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100
                     transition-colors duration-150"
        >
          <ArrowLeft className="h-5 w-5" />
        </button>
        <div>
          <h1 className="text-xl font-bold text-slate-900">New Feature Flag</h1>
          <p className="text-slate-500 text-sm mt-0.5">
            Flags always start disabled — flip them when ready.
          </p>
        </div>
      </div>

      {/* API error */}
      {mutation.isError && (
        <div className="flex items-center gap-2 p-3 mb-4 bg-red-50 rounded-lg
                        text-red-700 text-sm border border-red-200">
          <AlertCircle className="h-4 w-4 flex-shrink-0" />
          {mutation.error?.response?.data?.message || 'Failed to create flag.'}
        </div>
      )}

      {/* Form card */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm p-6">
        <form onSubmit={handleSubmit} className="space-y-5">

          {/* Flag key */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Flag Key <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={form.key}
              onChange={set('key')}
              placeholder="dark-mode"
              className={`w-full px-3 py-2.5 rounded-lg border text-sm font-mono
                          focus:outline-none focus:ring-2 focus:ring-primary-600
                          focus:border-transparent transition-shadow
                          ${errors.key ? 'border-red-400 bg-red-50' : 'border-slate-300 bg-white'}`}
            />
            {errors.key && (
              <p className="mt-1.5 text-xs text-red-600">{errors.key}</p>
            )}
            <p className="mt-1.5 text-xs text-slate-400">
              Lowercase, hyphens only. Used in SDK: <code className="font-mono">client.isEnabled("dark-mode", userId)</code>
            </p>
          </div>

          {/* Flag name */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Display Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={form.name}
              onChange={set('name')}
              placeholder="Dark Mode"
              className={`w-full px-3 py-2.5 rounded-lg border text-sm
                          focus:outline-none focus:ring-2 focus:ring-primary-600
                          focus:border-transparent transition-shadow
                          ${errors.name ? 'border-red-400 bg-red-50' : 'border-slate-300 bg-white'}`}
            />
            {errors.name && (
              <p className="mt-1.5 text-xs text-red-600">{errors.name}</p>
            )}
          </div>

          {/* Description */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Description
            </label>
            <textarea
              value={form.description}
              onChange={set('description')}
              placeholder="What does this flag control?"
              rows={2}
              className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm
                         bg-white focus:outline-none focus:ring-2 focus:ring-primary-600
                         focus:border-transparent transition-shadow resize-none"
            />
          </div>

          {/* Environment + Type row */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Environment
              </label>
              <select
                value={form.environment}
                onChange={set('environment')}
                className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm
                           bg-white focus:outline-none focus:ring-2 focus:ring-primary-600
                           focus:border-transparent"
              >
                <option value="production">production</option>
                <option value="staging">staging</option>
                <option value="development">development</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Flag Type
              </label>
              <select
                value={form.flagType}
                onChange={set('flagType')}
                className="w-full px-3 py-2.5 rounded-lg border border-slate-300 text-sm
                           bg-white focus:outline-none focus:ring-2 focus:ring-primary-600
                           focus:border-transparent"
              >
                <option value="BOOLEAN">BOOLEAN</option>
                <option value="STRING">STRING</option>
              </select>
            </div>
          </div>

          {/* Rollout percentage */}
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1.5">
              Rollout Percentage
              <span className="ml-2 text-xs font-mono font-normal text-slate-500">
                {form.rolloutPercentage}%
              </span>
            </label>
            <input
              type="range"
              min={0}
              max={100}
              step={5}
              value={form.rolloutPercentage}
              onChange={set('rolloutPercentage')}
              className="w-full accent-primary-600"
            />
            <div className="flex justify-between text-xs text-slate-400 mt-1">
              <span>0% (nobody)</span>
              <span>100% (everyone)</span>
            </div>
            {errors.rolloutPercentage && (
              <p className="mt-1 text-xs text-red-600">{errors.rolloutPercentage}</p>
            )}
          </div>

          {/* Start disabled checkbox */}
          <div className="flex items-start gap-3 p-3 bg-slate-50 rounded-lg border
                          border-slate-200">
            <input
              id="enabled"
              type="checkbox"
              checked={form.enabled}
              onChange={set('enabled')}
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-primary-600
                         focus:ring-primary-600"
            />
            <label htmlFor="enabled" className="text-sm text-slate-700">
              <span className="font-medium">Enable immediately</span>
              <p className="text-slate-500 text-xs mt-0.5">
                Best practice: deploy code first (disabled), then enable when ready.
              </p>
            </label>
          </div>

          {/* Actions */}
          <div className="flex items-center justify-end gap-3 pt-1">
            <button
              type="button"
              onClick={() => navigate(-1)}
              className="px-4 py-2 text-sm font-medium text-slate-700 rounded-lg
                         border border-slate-200 hover:bg-slate-50
                         transition-colors duration-150"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white
                         text-sm font-semibold rounded-lg transition-colors duration-150
                         disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {mutation.isPending ? 'Creating…' : 'Create Flag'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}