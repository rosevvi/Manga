import { useCallback, useEffect, useState } from 'react'
import {
  Bot,
  KeyRound,
  Pencil,
  Plus,
  Server,
  ShieldCheck,
  Star,
  Trash2,
  X,
} from 'lucide-react'
import {
  createAiProviderConfig,
  deleteAiProviderConfig,
  getAiProviderConfigs,
  setDefaultAiProviderConfig,
  updateAiProviderConfig,
} from '../api/aiProviderApi'
import { AI_CONFIG_LIMITS, AI_PROVIDER_OPTIONS } from '../constants/aiProvider'
import { useLanguage } from '../i18n/LanguageContext'
import './ai-provider-settings.css'
import './project-module.css'

const DEFAULT_PROVIDER = AI_PROVIDER_OPTIONS[0]
const EMPTY_FORM = Object.freeze({
  name: '',
  providerType: DEFAULT_PROVIDER.value,
  baseUrl: DEFAULT_PROVIDER.baseUrl,
  defaultModel: '',
  apiKey: '',
  removeApiKey: false,
  enabled: true,
  defaultConfig: false,
  remark: '',
})

/** 管理未来 AI 调用使用的多供应商连接配置。 */
function AiProviderSettings({ accessToken, isGuest }) {
  const { translate } = useLanguage()
  const [configs, setConfigs] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [editingConfig, setEditingConfig] = useState(undefined)
  const [form, setForm] = useState(EMPTY_FORM)
  const [saving, setSaving] = useState(false)

  const loadConfigs = useCallback(async () => {
    if (!accessToken || isGuest) {
      setConfigs([])
      setError(isGuest ? translate('aiConfig.guestDenied') : '')
      return
    }
    setLoading(true)
    setError('')
    try {
      setConfigs(await getAiProviderConfigs(accessToken))
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
    setEditingConfig(null)
    setForm({ ...EMPTY_FORM, defaultConfig: configs.length === 0 })
    setError('')
  }

  const openEdit = (config) => {
    setEditingConfig(config)
    setForm({
      name: config.name,
      providerType: config.providerType,
      baseUrl: config.baseUrl,
      defaultModel: config.defaultModel ?? '',
      apiKey: '',
      removeApiKey: false,
      enabled: config.enabled,
      defaultConfig: config.defaultConfig,
      remark: config.remark ?? '',
    })
    setError('')
  }

  const closeEditor = () => {
    setEditingConfig(undefined)
    setForm(EMPTY_FORM)
    setError('')
  }

  const selectProvider = (providerType) => {
    const previousProvider = AI_PROVIDER_OPTIONS.find((option) => option.value === form.providerType)
    const nextProvider = AI_PROVIDER_OPTIONS.find((option) => option.value === providerType)
    const canApplyDefaultUrl = !form.baseUrl || form.baseUrl === previousProvider?.baseUrl
    setForm((current) => ({
      ...current,
      providerType,
      baseUrl: canApplyDefaultUrl ? nextProvider?.baseUrl ?? '' : current.baseUrl,
    }))
  }

  const submitConfig = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      if (editingConfig) {
        await updateAiProviderConfig(accessToken, editingConfig.id, form)
      } else {
        const createPayload = {
          name: form.name,
          providerType: form.providerType,
          baseUrl: form.baseUrl,
          defaultModel: form.defaultModel,
          apiKey: form.apiKey,
          enabled: form.enabled,
          defaultConfig: form.defaultConfig,
          remark: form.remark,
        }
        await createAiProviderConfig(accessToken, createPayload)
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
        apiKey: '',
        removeApiKey: false,
        enabled: !config.enabled,
        defaultConfig: false,
        remark: config.remark,
      })
      await loadConfigs()
    } catch (requestError) {
      setError(requestError.message)
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
          {configs.map((config) => (
            <article className={config.defaultConfig ? 'ai-config-card default' : 'ai-config-card'} key={config.id}>
              <header>
                <span className="ai-provider-icon"><Bot size={21} /></span>
                <div><span>{providerLabel(config.providerType)}</span><h2>{config.name}</h2></div>
                {config.defaultConfig && <span className="ai-default-badge"><Star size={11} fill="currentColor" />{translate('aiConfig.default')}</span>}
              </header>
              <div className="ai-config-details">
                <div><Server size={14} /><span><small>{translate('aiConfig.baseUrl')}</small><strong>{config.baseUrl}</strong></span></div>
                <div><Bot size={14} /><span><small>{translate('aiConfig.model')}</small><strong>{config.defaultModel || translate('aiConfig.modelUnset')}</strong></span></div>
                <div><KeyRound size={14} /><span><small>{translate('aiConfig.apiKey')}</small><strong>{config.hasApiKey ? config.apiKeyHint : translate('aiConfig.keyUnset')}</strong></span></div>
              </div>
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
                  {!config.defaultConfig && <button type="button" onClick={() => makeDefault(config.id)}><Star size={14} />{translate('aiConfig.setDefault')}</button>}
                  <button type="button" aria-label={translate('common.edit')} onClick={() => openEdit(config)}><Pencil size={14} /></button>
                  <button type="button" aria-label={translate('common.delete')} onClick={() => removeConfig(config)}><Trash2 size={14} /></button>
                </div>
              </footer>
            </article>
          ))}
        </div>
      )}

      {editingConfig !== undefined && (
        <div className="workspace-modal-backdrop" role="presentation" onMouseDown={closeEditor}>
          <form className="workspace-modal ai-config-editor" onSubmit={submitConfig} onMouseDown={(event) => event.stopPropagation()}>
            <header>
              <div><span className="workspace-eyebrow">{translate('aiConfig.editorEyebrow')}</span><h2>{translate(editingConfig ? 'aiConfig.editTitle' : 'aiConfig.createTitle')}</h2></div>
              <button type="button" aria-label={translate('common.close')} onClick={closeEditor}><X size={19} /></button>
            </header>
            <div className="workspace-form-grid">
              <label className="workspace-field"><span>{translate('aiConfig.field.name')}</span><input required maxLength={AI_CONFIG_LIMITS.name} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
              <label className="workspace-field"><span>{translate('aiConfig.field.provider')}</span><select value={form.providerType} onChange={(event) => selectProvider(event.target.value)}>{AI_PROVIDER_OPTIONS.map((provider) => <option key={provider.value} value={provider.value}>{provider.label}</option>)}</select></label>
              <label className="workspace-field field-wide"><span>{translate('aiConfig.field.baseUrl')}</span><input required type="url" maxLength={AI_CONFIG_LIMITS.baseUrl} value={form.baseUrl} onChange={(event) => setForm({ ...form, baseUrl: event.target.value })} placeholder="https://api.example.com" /></label>
              <label className="workspace-field"><span>{translate('aiConfig.field.model')}</span><input maxLength={AI_CONFIG_LIMITS.model} value={form.defaultModel} onChange={(event) => setForm({ ...form, defaultModel: event.target.value })} placeholder={translate('aiConfig.modelPlaceholder')} /></label>
              <label className="workspace-field"><span>{translate('aiConfig.field.apiKey')}</span><input type="password" autoComplete="new-password" maxLength={AI_CONFIG_LIMITS.apiKey} value={form.apiKey} disabled={form.removeApiKey} onChange={(event) => setForm({ ...form, apiKey: event.target.value })} placeholder={editingConfig?.hasApiKey ? translate('aiConfig.keepExistingKey') : 'sk-...'} /></label>
              {editingConfig?.hasApiKey && <label className="ai-clear-key field-wide"><input type="checkbox" checked={form.removeApiKey} onChange={(event) => setForm({ ...form, removeApiKey: event.target.checked, apiKey: '' })} /><span>{translate('aiConfig.removeKey')}</span></label>}
              <label className="workspace-field field-wide"><span>{translate('aiConfig.field.remark')}</span><textarea maxLength={AI_CONFIG_LIMITS.remark} rows="3" value={form.remark} onChange={(event) => setForm({ ...form, remark: event.target.value })} /></label>
              <label className="ai-form-switch"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span><strong>{translate('aiConfig.enabled')}</strong><small>{translate('aiConfig.enabledHint')}</small></span></label>
              <label className="ai-form-switch"><input type="checkbox" checked={form.defaultConfig} onChange={(event) => setForm({ ...form, defaultConfig: event.target.checked, enabled: event.target.checked || form.enabled })} /><span><strong>{translate('aiConfig.default')}</strong><small>{translate('aiConfig.defaultHint')}</small></span></label>
            </div>
            {error && <div className="workspace-alert">{error}</div>}
            <footer><button type="button" onClick={closeEditor}>{translate('common.cancel')}</button><button className="workspace-primary" type="submit" disabled={saving}>{translate(saving ? 'common.saving' : 'common.save')}</button></footer>
          </form>
        </div>
      )}
    </section>
  )
}

function providerLabel(providerType) {
  return AI_PROVIDER_OPTIONS.find((provider) => provider.value === providerType)?.label ?? providerType
}

export default AiProviderSettings
