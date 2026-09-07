import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertTriangle,
  Bot,
  Check,
  CheckCircle2,
  ChevronDown,
  Eye,
  EyeOff,
  KeyRound,
  Network,
  Pencil,
  PlugZap,
  Plus,
  Server,
  ShieldCheck,
  Sparkles,
  Star,
  Trash2,
  X,
} from 'lucide-react'
import {
  createAiProviderConfig,
  deleteAiProviderConfig,
  getAiProviderConfigs,
  getAiProviderOptions,
  setDefaultAiProviderConfig,
  testAiProviderConnection,
  testAiProviderDraftConnection,
  updateAiProviderConfig,
} from '../api/aiProviderApi'
import { AI_CAPABILITY_LABELS, AI_CONFIG_LIMITS, AI_PROVIDER_OPTIONS, AI_PROXY_OPTIONS } from '../constants/aiProvider'
import { useLanguage } from '../i18n/LanguageContext'
import './ai-provider-settings.css'
import './project-module.css'

const DEFAULT_PROVIDER = AI_PROVIDER_OPTIONS[0]
const EMPTY_FORM = Object.freeze({
  name: '',
  providerType: DEFAULT_PROVIDER.value,
  baseUrl: DEFAULT_PROVIDER.baseUrl,
  defaultModel: DEFAULT_PROVIDER.model,
  apiKey: '',
  proxyType: 'NONE',
  proxyHost: '',
  proxyPort: '',
  proxyUsername: '',
  proxyPassword: '',
  removeProxyPassword: false,
  enabled: true,
  defaultConfig: false,
  remark: '',
})

