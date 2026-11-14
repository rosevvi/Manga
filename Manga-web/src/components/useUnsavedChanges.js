import { useEffect, useRef } from 'react'

const BEFORE_NAVIGATE = 'manga:before-editor-navigation'
const CONFIRM_MESSAGE = '有未保存的修改，确定放弃并离开吗？'

/** 控制台所有入口在改变 URL 或卸载编辑器前共用此确认。 */
export function confirmCreativeNavigation() {
  return window.dispatchEvent(new Event(BEFORE_NAVIGATE, { cancelable: true }))
}

/** 保护刷新、标签关闭和控制台内导航，不把草稿保存到浏览器。 */
export default function useUnsavedChanges(dirty) {
  const current = useRef(dirty)
  const location = useRef(window.location.href)
  current.current = dirty
  location.current = window.location.href
  useEffect(() => {
    const leave = (event) => { if (current.current) { event.preventDefault(); event.returnValue = '' } }
    const navigate = (event) => { if (current.current && !window.confirm(CONFIRM_MESSAGE)) event.preventDefault() }
    const back = (event) => {
      if (current.current && !window.confirm(CONFIRM_MESSAGE)) {
        window.history.pushState(null, '', location.current)
        event.stopImmediatePropagation()
      }
    }
    window.addEventListener('beforeunload', leave)
    window.addEventListener(BEFORE_NAVIGATE, navigate)
    window.addEventListener('popstate', back, true)
    return () => { window.removeEventListener('beforeunload', leave); window.removeEventListener(BEFORE_NAVIGATE, navigate); window.removeEventListener('popstate', back, true) }
  }, [])
}
