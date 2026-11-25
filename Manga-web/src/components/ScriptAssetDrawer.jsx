import { useEffect, useId, useRef, useState } from 'react'
import { Copy, Image as ImageIcon, Plus, RotateCw, Star, UserRound, X } from 'lucide-react'
import { useLanguage } from '../i18n/LanguageContext'
import ChapterAssetImage from './ChapterAssetImage'
import ScriptAssetReferencePicker from './ScriptAssetReferencePicker'
import WorkspaceSelect from './WorkspaceSelect'
import { CHAPTER_IMAGE_LIMITS, flattenChapterImageGroups } from './scriptChapterAssetImages'

const modelKey = (model) => `${model.provider}:${model.configId || ''}:${model.model}`
const sizeLabel = (value) => {
  const [width, height] = value.split('x').map(Number)
  const ratio = width === height ? '1:1' : width > height ? '3:2' : '2:3'
  return `${width} × ${height}（${ratio}）`
}

/** 保存图片项设置，图片本身由生成任务成功后在原位置替换。 */
export default function ScriptAssetDrawer({ asset, group, image, assets, modelOptions, modelError,
  onSave, onGenerate, onBaseline, onClose }) {
  const { locale } = useLanguage()
  const drawer = useRef(null)
  const titleId = useId()
  const [ownerId, setOwnerId] = useState(asset.id)
  const [kind, setKind] = useState(image?.visualKind || group.types[0].value)
  const [savedId, setSavedId] = useState(image?.id || null)
  const [draft, setDraft] = useState(() => ({
    label: image?.displayName || '', prompt: image?.prompt || '', negativePrompt: image?.negativePrompt || '',
    model: image?.provider ? modelKey({ provider: image.provider, configId: image.modelConfigId, model: image.modelName }) : '',
    size: image?.imageWidth && image?.imageHeight ? `${image.imageWidth}x${image.imageHeight}` : '',
    referenceVisualIds: image?.referenceVisualIds || [],
  }))
  const [selecting, setSelecting] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const creating = !savedId
  const owner = assets.find((item) => item.id === ownerId) || asset
  const groups = flattenChapterImageGroups(owner.groups)
  const types = groups.flatMap((item) => item.types.map((type) => ({ ...type, icon: ImageIcon })))
  const referenceGroups = groups.map((item) => ({
    ...item, images: item.images.filter((entry) => entry.preview && entry.id !== savedId),
  })).filter((item) => item.images.length)
  const references = referenceGroups.flatMap((item) => item.images).filter((entry) => draft.referenceVisualIds.includes(entry.id))
  const matchingModels = modelOptions.filter((item) => !references.length || item.supportsReferences)
  const selected = matchingModels.find((item) => modelKey(item) === draft.model)
    || (!draft.model ? matchingModels.find((item) => item.configId) || matchingModels[0] : null)
  const sizes = selected?.sizes || []
  const size = sizes.includes(draft.size) ? draft.size : sizes[0]
  const waiting = busy || image?.busy
  const generatedAt = image?.generatedAt
  const timeLabel = generatedAt ? new Date(generatedAt).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' }) : '—'

  useEffect(() => {
    const element = drawer.current
    element.showModal()
    return () => { if (element.open) element.close() }
  }, [])

  const close = (id, run) => {
    drawer.current.close()
    onClose(id, run)
  }
  const dismiss = () => { if (!busy) close(!image && savedId ? owner.id : null) }
  const changeDraft = (patch) => setDraft((current) => ({ ...current, ...patch }))

  const submit = async (generate) => {
    setBusy(true); setError(''); setStatus('')
    try {
      // If a configured model became unavailable, a draft save still preserves the selection.
      const model = selected || modelOptions.find((item) => modelKey(item) === draft.model)
      const [imageWidth, imageHeight] = (size || draft.size).split('x').map(Number)
      const result = await onSave(owner.id, savedId, {
        visualKind: kind, displayName: draft.label, prompt: draft.prompt, negativePrompt: draft.negativePrompt,
        provider: model?.provider ?? image?.provider ?? null,
        modelConfigId: model ? model.configId : image?.modelConfigId ?? null,
        modelName: model?.model ?? image?.modelName ?? null,
        imageWidth: imageWidth || null, imageHeight: imageHeight || null, referenceVisualIds: draft.referenceVisualIds,
      })
      setSavedId(result.id)
      const run = generate ? await onGenerate(result.id) : null
      close(owner.id, run)
    } catch (failure) {
      setError(failure.message)
    } finally {
      setBusy(false)
    }
  }

  const setPrimary = async () => {
    setBusy(true); setError('')
    try { await onBaseline(owner.id, savedId); setStatus('已设为基准图') }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  const copy = async () => {
    try { await navigator.clipboard.writeText(draft.prompt); setStatus('提示词已复制') }
    catch { setError('复制失败，请手动选择提示词') }
  }

  return <dialog ref={drawer} className="script-asset-drawer" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); dismiss() }}
    onKeyDown={(event) => { if (event.key === 'Escape') event.stopPropagation() }}
    onClick={(event) => { if (event.target === event.currentTarget) dismiss() }}>
    <header className="script-asset-drawer-header">
      <h2 id={titleId}>{creating ? '添加生成' : '图片详情'}</h2>
      <div className="workbench-action-row">
        {!creating && <button type="button" className="workspace-secondary" disabled={waiting} onClick={() => submit(false)}>保存设置</button>}
        <button type="button" className="workbench-icon-button" aria-label="关闭图片详情" onClick={dismiss} autoFocus><X size={17} /></button>
      </div>
    </header>
    <div className="script-asset-drawer-body">
      <figure className="script-asset-drawer-figure">
        <div className="chapter-image-frame">
          {image?.preview ? <ChapterAssetImage preview={image.preview} alt={image.label} />
            : <span className="script-asset-drawer-empty"><ImageIcon size={25} aria-hidden="true" />待生成预览</span>}
        </div>
      </figure>
      <div className="script-asset-settings">
        <AssetField label="图片类型"><WorkspaceSelect label="图片类型" value={kind} options={types}
          disabled={!creating || waiting} onChange={setKind} /></AssetField>
        <AssetField label="所属对象"><WorkspaceSelect label="所属对象" value={owner.id}
          options={assets.map((item) => ({ value: item.id, label: item.name, icon: UserRound }))}
          disabled={!creating || waiting} onChange={(id) => { setOwnerId(id); changeDraft({ referenceVisualIds: [] }) }} /></AssetField>
        <AssetField label="图片名称"><input aria-label="图片名称" maxLength={120} disabled={waiting}
          value={draft.label} placeholder={types.find((type) => type.value === kind)?.label}
          onChange={(event) => changeDraft({ label: event.target.value })} /></AssetField>
      </div>
      <PromptField label="提示词" required value={draft.prompt} limit={CHAPTER_IMAGE_LIMITS.prompt} rows={4}
        disabled={waiting} onChange={(prompt) => changeDraft({ prompt })} />
      <PromptField label="负面提示词" value={draft.negativePrompt} limit={CHAPTER_IMAGE_LIMITS.negativePrompt} rows={2}
        disabled={waiting} onChange={(negativePrompt) => changeDraft({ negativePrompt })} />
      <div className="script-asset-settings script-generation-settings">
        <AssetField label="尺寸 / 比例"><WorkspaceSelect label="尺寸 / 比例" value={size}
          options={sizes.map((value) => ({ value, label: sizeLabel(value) }))} disabled={!sizes.length || waiting}
          onChange={(value) => changeDraft({ size: value })} /></AssetField>
        <AssetField label="风格 / 模型"><WorkspaceSelect label="风格 / 模型" value={selected ? modelKey(selected) : ''}
          options={matchingModels.map((option) => ({ value: modelKey(option), label: `${option.name} · ${option.model}` }))}
          disabled={waiting || !matchingModels.length} onChange={(model) => changeDraft({ model })} /></AssetField>
        <AssetField label="生成时间">{generatedAt ? <time dateTime={generatedAt}>{timeLabel}</time> : '—'}</AssetField>
      </div>
      {modelError && <p className="workspace-alert" role="alert">{modelError}</p>}
      {references.length > 0 && !selected && <p className="chapter-panel-note">请选择支持参考图的模型。</p>}
      <section className="script-asset-drawer-section" aria-label="参考图">
        <h3>参考图 <small>{references.length}</small></h3>
        <div className="script-asset-reference-grid">
          {references.map((item) => <button type="button" key={item.id} className="chapter-image-button"
            aria-haspopup="dialog" aria-label={`更换参考图 ${item.label}`} disabled={waiting} onClick={() => setSelecting(true)}>
            <span className="chapter-image-frame"><ChapterAssetImage preview={item.preview} /></span><span>{item.label}</span>
          </button>)}
          <button type="button" className="chapter-add-image script-add-reference" aria-haspopup="dialog" disabled={waiting}
            onClick={() => setSelecting(true)}><Plus size={17} aria-hidden="true" />选择参考图</button>
        </div>
      </section>
    </div>
    <footer className="script-asset-drawer-footer">
      {status && <span role="status">{status}</span>}
      {error && <p className="workspace-alert" role="alert">{error}</p>}
      <div className="script-asset-actions">
        <button type="button" className="workspace-secondary" disabled={!draft.prompt.trim()} onClick={copy}><Copy size={14} />复制提示词</button>
        <button type="button" className="workspace-secondary" disabled={waiting || !selected || !size || !draft.prompt.trim()}
          onClick={() => submit(true)}><RotateCw size={14} />{image?.busy ? '生成处理中' : image?.preview ? '重新生成' : '生成图片'}</button>
        {creating ? <button type="button" className="workspace-primary" disabled={waiting} onClick={() => submit(false)}>保存草稿</button>
          : <button type="button" className="workspace-primary" disabled={waiting || !image?.preview || owner.primaryVisualId === savedId}
            onClick={setPrimary}><Star size={14} />{owner.primaryVisualId === savedId ? '当前基准图' : '设为基准图'}</button>}
      </div>
    </footer>
    {selecting && <ScriptAssetReferencePicker assetName={owner.name} groups={referenceGroups}
      selectedIds={draft.referenceVisualIds} onClose={() => setSelecting(false)}
      onConfirm={(referenceVisualIds) => changeDraft({ referenceVisualIds })} />}
  </dialog>
}

function AssetField({ label, children }) {
  return <div className="script-asset-field"><span>{label}</span><div>{children}</div></div>
}

function PromptField({ label, value, limit, rows, required, disabled, onChange }) {
  return <label className="script-asset-prompt-field">
    <span>{label}{required && <span className="script-asset-required" aria-hidden="true"> *</span>}
      <small>{value.length} / {limit}</small>
    </span>
    <textarea value={value} rows={rows} maxLength={limit} required={required} disabled={disabled}
      aria-label={label} onChange={(event) => onChange(event.target.value)} />
  </label>
}