/** 管理未来 AI 调用使用的多供应商连接配置。 */
function AiProviderSettings({ accessToken, isGuest }) {
  const { translate } = useLanguage()
  const [configs, setConfigs] = useState([])
  const [providerOptions, setProviderOptions] = useState(AI_PROVIDER_OPTIONS)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [editingConfig, setEditingConfig] = useState(undefined)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)
  const [testingConfigId, setTestingConfigId] = useState(null)
  const [editorTesting, setEditorTesting] = useState(false)
  const [editorConnectionResult, setEditorConnectionResult] = useState(null)
  const [connectionResults, setConnectionResults] = useState({})
  const [showApiKey, setShowApiKey] = useState(false)
  const [showProxyPassword, setShowProxyPassword] = useState(false)

  const providerLookup = useMemo(
    () => new Map(providerOptions.map((provider) => [provider.value, provider])),
    [providerOptions],
  )
  const proxyEnabled = form.proxyType !== 'NONE'
  const proxyPort = proxyPortValue(form.proxyPort)
  const proxyInvalid = Boolean(proxyEnabled && (
    !form.proxyHost.trim()
    || proxyPort === null
    || (!!form.proxyPassword && !form.proxyUsername.trim())
  ))

  const loadConfigs = useCallback(async () => {
    if (!accessToken || isGuest) {
      setConfigs([])
      setError(isGuest ? translate('aiConfig.guestDenied') : '')
      return
    }
    setLoading(true)
    setError('')
    try {
      const [options, savedConfigs] = await Promise.all([
        getAiProviderOptions(accessToken).then((items) => items.map(normalizeProviderOption)).catch(() => AI_PROVIDER_OPTIONS),
        getAiProviderConfigs(accessToken),
      ])
      setProviderOptions(options.length > 0 ? options : AI_PROVIDER_OPTIONS)
      setConfigs(savedConfigs)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }, [accessToken, isGuest, translate])

  useEffect(() => {
    loadConfigs()
  }, [loadConfigs])

  const openCreate = () => {
    const provider = providerOptions[0] ?? DEFAULT_PROVIDER
    setEditingConfig(null)
    setForm({
      ...EMPTY_FORM,
      providerType: provider.value,
      baseUrl: provider.baseUrl,
      defaultModel: provider.model,
      defaultConfig: configs.length === 0,
    })
    setError('')
    setFieldErrors({})
    setEditorConnectionResult(null)
    setShowApiKey(false)
    setShowProxyPassword(false)
  }

  const openEdit = (config) => {
    setEditingConfig(config)
    setForm({
      name: config.name,
      providerType: config.providerType,
      baseUrl: config.baseUrl,
      defaultModel: config.defaultModel ?? '',
      apiKey: config.apiKey ?? '',
      proxyType: config.proxyType ?? 'NONE',
      proxyHost: config.proxyHost ?? '',
      proxyPort: config.proxyPort ?? '',
      proxyUsername: config.proxyUsername ?? '',
      proxyPassword: '',
      removeProxyPassword: false,
      enabled: config.enabled,
      defaultConfig: config.defaultConfig,
      remark: config.remark ?? '',
    })
    setError('')
    setFieldErrors({})
    setEditorConnectionResult(null)
    setShowApiKey(false)
    setShowProxyPassword(false)
  }

  const closeEditor = () => {
    setEditingConfig(undefined)
    setForm(EMPTY_FORM)
    setError('')
    setFieldErrors({})
    setEditorConnectionResult(null)
    setShowApiKey(false)
    setShowProxyPassword(false)
  }

  const updateForm = (field, value) => {
    setForm((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => {
      if (!current[field]) return current
      const next = { ...current }
      delete next[field]
      return next
    })
  }

  const selectProvider = (providerType) => {
    const previousProvider = providerLookup.get(form.providerType)
    const nextProvider = providerLookup.get(providerType)
    const canApplyDefaultUrl = !form.baseUrl || form.baseUrl === previousProvider?.baseUrl
    const canApplyDefaultModel = !form.defaultModel || form.defaultModel === previousProvider?.model
    setForm((current) => ({
      ...current,
      providerType,
      baseUrl: canApplyDefaultUrl ? nextProvider?.baseUrl ?? '' : current.baseUrl,
      defaultModel: canApplyDefaultModel ? nextProvider?.model ?? '' : current.defaultModel,
    }))
  }

  const applyProviderPreset = (provider) => {
    setForm((current) => ({
      ...current,
      providerType: provider.value,
      name: current.name || provider.label,
      baseUrl: provider.baseUrl || current.baseUrl,
      defaultModel: provider.model || current.defaultModel,
    }))
  }

  const submitConfig = async (event) => {
    event.preventDefault()
    const nextFieldErrors = validateAiConfigForm(form, translate)
    if (Object.keys(nextFieldErrors).length > 0) {
      setFieldErrors(nextFieldErrors)
      return
    }
    setSaving(true)
    setError('')
    try {
      if (editingConfig) {
        await updateAiProviderConfig(accessToken, editingConfig.id, configPayload(form))
      } else {
        await createAiProviderConfig(accessToken, configPayload(form))
      }
      closeEditor()
      await loadConfigs()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  const removeConfig = async (config) => {
    if (!window.confirm(translate('aiConfig.deleteConfirm', { name: config.name }))) return
    setError('')
    try {
      await deleteAiProviderConfig(accessToken, config.id)
      await loadConfigs()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const makeDefault = async (configId) => {
    setError('')
    try {
      await setDefaultAiProviderConfig(accessToken, configId)
      await loadConfigs()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const toggleEnabled = async (config) => {
    if (config.defaultConfig) return
    setError('')
    try {
      await updateAiProviderConfig(accessToken, config.id, {
        name: config.name,
        providerType: config.providerType,
        baseUrl: config.baseUrl,
        defaultModel: config.defaultModel,
        apiKey: config.apiKey ?? '',
        proxyType: config.proxyType ?? 'NONE',
        proxyHost: config.proxyHost ?? '',
        proxyPort: config.proxyPort,
        proxyUsername: config.proxyUsername ?? '',
        proxyPassword: '',
        removeProxyPassword: false,
        enabled: !config.enabled,
        defaultConfig: false,
        remark: config.remark,
      })
      await loadConfigs()
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const testConnection = async (config) => {
    setTestingConfigId(config.id)
    setError('')
    try {
      const result = await testAiProviderConnection(accessToken, config.id)
      setConnectionResults((current) => ({ ...current, [config.id]: result }))
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setTestingConfigId(null)
    }
  }

  const testEditorConnection = async () => {
    setEditorTesting(true)
    setEditorConnectionResult(null)
    setError('')
    try {
      const result = await testAiProviderDraftConnection(accessToken, {
        configId: editingConfig?.id,
        providerType: form.providerType,
        baseUrl: form.baseUrl,
        defaultModel: form.defaultModel,
        apiKey: form.apiKey,
        proxyType: form.proxyType,
        proxyHost: form.proxyHost,
        proxyPort: proxyPortValue(form.proxyPort),
        proxyUsername: form.proxyUsername,
        proxyPassword: form.proxyPassword,
        removeProxyPassword: form.removeProxyPassword,
      })
      setEditorConnectionResult(result)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setEditorTesting(false)
    }
  }

  return (
    <section className="workspace-module" aria-labelledby="ai-config-title">
      <header className="workspace-heading ai-config-heading">
        <div>
          <span className="workspace-eyebrow">{translate('aiConfig.eyebrow')}</span>
          <h1 id="ai-config-title">{translate('aiConfig.title')}</h1>
          <p>{translate('aiConfig.description')}</p>
        </div>
        {!isGuest && (
          <button className="workspace-primary" type="button" onClick={openCreate}>
            <Plus size={17} />{translate('aiConfig.create')}
          </button>
        )}
      </header>

      <div className="ai-security-notice">
        <ShieldCheck size={19} />
        <div><strong>{translate('aiConfig.securityTitle')}</strong><p>{translate('aiConfig.securityHint')}</p></div>
      </div>

      {!isGuest && providerOptions.length > 0 && (
        <div className="ai-provider-strip" aria-label={translate('aiConfig.providerPresets')}>
          {providerOptions.slice(0, 7).map((provider) => (
            <span key={provider.value}>
              <Sparkles size={12} />
              {provider.label}
            </span>
          ))}
        </div>
      )}

      {error && <div className="workspace-alert">{error}</div>}
      {loading ? (
        <div className="workspace-state">{translate('common.loading')}</div>
      ) : configs.length === 0 ? (
        <div className="workspace-empty">
          <span><KeyRound size={28} /></span>
          <h2>{translate('aiConfig.empty')}</h2>
          <p>{translate(isGuest ? 'aiConfig.guestDenied' : 'aiConfig.emptyHint')}</p>
          {!isGuest && <button type="button" onClick={openCreate}><Plus size={16} />{translate('aiConfig.create')}</button>}
        </div>
      ) : (
        <div className="ai-config-grid">
          {configs.map((config) => {
            const provider = providerLookup.get(config.providerType) ?? normalizeProviderOption(config)
            const capabilities = config.capabilities?.length ? config.capabilities : provider.capabilities
            const result = connectionResults[config.id]
            return (
              <article className={config.defaultConfig ? 'ai-config-card default' : 'ai-config-card'} key={config.id}>
                <header>
                  <span className="ai-provider-icon"><Bot size={21} /></span>
                  <div>
                    <span>{config.providerLabel || provider.label}</span>
                    <h2>{config.name}</h2>
                  </div>
                  {config.defaultConfig && <span className="ai-default-badge"><Star size={11} fill="currentColor" />{translate('aiConfig.default')}</span>}
                </header>
                <p className="ai-provider-description">{config.providerDescription || provider.description}</p>
                <div className="ai-capability-row">
                  {capabilities.map((capability) => (
                    <span key={capability}>{capabilityLabel(capability)}</span>
                  ))}
                </div>
                <div className="ai-config-details">
                  <div><Server size={14} /><span><small>{translate('aiConfig.baseUrl')}</small><strong>{config.baseUrl}</strong></span></div>
                  <div><Bot size={14} /><span><small>{translate('aiConfig.model')}</small><strong>{config.defaultModel || config.recommendedModel || provider.model || translate('aiConfig.modelUnset')}</strong></span></div>
                  <div><KeyRound size={14} /><span><small>{translate('aiConfig.apiKey')}</small><strong>{config.hasApiKey ? config.apiKeyHint : translate(config.apiKeyRequired ? 'aiConfig.keyRequired' : 'aiConfig.keyUnset')}</strong></span></div>
                  <div><Network size={14} /><span><small>{translate('aiConfig.proxy.title')}</small><strong>{proxyLabel(config.proxyType, translate)}</strong></span></div>
                </div>
                {result && (
                  <div
                    className={result.reachable ? 'ai-test-result success' : 'ai-test-result failed'}
                    data-full-message={result.message}
                  >
                    {result.reachable ? <CheckCircle2 size={14} /> : <AlertTriangle size={14} />}
                    <span className="ai-test-message" title={result.message}>{result.message}</span>
                    <small>{result.httpStatus ? `HTTP ${result.httpStatus}` : `${result.durationMillis}ms`}</small>
                  </div>
                )}
                {config.remark && <p className="ai-config-remark">{config.remark}</p>}
                <footer>
                  <button
                    className={config.enabled ? 'ai-config-toggle enabled' : 'ai-config-toggle'}
                    type="button"
                    role="switch"
                    aria-checked={config.enabled}
                    disabled={config.defaultConfig}
                    onClick={() => toggleEnabled(config)}
                  ><span />{translate(config.enabled ? 'aiConfig.enabled' : 'aiConfig.disabled')}</button>
                  <div>
                    <button type="button" disabled={testingConfigId === config.id} onClick={() => testConnection(config)}>
                      <PlugZap size={14} />{translate(testingConfigId === config.id ? 'aiConfig.testing' : 'aiConfig.testConnection')}
                    </button>
                    {!config.defaultConfig && <button type="button" onClick={() => makeDefault(config.id)}><Star size={14} />{translate('aiConfig.setDefault')}</button>}
                    <button type="button" aria-label={translate('common.edit')} onClick={() => openEdit(config)}><Pencil size={14} /></button>
                    <button type="button" aria-label={translate('common.delete')} onClick={() => removeConfig(config)}><Trash2 size={14} /></button>
                  </div>
                </footer>
              </article>
            )
          })}
        </div>
      )}

      {editingConfig !== undefined && (
        <div className="workspace-modal-backdrop" role="presentation" onMouseDown={closeEditor}>
          <form className="workspace-modal ai-config-editor" onSubmit={submitConfig} noValidate onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div><span className="workspace-eyebrow">{translate('aiConfig.editorEyebrow')}</span><h2>{translate(editingConfig ? 'aiConfig.editTitle' : 'aiConfig.createTitle')}</h2></div>
              <button type="button" aria-label={translate('common.close')} onClick={closeEditor}><X size={19} /></button>
            </header>
            <div className="ai-provider-presets">
              {providerOptions.map((provider) => (
                <button
                  className={form.providerType === provider.value ? 'active' : ''}
                  key={provider.value}
                  type="button"
                  onClick={() => applyProviderPreset(provider)}
                >
                  <strong>{provider.label}</strong>
                  <small>{provider.description}</small>
                </button>
              ))}
            </div>
            <div className="workspace-form-grid">
              <label className="workspace-field">
                <span>{translate('aiConfig.field.name')}</span>
                <input maxLength={AI_CONFIG_LIMITS.name} value={form.name} onChange={(event) => updateForm('name', event.target.value)} />
                {fieldErrors.name && <small className="workspace-field-error">{fieldErrors.name}</small>}
              </label>
              <label className="workspace-field">
                <span>{translate('aiConfig.field.provider')}</span>
                <AiSelectMenu
                  value={form.providerType}
                  options={providerOptions.map((provider) => ({
                    value: provider.value,
                    label: provider.label,
                    description: provider.description,
                  }))}
                  onChange={selectProvider}
                />
              </label>
              <label className="workspace-field field-wide">
                <span>{translate('aiConfig.field.baseUrl')}</span>
                <input type="url" maxLength={AI_CONFIG_LIMITS.baseUrl} value={form.baseUrl} onChange={(event) => updateForm('baseUrl', event.target.value)} placeholder="https://api.example.com" />
                {fieldErrors.baseUrl && <small className="workspace-field-error">{fieldErrors.baseUrl}</small>}
              </label>
              <label className="workspace-field"><span>{translate('aiConfig.field.model')}</span><input maxLength={AI_CONFIG_LIMITS.model} value={form.defaultModel} onChange={(event) => setForm({ ...form, defaultModel: event.target.value })} placeholder={translate('aiConfig.modelPlaceholder')} /></label>
              <label className="workspace-field">
                <span>{translate('aiConfig.field.apiKey')}</span>
                <div className="ai-secret-input">
                  <input
                    type={editingConfig?.hasApiKey && form.apiKey && !showApiKey ? 'text' : showApiKey ? 'text' : 'password'}
                    autoComplete="new-password"
                    maxLength={AI_CONFIG_LIMITS.apiKey}
                    value={editingConfig?.hasApiKey && form.apiKey && !showApiKey ? maskApiKey(form.apiKey) : form.apiKey}
                    readOnly={Boolean(editingConfig?.hasApiKey && form.apiKey && !showApiKey)}
                    onChange={(event) => updateForm('apiKey', event.target.value)}
                    placeholder="sk-..."
                  />
                  <button
                    type="button"
                    aria-label={translate(showApiKey ? 'aiConfig.hideApiKey' : 'aiConfig.showApiKey')}
                    title={translate(showApiKey ? 'aiConfig.hideApiKey' : 'aiConfig.showApiKey')}
                    onClick={() => setShowApiKey((visible) => !visible)}
                  >
                    {showApiKey ? <EyeOff size={15} /> : <Eye size={15} />}
                  </button>
                </div>
              </label>
              <label className="workspace-field">
                <span className="ai-label-with-icon"><Network size={13} />{translate('aiConfig.proxy.title')}</span>
                <AiSelectMenu
                  value={form.proxyType}
                  options={AI_PROXY_OPTIONS.map((option) => ({
                    value: option.value,
                    label: translate(option.labelKey),
                  }))}
                  onChange={(proxyType) => setForm({ ...form, ...proxyDefaults(proxyType) })}
                />
              </label>
              {proxyEnabled && (
                <>
                  <label className="workspace-field">
                    <span>{translate('aiConfig.proxy.host')}</span>
                    <input maxLength={AI_CONFIG_LIMITS.proxyHost} value={form.proxyHost} onChange={(event) => setForm({ ...form, proxyHost: event.target.value })} placeholder="127.0.0.1" />
                  </label>
                  <label className="workspace-field">
                    <span>{translate('aiConfig.proxy.port')}</span>
                    <input type="number" min="1" max="65535" value={form.proxyPort} onChange={(event) => setForm({ ...form, proxyPort: event.target.value })} placeholder="7890" />
                  </label>
                  <label className="workspace-field">
                    <span>{translate('aiConfig.proxy.username')}</span>
                    <input maxLength={AI_CONFIG_LIMITS.proxyUsername} value={form.proxyUsername} onChange={(event) => setForm({ ...form, proxyUsername: event.target.value })} placeholder={translate('aiConfig.proxy.optional')} />
                  </label>
                  <label className="workspace-field">
                    <span>{translate('aiConfig.proxy.password')}</span>
                    <div className="ai-secret-input">
                      <input type={showProxyPassword ? 'text' : 'password'} autoComplete="new-password" maxLength={AI_CONFIG_LIMITS.proxyPassword} value={form.proxyPassword} disabled={form.removeProxyPassword} onChange={(event) => setForm({ ...form, proxyPassword: event.target.value })} placeholder={editingConfig?.hasProxyPassword ? translate('aiConfig.proxy.keepExistingPassword') : translate('aiConfig.proxy.optional')} />
                      <button
                        type="button"
                        aria-label={translate(showProxyPassword ? 'aiConfig.proxy.hidePassword' : 'aiConfig.proxy.showPassword')}
                        title={translate(showProxyPassword ? 'aiConfig.proxy.hidePassword' : 'aiConfig.proxy.showPassword')}
                        disabled={form.removeProxyPassword}
                        onClick={() => setShowProxyPassword((visible) => !visible)}
                      >
                        {showProxyPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                      </button>
                    </div>
                  </label>
                  {editingConfig?.hasProxyPassword && <label className="ai-clear-key field-wide"><input type="checkbox" checked={form.removeProxyPassword} onChange={(event) => setForm({ ...form, removeProxyPassword: event.target.checked, proxyPassword: '' })} /><span>{translate('aiConfig.proxy.removePassword')}</span></label>}
                  {proxyInvalid && <p className="ai-proxy-error field-wide">{translate('aiConfig.proxy.invalid')}</p>}
                </>
              )}
              <label className="workspace-field field-wide"><span>{translate('aiConfig.field.remark')}</span><textarea maxLength={AI_CONFIG_LIMITS.remark} rows="3" value={form.remark} onChange={(event) => setForm({ ...form, remark: event.target.value })} /></label>
              <label className="ai-form-switch"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span><strong>{translate('aiConfig.enabled')}</strong><small>{translate('aiConfig.enabledHint')}</small></span></label>
              <label className="ai-form-switch"><input type="checkbox" checked={form.defaultConfig} onChange={(event) => setForm({ ...form, defaultConfig: event.target.checked, enabled: event.target.checked || form.enabled })} /><span><strong>{translate('aiConfig.default')}</strong><small>{translate('aiConfig.defaultHint')}</small></span></label>
            </div>
            {editorConnectionResult && (
              <div
                className={editorConnectionResult.reachable ? 'ai-test-result ai-editor-test success' : 'ai-test-result ai-editor-test failed'}
                data-full-message={editorConnectionResult.message}
              >
                {editorConnectionResult.reachable ? <CheckCircle2 size={14} /> : <AlertTriangle size={14} />}
                <span className="ai-test-message" title={editorConnectionResult.message}>{editorConnectionResult.message}</span>
                <small>{editorConnectionResult.httpStatus ? `HTTP ${editorConnectionResult.httpStatus}` : `${editorConnectionResult.durationMillis}ms`}</small>
              </div>
            )}
            {error && <div className="workspace-alert">{error}</div>}
            <footer>
              <button type="button" onClick={closeEditor}>{translate('common.cancel')}</button>
              <button className="ai-test-button" type="button" disabled={editorTesting || saving || !form.baseUrl || proxyInvalid} onClick={testEditorConnection}>
                <PlugZap size={14} />{translate(editorTesting ? 'aiConfig.testing' : 'aiConfig.testConnection')}
              </button>
              <button className="workspace-primary" type="submit" disabled={saving || proxyInvalid}>{translate(saving ? 'common.saving' : 'common.save')}</button>
            </footer>
          </form>
        </div>
      )}
    </section>
  )
}

/** 在 AI 配置弹窗中提供与系统暗色风格一致的轻量下拉菜单。 */
function AiSelectMenu({ value, options, onChange, compact = false }) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef(null)
  const selectedOption = options.find((option) => option.value === value) ?? options[0]

  useEffect(() => {
    /** 点击控件外部时关闭下拉菜单。 */
    const closeOnOutsideClick = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [])

  /** 选择当前项并收起菜单。 */
  const selectOption = (nextValue) => {
    onChange(nextValue)
    setOpen(false)
  }

  return (
    <div className={compact ? 'ai-select compact' : 'ai-select'} ref={containerRef}>
      <button
        className="ai-select-button"
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <span>{selectedOption?.label}</span>
        <ChevronDown className={open ? 'open' : ''} size={14} />
      </button>
      {open && (
        <div className="ai-select-menu" role="menu">
          {options.map((option) => (
            <button
              className={option.value === value ? 'active' : ''}
              key={option.value}
              type="button"
              role="menuitemradio"
              aria-checked={option.value === value}
              onClick={() => selectOption(option.value)}
            >
              <span>
                <strong>{option.label}</strong>
                {option.description && <small>{option.description}</small>}
              </span>
              {option.value === value && <Check size={14} />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function normalizeProviderOption(provider) {
  return {
    value: provider.value ?? provider.providerType,
    label: provider.label ?? provider.providerType,
    baseUrl: provider.baseUrl ?? provider.defaultBaseUrl ?? '',
    model: provider.model ?? provider.recommendedModel ?? '',
    apiKeyRequired: Boolean(provider.apiKeyRequired),
    capabilities: provider.capabilities ?? [],
    description: provider.description ?? '',
  }
}

function configPayload(form) {
  const proxyEnabled = form.proxyType !== 'NONE'
  return {
    name: form.name,
    providerType: form.providerType,
    baseUrl: form.baseUrl,
    defaultModel: form.defaultModel,
    apiKey: form.apiKey,
    proxyType: form.proxyType,
    proxyHost: proxyEnabled ? form.proxyHost : '',
    proxyPort: proxyEnabled ? proxyPortValue(form.proxyPort) : null,
    proxyUsername: proxyEnabled ? form.proxyUsername : '',
    proxyPassword: proxyEnabled ? form.proxyPassword : '',
    removeProxyPassword: proxyEnabled ? form.removeProxyPassword : false,
    enabled: form.enabled,
    defaultConfig: form.defaultConfig,
    remark: form.remark,
  }
}

function proxyPortValue(value) {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const port = Number(value)
  return Number.isInteger(port) && port >= 1 && port <= 65535 ? port : null
}

function maskApiKey(value) {
  if (!value) return ''
  if (value.length <= 8) return `${value.slice(0, 2)}••••${value.slice(-2)}`
  return `${value.slice(0, 4)}••••${value.slice(-4)}`
}

function proxyDefaults(proxyType) {
  if (proxyType === 'NONE') {
    return {
      proxyType,
      proxyHost: '',
      proxyPort: '',
      proxyUsername: '',
      proxyPassword: '',
      removeProxyPassword: false,
    }
  }
  return { proxyType }
}

function proxyLabel(proxyType, translate) {
  const option = AI_PROXY_OPTIONS.find((item) => item.value === (proxyType ?? 'NONE'))
  return translate(option?.labelKey ?? 'aiConfig.proxy.none')
}

function capabilityLabel(capability) {
  return AI_CAPABILITY_LABELS[capability] ?? capability
}

function validateAiConfigForm(form, translate) {
  const errors = {}
  if (!form.name.trim()) {
    errors.name = translate('common.fieldRequired')
  }
  if (!form.baseUrl.trim()) {
    errors.baseUrl = translate('common.fieldRequired')
  }
  return errors
}

export default AiProviderSettings
