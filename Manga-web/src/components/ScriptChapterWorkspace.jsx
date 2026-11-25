import { useState } from 'react'
import { BookOpen, Box, ChevronDown, FileText, Image as ImageIcon, Mountain, Network, Plus, Star, UserRound, UsersRound } from 'lucide-react'
import { useLanguage } from '../i18n/LanguageContext'
import { CHAPTER_ASSET_CATEGORY } from '../constants/project'
import ChapterAssetImage from './ChapterAssetImage'
import ScriptAssetDrawer from './ScriptAssetDrawer'
import WorkspaceSelect from './WorkspaceSelect'
import { flattenChapterImageGroups, upsertChapterImage } from './scriptChapterAssetImages'
import './script-chapter-workspace.css'

const { CHARACTER, SCENE, PROP } = CHAPTER_ASSET_CATEGORY
const CATEGORIES = [
  { value: CHARACTER, label: '角色', overviewTitle: '主要角色', icon: UsersRound },
  { value: SCENE, label: '场景', overviewTitle: '场景', icon: Mountain },
  { value: PROP, label: '道具', overviewTitle: '道具', icon: Box },
]
/** 章节概要与资产工作区共享当前选中的对象，图片详情按需从右侧展开。 */
export default function ScriptChapterWorkspace({ chapter, project, assets, onAssetsChange, onOpenSource, onShowAssets }) {
  const { locale } = useLanguage()
  const [selection, setSelection] = useState({ category: CHARACTER, assetId: null })
  const [detail, setDetail] = useState(null)
  const category = CATEGORIES.find((item) => item.value === selection.category)
  const items = assets[category.value]
  const asset = items.find((item) => item.id === selection.assetId) || items[0]
  const detailAsset = detail && items.find((item) => item.id === detail.assetId)
  const detailGroup = detailAsset && flattenChapterImageGroups(detailAsset.groups).find((item) => item.id === detail.groupId)
  const detailImage = detailGroup?.images.find((item) => item.id === detail.imageId)
  const characters = assets[CHARACTER]
  const coverUrl = project?.coverPreviewUrl || project?.coverUrl
  const cover = coverUrl ? { src: coverUrl } : assets.cover
  const synopsis = chapter.synopsis?.trim()
  const updated = Date.parse(chapter.updatedAt)
  const chooseAsset = (value, assetId) => {
    setSelection({ category: value, assetId })
    onShowAssets()
  }
  const openImage = (group, image) => setDetail({ assetId: asset.id, groupId: group.id, imageId: image?.id })
  const updateAsset = (assetId, update) => onAssetsChange((current) => ({
    ...current,
    [category.value]: current[category.value].map((item) => item.id === assetId ? update(item) : item),
  }))

  const updateImage = (assetId, groupId, image) => {
    updateAsset(assetId, (item) => ({ ...item, groups: upsertChapterImage(item.groups, groupId, image) }))
    if (!detail.imageId) setSelection({ category: category.value, assetId })
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
          <div className="chapter-image-frame"><ChapterAssetImage preview={cover} alt={coverUrl ? '项目封面' : '示例场景图'} /></div>
          <dl>
            <div><dt>章节名称</dt><dd>{chapter.title}</dd></div>
            <div><dt>章节摘要</dt><dd>{synopsis || '暂无章节摘要'}</dd></div>
          </dl>
        </div>
      </section>

      {assets.isPreview && <p className="chapter-preview-label">以下角色、场景和道具为布局示例。</p>}
      <div className="chapter-overview-grid">
        <OverviewAssetList category={CATEGORIES[0]} items={characters} onSelect={chooseAsset} />
        <section className="chapter-card">
          <h3 className="chapter-card-title"><Network size={16} aria-hidden="true" />角色关系</h3>
          {assets.relationships.length ? <ul className="chapter-relations">
            {assets.relationships.map(({ id, from, to, label }) => {
              const source = characters.find((item) => item.id === from)
              const target = characters.find((item) => item.id === to)
              if (!source || !target) return null
              return <li key={id}>
                {[source, target].map((item, index) => <div className={`chapter-relation-person person-${index}`} key={item.id}>
                  <span className="chapter-relation-avatar"><ChapterAssetImage preview={item.preview} /></span>
                  <span>{item.name}</span>
                </div>)}
                <span className="chapter-relation-label">{label}</span>
              </li>
            })}
          </ul> : <p className="chapter-panel-note">暂无角色关系</p>}
        </section>
        {CATEGORIES.slice(1).map((item) => <OverviewAssetList key={item.value} category={item}
          items={assets[item.value]} onSelect={chooseAsset} />)}
      </div>
    </section>

    <aside className="chapter-assets-panel" aria-label={assets.isPreview ? '章节资产示例' : '章节资产'}>
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
      {assets.isPreview && <p className="chapter-panel-note chapter-assets-preview">示例资产</p>}
      {asset ? <>
        <AssetInformation category={category} asset={asset} onOpen={openImage} />
        {asset.groups.map((group) => <AssetImageGroup key={`${asset.id}-${group.id}`} group={group}
          baselineImageId={asset.baselineImageId} onOpen={openImage} />)}
      </> : <p className="chapter-panel-note">暂无{category.label}资产</p>}
    </aside>
    {detailGroup && <ScriptAssetDrawer key={detail.imageId || detail.groupId} asset={detailAsset}
      group={detailGroup} image={detailImage} assets={items} onUpdate={updateImage}
      onBaseline={(assetId, baselineImageId) => updateAsset(assetId, (item) => ({ ...item, baselineImageId }))}
      onClose={() => setDetail(null)} />}
  </>
}

/** 概览缩略图定位右侧资产面板。 */
function OverviewAssetList({ category, items, onSelect }) {
  const Icon = category.icon
  return <section className="chapter-card">
    <h3 className="chapter-card-title"><Icon size={16} aria-hidden="true" />{category.overviewTitle}<small>{items.length}</small></h3>
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
  const coverGroup = groups.find((group) => group.images.some((image) => image.id === asset.baselineImageId))
    || groups.find((group) => group.images.some((image) => image.preview))
  const cover = coverGroup?.images.find((image) => image.id === asset.baselineImageId)
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
      {!image.preview && <small>待生成</small>}
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
