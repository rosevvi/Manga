import { useState } from 'react'
import { ChevronDown, ChevronRight, Film, Folder, Plus } from 'lucide-react'

/** 按章节折叠镜头，支持从分镜目录直接切换当前选择。 */
export default function StoryboardDirectory({
  groups, allChapters, chapter, selectedShot, chapterOf,
  onChapterChange, onShotChange, onCreate, saving,
}) {
  const [closed, setClosed] = useState(null)
  const activeChapter = chapter === allChapters && selectedShot ? chapterOf(selectedShot) : chapter

  async function toggleChapter(id) {
    if (chapter !== id) {
      if (await onChapterChange(id)) setClosed(null)
      return
    }
    setClosed(closed === id ? null : id)
  }

  return <aside className="storyboard-directory" aria-label="分镜目录">
    <header className="storyboard-directory-heading">
      <strong>分镜目录</strong><span>{groups.length} 组</span>
    </header>
    <div className="storyboard-directory-scroll">
      <button type="button" className="storyboard-chapter-row" aria-pressed={chapter === allChapters}
        onClick={async () => { if (await onChapterChange(allChapters)) setClosed(null) }}>
        <Film size={15} aria-hidden="true" /><strong>全部镜头</strong>
      </button>
      {groups.map(({ id, title, shots }) => {
        const expanded = activeChapter === id && closed !== id
        return <div key={id}>
          <button type="button" className="storyboard-chapter-row" aria-expanded={expanded}
            data-selected={chapter === id} onClick={() => toggleChapter(id)}>
            {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
            <Folder size={15} aria-hidden="true" /><strong>{title}</strong><small>{shots.length}</small>
          </button>
          {expanded && <ul className="storyboard-directory-items">
            {shots.map((shot) => <li key={shot.id}>
                <button type="button" className="storyboard-directory-item" aria-current={shot.id === selectedShot?.id || undefined}
                  title={shot.title} onClick={() => onShotChange(shot, id)}>
                  <Film size={13} aria-hidden="true" />
                  {shot.shotNumber && <small>{shot.shotNumber}</small>}
                  <span>{shot.title || '未命名镜头'}</span>
                </button>
              </li>)}
            {shots.length === 0 && <li className="storyboard-directory-empty">本章暂无镜头</li>}
          </ul>}
        </div>
      })}
    </div>
    <button type="button" className="workspace-secondary storyboard-directory-create" onClick={onCreate} disabled={saving}>
      <Plus size={15} />新增镜头
    </button>
  </aside>
}
