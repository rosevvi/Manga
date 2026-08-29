import { createContext, useContext, useMemo, useState } from 'react'
import { APP_LOCALE, LANGUAGE_OPTIONS, LOCALE_CONFIG } from '../constants/locale.js'
import { MESSAGES } from './messages.js'

const LanguageContext = createContext(null)
const SUPPORTED_LOCALES = new Set(LANGUAGE_OPTIONS.map(({ locale }) => locale))
const TOKEN_PATTERN = /\{(\w+)\}/g

/** 从本地存储读取有效语言，异常或未知值回退到默认语言。 */
function readStoredLocale() {
  try {
    const storedLocale = window.localStorage.getItem(LOCALE_CONFIG.storageKey)
    return SUPPORTED_LOCALES.has(storedLocale) ? storedLocale : LOCALE_CONFIG.defaultLocale
  } catch {
    return LOCALE_CONFIG.defaultLocale
  }
}

/** 为应用提供语言状态、持久化切换能力和文本翻译函数。 */
export function LanguageProvider({ children }) {
  const [locale, setLocaleState] = useState(readStoredLocale)

  /** 切换到受支持语言并保存用户选择。 */
  const setLocale = (nextLocale) => {
    if (!SUPPORTED_LOCALES.has(nextLocale)) return
    setLocaleState(nextLocale)
    try {
      window.localStorage.setItem(LOCALE_CONFIG.storageKey, nextLocale)
    } catch {
      // 存储不可用时仍保留当前页面内的语言切换结果。
    }
  }

  /** 根据键值读取当前语言文本，并替换动态参数。 */
  const translate = (key, parameters = {}) => {
    const message = MESSAGES[locale]?.[key] ?? MESSAGES[APP_LOCALE.ENGLISH]?.[key] ?? key
    return message.replace(TOKEN_PATTERN, (_, token) => parameters[token] ?? `{${token}}`)
  }

  const value = useMemo(() => ({ locale, setLocale, translate }), [locale])
  return <LanguageContext.Provider value={value}>{children}</LanguageContext.Provider>
}

/** 读取全局语言能力。 */
export function useLanguage() {
  const context = useContext(LanguageContext)
  if (!context) throw new Error('useLanguage must be used within LanguageProvider')
  return context
}
