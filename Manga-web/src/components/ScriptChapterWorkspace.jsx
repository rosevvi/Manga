import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { BookOpen, Box, ChevronDown, FileText, Image as ImageIcon, Mountain, Plus, Star, UserRound, UsersRound } from 'lucide-react'
import { createManualRun } from '../api/creativeRunApi'
import { createProjectAsset, listChapterAssets, listProjectAssets, saveAssetVisual, setAssetPrimaryVisual,
  setChapterAsset, updateProjectAsset } from '../api/projectAssetApi'
import { listImageGenerationOptions } from '../api/generationApi'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import { useLanguage } from '../i18n/LanguageContext'
import { CHAPTER_ASSET_CATEGORY } from '../constants/project'
import ChapterAssetImage from './ChapterAssetImage'
import ChapterAssetEditor from './ChapterAssetEditor'
import { openCreativeTask } from './creativeTaskEvents'
import ScriptAssetDrawer from './ScriptAssetDrawer'
import WorkspaceSelect from './WorkspaceSelect'
import { chapterAssetView, flattenChapterImageGroups } from './scriptChapterAssetImages'
import './script-chapter-workspace.css'

const { CHARACTER, SCENE, PROP } = CHAPTER_ASSET_CATEGORY
const CATEGORIES = [
  { value: CHARACTER, kind: 'CHARACTER', label: '角色', overviewTitle: '主要角色', icon: UsersRound },
  { value: SCENE, kind: 'SCENE', label: '场景', overviewTitle: '场景', icon: Mountain },
  { value: PROP, kind: 'PROP', label: '道具', overviewTitle: '道具', icon: Box },
]
/** 章节概要与资产工作区共享当前选中的对象，图片详情按需从右侧展开。 */
export default function ScriptChapterWorkspace({ chapter, project, projectId, accessToken, onOpenSource, onShowAssets }) {
  const { locale } = useLanguage()
  const [selection, setSelection] = useState({ category: CHARACTER, assetId: null })
  const [detail, setDetail] = useState(null)
  const [editor, setEditor] = useState(null)
  const [chapterAssets, setChapterAssets] = useState(null)
  const [error, setError] = useState('')
  const [modelOptions, setModelOptions] = useState([])
  const [modelError, setModelError] = useState('')
  const request = useRef(0)
  const category = CATEGORIES.find((item) => item.value === selection.category)
  const assets = useMemo(() => Object.fromEntries(CATEGORIES.map(({ value, kind }) => [
    value, (chapterAssets || []).filter((item) => item.assetKind === kind).map((item) => chapterAssetView(item, value)),
  ])), [chapterAssets])
  const items = assets[category.value]
  const asset = items.find((item) => item.id === selection.assetId) || items[0]
  const detailAsset = detail && items.find((item) => item.id === detail.assetId)
  const detailGroup = detailAsset && flattenChapterImageGroups(detailAsset.groups).find((item) => item.id === detail.groupId)
  const detailImage = detailGroup?.images.find((item) => item.id === detail.imageId)
  const coverUrl = project?.coverPreviewUrl || project?.coverUrl
  const synopsis = chapter.synopsis?.trim()
  const updated = Date.parse(chapter.updatedAt)
  const hasPending = chapterAssets?.some((item) => item.visuals.some((visual) => visual.busy))
  const refresh = useCallback(async () => {
    const stamp = ++request.current
    try {
      const result = await listChapterAssets(accessToken, projectId, chapter.id)
      if (stamp === request.current) { setChapterAssets(result); setError('') }
    } catch (failure) {
      if (stamp === request.current) setError(failure.message)
    }
  }, [accessToken, projectId, chapter.id])

  useEffect(() => {
    void refresh()
    return () => { request.current += 1 }
  }, [refresh])

  useEffect(() => {
    if (!hasPending) return undefined
    let stopped = false
    let timer
    const poll = async () => { await refresh(); if (!stopped) timer = window.setTimeout(poll, 3000) }
    timer = window.setTimeout(poll, 3000)
    return () => { stopped = true; window.clearTimeout(timer) }
  }, [hasPending, refresh])

  useEffect(() => {
    if (!detail) return undefined
    let active = true
    listImageGenerationOptions(accessToken).then((result) => {
      if (active) { setModelOptions(result); setModelError('') }
    }).catch((failure) => { if (active) setModelError(failure.message) })
    return () => { active = false }
  }, [accessToken, detail])

  const chooseAsset = (value, assetId) => {
    setSelection({ category: value, assetId })
    onShowAssets()
  }
  const openImage = (group, image) => setDetail({ assetId: asset.id, groupId: group.id, imageId: image?.id })
  const saveAsset = async (fields) => {
    let id = editor.asset?.id
    if (id) await updateProjectAsset(accessToken, projectId, id, fields)
    else ({ id } = await createProjectAsset(accessToken, projectId, {
      fields, assetKind: editor.category.kind, chapterId: chapter.id,
    }))
    await refresh()
    chooseAsset(editor.category.value, id)
  }
  const linkAsset = async (id, attached) => {
    await setChapterAsset(accessToken, projectId, chapter.id, id, attached)
    await refresh()
    if (attached) chooseAsset(editor.category.value, id)
  }
  const availableAssets = async () => (await listProjectAssets(accessToken, projectId)).filter((item) =>
    item.assetKind === editor.category.kind && !assets[editor.category.value].some((linked) => linked.id === item.id))
    .map((item) => ({ value: item.id, label: item.displayName }))
  const startGeneration = (visualId) => createManualRun(accessToken, {
    requestId: globalThis.crypto.randomUUID(), projectId, operationType: CREATIVE_OPERATION.GENERATE_ASSET_IMAGE,
    target: { resourceType: CREATIVE_RESOURCE.ASSET_VISUAL, resourceId: visualId },
    permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES, instruction: '生成资产图片',
  })
  const updatePrimary = async (assetId, visualId) => {
    await setAssetPrimaryVisual(accessToken, projectId, assetId, visualId)
    await refresh()
  }

  return <>
    <section className="project-content-panel chapter-overview" aria-label="章节概览">
      <header className="chapter-overview-header">
        <div className="chapter-overview-title">
          <h2>{chapter.title}</h2>
          {Number.isFinite(updated) && <time dateTime={chapter.updatedAt}>
            更新于 {new Date(updated).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' })}
          </time>}
        </div>
        <button type="button" className="workspace-secondary" onClick={onOpenSource}><FileText size={15} aria-hidden="true" />查看原文</button>
      </header>

      <section className="chapter-card chapter-summary">
        <h3 className="chapter-card-title"><BookOpen size={16} aria-hidden="true" />章节概要</h3>
        <div className="chapter-summary-body">
          <div className="chapter-image-frame"><ChapterAssetImage preview={coverUrl ? { src: coverUrl } : null} alt="项目封面" /></div>
          <dl>
            <div><dt>章节名称</dt><dd>{chapter.title}</dd></div>
            <div><dt>章节摘要</dt><dd>{synopsis || '暂无章节摘要'}</dd></div>
          </dl>
        </div>
      </section>

      <div className="chapter-overview-grid">
        {CATEGORIES.map((item) => <OverviewAssetList key={item.value} category={item}
          items={assets[item.value]} onSelect={chooseAsset} onAdd={() => setEditor({ category: item })} />)}
      </div>
    </section>

    <aside className="chapter-assets-panel" aria-label="章节资产">
      <header className="chapter-assets-header">
        <div className="workbench-tabs chapter-assets-tabs" role="group" aria-label="资产类别">
          {CATEGORIES.map(({ value, label }) => <button type="button" key={value}
            className={value === category.value ? 'active' : ''} aria-pressed={value === category.value}
            onClick={() => setSelection({ category: value, assetId: null })}>{label}</button>)}
        </div>
        {asset && <WorkspaceSelect label={`当前${category.label}`} value={asset.id}
          options={items.map((item) => ({ value: item.id, label: item.name }))}
          onChange={(assetId) => setSelection({ category: category.value, assetId })} />}
      </header>
      <div className="chapter-asset-toolbar">
        <button type="button" className="workspace-secondary" onClick={() => setEditor({ category })}><Plus size={15} />添加{category.label}</button>
        {asset && <button type="button" className="workspace-secondary" onClick={() => setEditor({ category, asset })}>编辑资料</button>}
        {asset && <button type="button" className="workspace-secondary" onClick={() => void linkAsset(asset.id, false)
          .catch((failure) => setError(failure.message))}>移出本章</button>}
      </div>
      {error && <p className="workspace-alert" role="alert">{error} <button type="button" className="workspace-secondary" onClick={refresh}>重新读取</button></p>}
      {asset ? <>
        <AssetInformation category={category} asset={asset} onOpen={openImage} />
        {asset.groups.map((group) => <AssetImageGroup key={`${asset.id}-${group.id}`} group={group}
          baselineImageId={asset.primaryVisualId} onOpen={openImage} />)}
      </> : <p className="chapter-panel-note">{chapterAssets ? `暂无${category.label}资产` : !error && '正在读取章节资产…'}</p>}
    </aside>
    {detailGroup && <ScriptAssetDrawer key={detail.imageId || detail.groupId} asset={detailAsset} group={detailGroup}
      image={detailImage} assets={items} modelOptions={modelOptions} modelError={modelError}
      onSave={(assetId, visualId, payload) => saveAssetVisual(accessToken, projectId, assetId, visualId, payload)}
      onBaseline={updatePrimary} onGenerate={startGeneration}
      onClose={(savedAssetId, run) => {
        setDetail(null)
        if (savedAssetId) { chooseAsset(category.value, savedAssetId); void refresh() }
        if (run) openCreativeTask(run.runId, projectId)
      }} />}
    {editor && <ChapterAssetEditor {...editor} loadAvailable={availableAssets} onSave={saveAsset}
      onAttach={(id) => linkAsset(id, true)} onClose={() => setEditor(null)} />}
  </>
}

/** 概览缩略图定位右侧资产面板。 */
function OverviewAssetList({ category, items, onSelect, onAdd }) {
  const Icon = category.icon
  return <section className="chapter-card">
    <h3 className="chapter-card-title"><Icon size={16} aria-hidden="true" />{category.overviewTitle}<small>{items.length}</small>
      <button type="button" className="workbench-icon-button chapter-overview-add" aria-label={`添加${category.label}`} onClick={onAdd}><Plus size={14} /></button></h3>
    {items.length ? <div className="chapter-overview-assets" data-category={category.value}>
      {items.map((item) => <button type="button" key={item.id} className="chapter-image-button"
        aria-label={`查看${category.label} ${item.name}`} onClick={() => onSelect(category.value, item.id)}>
        <span className="chapter-image-frame"><ChapterAssetImage preview={item.preview} /></span>
        <strong>{item.name}</strong>
        {item.role && <small>{item.role}</small>}
      </button>)}
    </div> : <p className="chapter-panel-note">暂无{category.label}</p>}
  </section>
}

/** 展示资产属性并允许从封面打开首张详情图。 */
function AssetInformation({ category, asset, onOpen }) {
  const Icon = category.value === CHARACTER ? UserRound : category.icon
  const groups = flattenChapterImageGroups(asset.groups)
  const coverGroup = groups.find((group) => group.images.some((image) => image.id === asset.primaryVisualId))
    || groups.find((group) => group.images.some((image) => image.preview))
  const cover = coverGroup?.images.find((image) => image.id === asset.primaryVisualId)
    || coverGroup?.images.find((image) => image.preview)
  return <section className="chapter-card chapter-asset-info">
    <h3 className="chapter-card-title"><Icon size={16} aria-hidden="true" />{category.label}信息</h3>
    <div className="chapter-asset-info-body">
      {cover ? <button type="button" className="chapter-image-frame chapter-image-trigger"
        aria-label={`查看图片 ${asset.name}`} onClick={() => onOpen(coverGroup, cover)}>
        <ChapterAssetImage preview={cover.preview} />
      </button> : <div className="chapter-image-frame"><ChapterAssetImage preview={asset.preview} /></div>}
      <dl>
        <div><dt>名称</dt><dd>{asset.name}</dd></div>
        {asset.details.map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}
        {asset.role && <div><dt>角色定位</dt><dd><span className="chapter-role-tag">{asset.role}</span></dd></div>}
      </dl>
    </div>
  </section>
}

/** 三视图随设定图分组，表情集使用独立的同级分组。 */
function AssetImageGroup({ group, baselineImageId, onOpen, nested = false }) {
  const gallery = <div className="chapter-asset-gallery">
    {group.images.map((image) => <button type="button" className="chapter-image-button" key={image.id}
      aria-label={`查看图片 ${image.label}`} aria-haspopup="dialog" onClick={() => onOpen(group, image)}>
      <span className="chapter-image-frame" data-baseline={baselineImageId === image.id || undefined}>
        <ChapterAssetImage preview={image.preview} />
        {baselineImageId === image.id && <span className="chapter-image-baseline" title="基准图"><Star size={11} aria-hidden="true" /></span>}
      </span>
      <span>{image.label}</span>
      {image.busy ? <small>生成任务处理中</small> : !image.preview && <small>待生成</small>}
    </button>)}
    <button type="button" className="chapter-add-image" aria-label={`${group.title} 添加生成`}
      aria-haspopup="dialog" onClick={() => onOpen(group)}>
      <Plus size={20} aria-hidden="true" />添加生成
    </button>
  </div>

  if (nested) return <section className="chapter-asset-subgroup">
    <h4>{group.title}</h4>{gallery}
  </section>

  return <details className="chapter-card chapter-asset-group" open>
    <summary className="chapter-card-title"><ImageIcon size={16} aria-hidden="true" />{group.title}
      <small>{group.images.length}</small><ChevronDown className="chapter-group-chevron" size={15} aria-hidden="true" />
    </summary>
    {gallery}
    {group.sections?.map((section) => <AssetImageGroup key={section.id} group={section}
      baselineImageId={baselineImageId} onOpen={onOpen} nested />)}
  </details>
}
