import { useEffect, useId, useRef, useState } from 'react'
import { Image as ImageIcon, X } from 'lucide-react'
import { adoptShotAssetImage, getShotAssetImage, listChapterAssets } from '../api/projectAssetApi'
import ChapterAssetImage from './ChapterAssetImage'
import './script-chapter-workspace.css'

/** 从镜头所属章节选择资产图并保存为参考图。 */
export default function StoryboardAssetImage({ accessToken, projectId, shot, onAdopted }) {
  const [open, setOpen] = useState(false)
  const [chooser, setChooser] = useState(null)
  const [selected, setSelected] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const dialog = useRef(null)
  const titleId = useId()

  const read = async () => {
    setChooser(null); setSelected(null); setError('')
    try {
      const [items, context] = await Promise.all([
        listChapterAssets(accessToken, projectId, shot.chapterId),
        getShotAssetImage(accessToken, projectId, shot.id),
      ])
      setChooser({ context, assets: items.map((item) => ({
        ...item, visuals: item.visuals.filter((visual) => visual.previewUrl),
      })).filter((item) => item.visuals.length) })
    } catch (failure) { setError(failure.message) }
  }

  useEffect(() => {
    if (!open) return undefined
    const element = dialog.current
    element.showModal()
    return () => { if (element.open) element.close() }
  }, [open])

  const close = () => {
    if (busy) return
    dialog.current.close()
    setOpen(false)
  }

  const adopt = async () => {
    setBusy(true); setError('')
    try {
      await adoptShotAssetImage(accessToken, projectId, shot.id, {
        visualId: selected.id, artifactId: selected.currentArtifactId, expectedProjectRevision: chooser.context.projectRevision,
      })
      dialog.current.close()
      setOpen(false)
      onAdopted()
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }

  return <div className="storyboard-asset-reference">
    <button type="button" className="workspace-secondary" disabled={!shot.chapterId} title="从镜头所属章节选择资产图"
      onClick={() => { setOpen(true); void read() }}><ImageIcon size={15} />选择资产参考图</button>
    {open && <dialog ref={dialog} className="script-reference-picker" aria-labelledby={titleId}
      onCancel={(event) => { event.preventDefault(); close() }}
      onClick={(event) => { if (event.target === event.currentTarget) close() }}>
      <header className="script-reference-picker-header">
        <div><h2 id={titleId}>选择资产参考图</h2><p>{shot.title}</p></div>
        <button type="button" className="workbench-icon-button" onClick={close} aria-label="关闭资产参考图选择"><X size={17} /></button>
      </header>
      <div className="script-reference-picker-body">
        {chooser?.assets.map((item) => <section key={item.id} className="script-reference-picker-group">
          <h3>{item.fields.displayName}</h3>
          <div className="script-reference-options">{item.visuals.map((visual) => <button type="button" key={visual.id}
            className="chapter-image-button" aria-pressed={selected?.id === visual.id} onClick={() => setSelected(visual)}>
            <span className="chapter-image-frame"><ChapterAssetImage preview={{ src: visual.previewUrl }} /></span>
            <span>{visual.displayName}</span>
          </button>)}</div>
        </section>)}
        {!error && !chooser?.assets.length && <p className="chapter-panel-note">{chooser ? '本章暂无已生成的资产图' : '正在读取章节资产…'}</p>}
        {error && <p role="alert">{error} <button type="button" className="workspace-secondary" onClick={read}>重新读取</button></p>}
      </div>
      <footer className="script-reference-picker-footer">
        <button type="button" className="workspace-secondary" disabled={busy} onClick={close}>取消</button>
        <button type="button" className="workspace-primary" disabled={busy || !selected} onClick={adopt}>用作镜头参考图</button>
      </footer>
    </dialog>}
  </div>
}
