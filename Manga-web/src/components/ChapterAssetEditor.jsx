import { useEffect, useId, useRef, useState } from 'react'
import { X } from 'lucide-react'
import { ASSET_DETAILS } from './scriptChapterAssetImages'
import WorkspaceSelect from './WorkspaceSelect'

/** 新建项目资产、编辑资料或选取已有资产加入当前章节。 */
export default function ChapterAssetEditor({ category, asset, loadAvailable, onSave, onAttach, onClose }) {
  const dialog = useRef(null)
  const titleId = useId()
  const [fields, setFields] = useState(() => asset?.fields || {})
  const [mode, setMode] = useState('new')
  const [available, setAvailable] = useState(null)
  const [selected, setSelected] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const existing = mode === 'existing'

  useEffect(() => {
    const element = dialog.current
    element.showModal()
    return () => { if (element.open) element.close() }
  }, [])

  const close = () => {
    if (busy) return
    dialog.current.close()
    onClose()
  }

  const chooseExisting = async () => {
    setMode('existing')
    if (available) return
    setError('')
    try { setAvailable(await loadAvailable()) }
    catch (failure) { setError(failure.message); setAvailable([]) }
  }

  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError('')
    try {
      if (existing) await onAttach(selected)
      else await onSave(fields)
      dialog.current.close()
      onClose()
    } catch (failure) {
      setError(failure.message)
    } finally {
      setBusy(false)
    }
  }

  const textField = ([key, label, limit], multiline = false) => <label key={key} className="workspace-field">
    <span>{label}</span>
    {multiline
      ? <textarea aria-label={label} rows={3} maxLength={limit} value={fields[key] || ''}
        onChange={(event) => setFields({ ...fields, [key]: event.target.value })} />
      : <input aria-label={label} required={key === 'displayName'} maxLength={limit} value={fields[key] || ''}
        onChange={(event) => setFields({ ...fields, [key]: event.target.value })} />}
  </label>

  return <dialog ref={dialog} className="script-reference-picker chapter-asset-editor" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); close() }}
    onClick={(event) => { if (event.target === event.currentTarget) close() }}>
    <header className="script-reference-picker-header">
      <h2 id={titleId}>{asset ? '编辑' : '添加'}{category.label}</h2>
      <button type="button" className="workbench-icon-button" aria-label="关闭资产编辑" onClick={close}><X size={17} /></button>
    </header>
    <form className="chapter-asset-editor-form" onSubmit={submit}>
      <div className="script-reference-picker-body">
        {!asset && <div role="group" aria-label="资产来源" className="workbench-tabs">
          <button type="button" aria-pressed={!existing} className={!existing ? 'active' : ''} onClick={() => { setMode('new'); setError('') }}>新建资产</button>
          <button type="button" aria-pressed={existing} className={existing ? 'active' : ''} onClick={chooseExisting}>选择已有资产</button>
        </div>}
        {existing ? <div className="workspace-field">
          <span>项目{category.label}</span>
          {available?.length ? <WorkspaceSelect label={`项目${category.label}`} options={available} value={selected} onChange={setSelected} />
            : <p className="chapter-panel-note">{available ? '暂无可加入本章的资产' : '正在读取资产…'}</p>}
        </div> : <div className="chapter-asset-editor-fields">
          {textField(['displayName', '资产名称', 120])}
          {textField(['overview', '资产概述', 2000], true)}
          {ASSET_DETAILS[category.value].map((field) => textField(field))}
        </div>}
        {error && <p className="workspace-alert" role="alert">{error}</p>}
      </div>
      <footer className="script-reference-picker-footer">
        <button type="button" className="workspace-secondary" disabled={busy} onClick={close}>取消</button>
        <button type="submit" className="workspace-primary" disabled={busy || (existing ? !selected : !fields.displayName?.trim())}>
          {busy ? '保存中…' : existing ? '加入章节' : '保存资料'}
        </button>
      </footer>
    </form>
  </dialog>
}
