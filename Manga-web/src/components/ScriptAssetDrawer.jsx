import { useEffect, useId, useRef, useState } from 'react'
import { Copy, Image as ImageIcon, Plus, RotateCw, Star, UserRound, X } from 'lucide-react'
import { useLanguage } from '../i18n/LanguageContext'
import ChapterAssetImage from './ChapterAssetImage'
import ScriptAssetReferencePicker from './ScriptAssetReferencePicker'
import WorkspaceSelect from './WorkspaceSelect'
import { CHAPTER_IMAGE_DEFAULTS, CHAPTER_IMAGE_LIMITS, CHAPTER_IMAGE_MODELS, CHAPTER_IMAGE_SIZES,
  flattenChapterImageGroups } from './scriptChapterAssetImages'

/** 编辑资产图片的前端配置，参考图选择与主图预览分别呈现。 */
export default function ScriptAssetDrawer({ asset, group, image, assets, onUpdate, onBaseline, onClose }) {
  const { locale } = useLanguage()
  const drawer = useRef(null)
  const titleId = useId()
  const [draft, setDraft] = useState(() => ({ ...CHAPTER_IMAGE_DEFAULTS, ...image }))
  const [target, setTarget] = useState(() => ({
    assetId: asset.id,
    groupId: group.id,
    type: image?.type || group.types?.[0] || group.title,
  }))
  const [selectingReferences, setSelectingReferences] = useState(false)
  const [copyStatus, setCopyStatus] = useState('')
  const creating = !image
  const owner = assets.find((item) => item.id === target.assetId)
  const imageGroups = flattenChapterImageGroups(owner.groups)
  const types = imageGroups.flatMap((item) => {
    const labels = [...new Set([...(item.types || [item.title]), ...item.images.map((entry) => entry.type)])]
    return labels.map((label) => ({ value: `${item.id}:${label}`, label, groupId: item.id, icon: ImageIcon }))
  })
  const referenceGroups = imageGroups.map((item) => ({
    ...item,
    images: item.images.filter((entry) => entry.preview && entry.id !== image?.id),
  })).filter((item) => item.images.length)
  const references = referenceGroups.flatMap((item) => item.images)
    .filter((entry) => draft.referenceImageIds.includes(entry.id))
  const baseline = image?.preview && image.id === asset.baselineImageId
  const generatedAt = image?.generatedAt
  const timeLabel = Number.isFinite(Date.parse(generatedAt))
    ? new Date(generatedAt).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' }) : '—'

  useEffect(() => {
    const element = drawer.current
    element.showModal()
    return () => { if (element.open) element.close() }
  }, [])

  const close = () => {
    drawer.current.close()
    onClose()
  }

  const updateDraft = (patch) => {
    const next = { ...draft, ...patch }
    setDraft(next)
    if (image) onUpdate(asset.id, group.id, next)
  }

  const changeOwner = (assetId) => {
    const next = assets.find((item) => item.id === assetId)
    const nextGroup = flattenChapterImageGroups(next.groups).find((item) => item.id === target.groupId) || next.groups[0]
    setTarget({ assetId, groupId: nextGroup.id, type: nextGroup.types?.[0] || nextGroup.title })
    setDraft({ ...draft, referenceImageIds: [] })
  }

  const saveDraft = () => {
    if (!draft.prompt.trim()) return
    onUpdate(target.assetId, target.groupId, {
      ...draft,
      id: globalThis.crypto.randomUUID(),
      type: target.type,
      label: target.type.split(' · ').at(-1),
    })
    close()
  }

  const copyPrompt = async () => {
    try {
      await navigator.clipboard.writeText(draft.prompt)
      setCopyStatus('提示词已复制')
    } catch {
      setCopyStatus('复制失败，请手动选择提示词')
    }
  }

  return <dialog ref={drawer} className="script-asset-drawer" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); close() }}
    onKeyDown={(event) => { if (event.key === 'Escape') event.stopPropagation() }}
    onClick={(event) => { if (event.target === event.currentTarget) close() }}>
    <header className="script-asset-drawer-header">
      <h2 id={titleId}>{creating ? '添加生成' : '图片详情'}</h2>
      <button type="button" className="workbench-icon-button" aria-label="关闭图片详情" onClick={close} autoFocus><X size={17} /></button>
    </header>
    <div className="script-asset-drawer-body">
      <figure className="script-asset-drawer-figure" data-fit={image?.preview?.fit}>
        <div className="chapter-image-frame">
          {image?.preview ? <ChapterAssetImage preview={image.preview} alt={image.label} />
            : <span className="script-asset-drawer-empty"><ImageIcon size={25} aria-hidden="true" />待生成预览</span>}
        </div>
      </figure>
      <div className="script-asset-settings">
        <AssetField label="图片类型"><WorkspaceSelect label="图片类型" value={`${target.groupId}:${target.type}`} options={types}
          disabled={!creating} onChange={(value) => {
            const option = types.find((item) => item.value === value)
            setTarget({ ...target, groupId: option.groupId, type: option.label })
          }} /></AssetField>
        <AssetField label="所属对象"><WorkspaceSelect label="所属对象" value={target.assetId}
          options={assets.map((item) => ({ value: item.id, label: item.name, icon: UserRound }))}
          disabled={!creating} onChange={changeOwner} /></AssetField>
      </div>
      <PromptField label="提示词" required value={draft.prompt} limit={CHAPTER_IMAGE_LIMITS.prompt} rows={4}
        onChange={(prompt) => updateDraft({ prompt })} />
      <PromptField label="负面提示词" value={draft.negativePrompt} limit={CHAPTER_IMAGE_LIMITS.negativePrompt} rows={2}
        onChange={(negativePrompt) => updateDraft({ negativePrompt })} />
      <div className="script-asset-settings script-generation-settings">
        <AssetField label="尺寸 / 比例"><WorkspaceSelect label="尺寸 / 比例" value={draft.size}
          options={CHAPTER_IMAGE_SIZES} onChange={(size) => updateDraft({ size })} /></AssetField>
        <AssetField label="风格 / 模型"><WorkspaceSelect label="风格 / 模型" value={draft.model}
          options={CHAPTER_IMAGE_MODELS} onChange={(model) => updateDraft({ model })} /></AssetField>
        <AssetField label="生成时间">{generatedAt ? <time dateTime={generatedAt}>{timeLabel}</time> : '—'}</AssetField>
        <AssetField label="版本号">{image?.version || '—'}</AssetField>
      </div>
      <section className="script-asset-drawer-section" aria-label="参考图">
        <h3>参考图 <small>{references.length}</small></h3>
        <div className="script-asset-reference-grid">
          {references.map((item) => <button type="button" key={item.id} className="chapter-image-button"
            aria-haspopup="dialog" aria-label={`更换参考图 ${item.label}`} onClick={() => setSelectingReferences(true)}>
            <span className="chapter-image-frame"><ChapterAssetImage preview={item.preview} /></span><span>{item.label}</span>
          </button>)}
          <button type="button" className="chapter-add-image script-add-reference" aria-haspopup="dialog"
            onClick={() => setSelectingReferences(true)}><Plus size={17} aria-hidden="true" />选择参考图</button>
        </div>
      </section>
    </div>
    <footer className="script-asset-drawer-footer">
      {copyStatus && <span role="status">{copyStatus}</span>}
      <div className="script-asset-actions">
        <button type="button" className="workspace-secondary" disabled={!draft.prompt.trim()} onClick={copyPrompt}><Copy size={14} aria-hidden="true" />复制提示词</button>
        <button type="button" className="workspace-secondary" disabled title="图片生成服务待接入"><RotateCw size={14} aria-hidden="true" />
          {image?.preview ? '重新生成' : '生成图片'}</button>
        {creating ? <button type="button" className="workspace-primary" disabled={!draft.prompt.trim()} onClick={saveDraft}>保存草稿</button>
          : <button type="button" className="workspace-primary" disabled={!image.preview || baseline} onClick={() => onBaseline(asset.id, image.id)}>
            <Star size={14} aria-hidden="true" />{baseline ? '当前基准图' : '设为基准图'}
          </button>}
      </div>
    </footer>
    {selectingReferences && <ScriptAssetReferencePicker assetName={owner.name} groups={referenceGroups}
      selectedIds={references.map((item) => item.id)} onClose={() => setSelectingReferences(false)}
      onConfirm={(referenceImageIds) => updateDraft({ referenceImageIds })} />}
  </dialog>
}

/** 对齐详情中的标签和控件，生成时间等元数据也沿用同一网格。 */
function AssetField({ label, children }) {
  return <div className="script-asset-field"><span>{label}</span><div>{children}</div></div>
}

/** 提示词输入单独显示字符用量，保留可复制的可见正文。 */
function PromptField({ label, value, limit, rows, required, onChange }) {
  return <label className="script-asset-prompt-field">
    <span>{label}{required && <span className="script-asset-required" aria-hidden="true"> *</span>}
      <small>{value.length} / {limit}</small>
    </span>
    <textarea value={value} rows={rows} maxLength={limit} required={required} aria-label={label}
      onChange={(event) => onChange(event.target.value)} />
  </label>
}
