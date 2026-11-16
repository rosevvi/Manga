import { useCallback, useEffect, useLayoutEffect, useRef } from 'react'
import { useLanguage } from '../i18n/LanguageContext'
import { useWorkspaceConfirm } from './WorkspaceConfirmDialog'
import { ensureWorkspaceHistoryIndex, workspaceHistoryIndex } from './workspaceHistory'

const BEFORE_NAVIGATE = 'manga:before-editor-navigation'

export function useDiscardChangesConfirmation() {
  const confirm = useWorkspaceConfirm()
  const { translate } = useLanguage()
  return useCallback((message = translate('confirm.discardMessage')) => confirm({
    title: translate('confirm.discardTitle'), message, confirmLabel: translate('confirm.discard'),
  }), [confirm, translate])
}

/** 控制台内导航先检查草稿，再等待居中确认。 */
export function useConfirmCreativeNavigation() {
  const discard = useDiscardChangesConfirmation()
  return useCallback(() => window.dispatchEvent(new Event(BEFORE_NAVIGATE, { cancelable: true })) || discard(), [discard])
}

const currentEntry = () => ({ index: workspaceHistoryIndex(), url: window.location.href, state: window.history.state })
const sameEntry = (left, right) => left.index === right.index && left.url === right.url

/** 草稿保护覆盖应用内导航和同页历史遍历，跨文档离开由浏览器处理。 */
export default function useUnsavedChanges(dirty) {
  const discard = useDiscardChangesConfirmation()
  const current = useRef(dirty)
  const position = useRef(null)
  const transition = useRef(null)
  current.current = dirty

  useLayoutEffect(() => {
    if (transition.current) return
    ensureWorkspaceHistoryIndex()
    position.current = currentEntry()
  })

  useEffect(() => {
    const leave = (event) => { if (current.current) { event.preventDefault(); event.returnValue = '' } }
    const navigate = (event) => { if (current.current) event.preventDefault() }

    /** 确认后遍历原历史栈；无法定位的旧条目仅作一次原地恢复。 */
    const resumeFromOrigin = (pending) => {
      if (!sameEntry(currentEntry(), pending.origin) || pending.decision === undefined) return
      if (!pending.decision) {
        transition.current = null
      } else if (pending.offset !== null) {
        pending.replaying = true
        window.history.go(pending.offset)
      } else {
        pending.replaying = true
        window.history.replaceState(pending.target.state, '', pending.target.url)
        window.dispatchEvent(new PopStateEvent('popstate', { state: pending.target.state }))
      }
    }

    const restoreOrigin = (pending, entry) => {
      if (!sameEntry(entry, pending.origin)) {
        if (entry.index !== null && entry.index !== pending.origin.index) {
          window.history.go(pending.origin.index - entry.index)
          return
        }
        pending.offset = null
        window.history.pushState(pending.origin.state, '', pending.origin.url)
      }
      if (pending.requested) { resumeFromOrigin(pending); return }
      pending.requested = true
      void discard().then((accepted) => {
        if (transition.current !== pending) return
        pending.decision = accepted
        resumeFromOrigin(pending)
      })
    }

    const back = (event) => {
      const entry = currentEntry()
      let pending = transition.current
      if (pending?.replaying || (!pending && !current.current)) {
        transition.current = null
        position.current = entry
        return
      }
      event.stopImmediatePropagation()
      if (!pending) {
        const origin = position.current
        const offset = entry.index === null || entry.index === origin.index ? null : entry.index - origin.index
        pending = { origin, target: entry, offset }
        transition.current = pending
      }
      restoreOrigin(pending, entry)
    }
    window.addEventListener('beforeunload', leave)
    window.addEventListener(BEFORE_NAVIGATE, navigate)
    window.addEventListener('popstate', back, true)
    return () => {
      transition.current = null
      window.removeEventListener('beforeunload', leave)
      window.removeEventListener(BEFORE_NAVIGATE, navigate)
      window.removeEventListener('popstate', back, true)
    }
  }, [discard])
}
