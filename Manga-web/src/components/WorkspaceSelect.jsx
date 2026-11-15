import { useEffect, useId, useRef, useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import './creative-workbench.css'

/** 共用深色主题选择器，支持键盘方向键、Escape 和点击外部关闭。 */
export default function WorkspaceSelect({ value, options, onChange, label, disabled, placeholder = '请选择' }) {
  const [open, setOpen] = useState(false)
  const [focused, setFocused] = useState(0)
  const root = useRef(null)
  const id = useId()
  const selected = options.find((option) => String(option.value) === String(value))
  const SelectedIcon = selected?.icon
  useEffect(() => {
    if (!open) return undefined
    const close = (event) => { if (!root.current?.contains(event.target)) setOpen(false) }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [open])
  const choose = (option) => { onChange(option.value); setOpen(false); root.current?.querySelector('button')?.focus() }
  return <div className="workbench-select" ref={root} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false) }}>
    <button type="button" className="workbench-select-trigger" data-danger={selected?.danger || undefined} role="combobox" aria-label={label} aria-controls={id}
      aria-expanded={open} aria-haspopup="listbox" disabled={disabled} onClick={() => {
        setFocused(Math.max(0, options.findIndex((option) => String(option.value) === String(value)))); setOpen((current) => !current)
      }} onKeyDown={(event) => {
        if (event.key === 'Escape') { event.stopPropagation(); setOpen(false) }
        if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
          event.preventDefault(); setOpen(true)
          setFocused((current) => open ? Math.max(0, Math.min(options.length - 1, current + (event.key === 'ArrowDown' ? 1 : -1)))
            : Math.max(0, options.findIndex((option) => String(option.value) === String(value))))
        }
        if (event.key === 'Home' || event.key === 'End') { event.preventDefault(); setOpen(true); setFocused(event.key === 'Home' ? 0 : options.length - 1) }
        if (event.key === 'Enter' && open && options[focused]) { event.preventDefault(); choose(options[focused]) }
      }} aria-activedescendant={open ? `${id}-${focused}` : undefined}>
      <span className="workbench-select-value">{SelectedIcon && <SelectedIcon size={14} aria-hidden="true" />}{selected?.label || placeholder}</span><ChevronDown size={14} />
    </button>
    {open && <div id={id} role="listbox" aria-label={label} className="workbench-select-options">
      {options.length === 0 && <small>暂无可用选项</small>}
      {options.map((option, index) => <button id={`${id}-${index}`} key={option.value} type="button" role="option" tabIndex={-1}
        aria-selected={String(option.value) === String(value)} className={focused === index ? 'focused' : ''} data-danger={option.danger || undefined}
        onPointerMove={() => setFocused(index)} onClick={() => choose(option)}>
        <span className="workbench-option-content">{option.icon && <option.icon size={16} aria-hidden="true" />}
          <span>{option.label}{option.description && <small className="workbench-option-description">{option.description}</small>}</span></span>
        {String(option.value) === String(value) && <Check size={13} />}</button>)}
    </div>}
  </div>
}
