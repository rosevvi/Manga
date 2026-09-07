import { useEffect, useRef, useState } from 'react'
import { Check, ChevronDown, Globe2 } from 'lucide-react'
import { LANGUAGE_OPTIONS } from '../constants/locale'
import { useLanguage } from '../i18n/LanguageContext'
import './language-selector.css'

/** 提供全站通用的语言选择下拉菜单。 */
function LanguageSelector({ compact = false }) {
  const { locale, setLocale, translate } = useLanguage()
  const [open, setOpen] = useState(false)
  const containerRef = useRef(null)
  const activeOption = LANGUAGE_OPTIONS.find((option) => option.locale === locale) ?? LANGUAGE_OPTIONS[0]

  useEffect(() => {
    /** 点击控件外部时关闭语言菜单。 */
    const closeOnOutsideClick = (event) => {
      if (!containerRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [])

  /** 应用语言并关闭菜单。 */
  const selectLocale = (nextLocale) => {
    setLocale(nextLocale)
    setOpen(false)
  }

  return (
    <div className={compact ? 'app-language-selector compact' : 'app-language-selector'} ref={containerRef}>
      <button
        className="app-language-button"
        type="button"
        aria-label={translate('common.language')}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        <Globe2 size={compact ? 17 : 16} />
        <span>{activeOption.shortLabel}</span>
        <ChevronDown className={open ? 'open' : ''} size={compact ? 13 : 14} />
      </button>
      {open && (
        <div className="app-language-menu" role="menu">
          {LANGUAGE_OPTIONS.map((option) => (
            <button
              className={option.locale === locale ? 'active' : ''}
              type="button"
              role="menuitemradio"
              aria-checked={option.locale === locale}
              key={option.locale}
              onClick={() => selectLocale(option.locale)}
            >
              <span>{option.label}</span>
              {option.locale === locale && <Check size={14} />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default LanguageSelector
