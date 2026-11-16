import { createContext, useCallback, useContext, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { CircleAlert, Trash2 } from 'lucide-react'
import { useLanguage } from '../i18n/LanguageContext'
import './project-module.css'

const WorkspaceConfirmContext = createContext(null)

/** 单一确认入口，阻止重复点击叠加确认或提交同一个操作。 */
export function WorkspaceConfirmProvider({ children }) {
  const { translate } = useLanguage()
  const dialog = useRef(null)
  const pending = useRef(null)
  const [request, setRequest] = useState(null)
  const titleId = useId()
  const messageId = useId()

  const confirm = useCallback((options) => {
    if (pending.current) return Promise.resolve(false)
    return new Promise((resolve) => {
      pending.current = resolve
      setRequest(options)
    })
  }, [])

  const finish = (accepted) => {
    const resolve = pending.current
    pending.current = null
    dialog.current.close()
    setRequest(null)
    resolve?.(accepted)
  }

  useLayoutEffect(() => {
    if (request && !dialog.current.open) dialog.current.showModal()
  }, [request])

  useEffect(() => () => { pending.current?.(false); pending.current = null }, [])

  const Icon = request?.danger ? Trash2 : CircleAlert
  return <WorkspaceConfirmContext.Provider value={confirm}>
    {children}
    <dialog ref={dialog} className="workspace-modal workspace-confirm-dialog" aria-labelledby={titleId} aria-describedby={messageId}
      onCancel={(event) => { event.preventDefault(); finish(false) }}
      onClick={(event) => { if (event.target === event.currentTarget) finish(false) }}>
      {request && <div className="workspace-confirm-content">
        <div className="workspace-confirm-description">
          <span className="workspace-confirm-icon" data-danger={Boolean(request.danger)}><Icon size={19} aria-hidden="true" /></span>
          <div><h2 id={titleId}>{request.title}</h2><p id={messageId}>{request.message}</p></div>
        </div>
        <footer>
          <button type="button" className="workspace-confirm-cancel" autoFocus onClick={() => finish(false)}>{translate('common.cancel')}</button>
          <button type="button" className="workspace-primary" data-danger={Boolean(request.danger)}
            onClick={() => finish(true)}>{request.confirmLabel}</button>
        </footer>
      </div>}
    </dialog>
  </WorkspaceConfirmContext.Provider>
}

/** 返回异步确认函数，取消、Esc 和点击遮罩均解析为 false。 */
export function useWorkspaceConfirm() {
  return useContext(WorkspaceConfirmContext)
}
