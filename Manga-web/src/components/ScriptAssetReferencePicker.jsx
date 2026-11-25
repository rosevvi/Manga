import { useEffect, useId, useRef, useState } from 'react'
import { Check, X } from 'lucide-react'
import ChapterAssetImage from './ChapterAssetImage'
import { CHAPTER_IMAGE_LIMITS } from './scriptChapterAssetImages'

/** 在当前资产的图片分组中多选参考图，确认后回写详情抽屉。 */
export default function ScriptAssetReferencePicker({ assetName, groups, selectedIds, onClose, onConfirm }) {
  const dialog = useRef(null)
  const titleId = useId()
  const [selection, setSelection] = useState(() => new Set(selectedIds))

  useEffect(() => {
    const element = dialog.current
    element.showModal()
    return () => { if (element.open) element.close() }
  }, [])

  const close = () => {
    dialog.current.close()
    onClose()
  }

  const toggle = (id) => setSelection((previous) => {
    const next = new Set(previous)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    return next
  })

  return <dialog ref={dialog} className="script-reference-picker" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); event.stopPropagation(); close() }}
    onKeyDown={(event) => { if (event.key === 'Escape') event.stopPropagation() }}
    onClick={(event) => { if (event.target === event.currentTarget) close() }}>
    <header className="script-reference-picker-header">
      <div><h2 id={titleId}>选择参考图</h2><p>{assetName}</p></div>
      <button type="button" className="workbench-icon-button" aria-label="关闭参考图选择" onClick={close} autoFocus><X size={17} /></button>
    </header>
    <div className="script-reference-picker-body">
      {groups.length ? groups.map((group) => <section className="script-reference-picker-group" key={group.id}>
        <h3>{group.title}</h3>
        <div className="script-reference-options">
          {group.images.map((image) => <button type="button" key={image.id} className="chapter-image-button"
            aria-pressed={selection.has(image.id)}
            disabled={!selection.has(image.id) && selection.size >= CHAPTER_IMAGE_LIMITS.references} onClick={() => toggle(image.id)}>
            <span className="chapter-image-frame">
              <ChapterAssetImage preview={image.preview} />
              {selection.has(image.id) && <span className="script-reference-selected"><Check size={12} aria-hidden="true" /></span>}
            </span>
            <span>{image.label}</span>
          </button>)}
        </div>
      </section>) : <p className="chapter-panel-note">当前对象暂无可选的其他图片。</p>}
    </div>
    <footer className="script-reference-picker-footer">
      <small>已选 {selection.size} 张</small>
      <button type="button" className="workspace-secondary" onClick={close}>取消</button>
      <button type="button" className="workspace-primary" onClick={() => { onConfirm([...selection]); close() }}>确认选择</button>
    </footer>
  </dialog>
}
