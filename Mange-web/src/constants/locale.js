/** 平台支持的语言编码。 */
export const APP_LOCALE = Object.freeze({
  ENGLISH: 'en',
  SIMPLIFIED_CHINESE: 'zh-CN',
})

/** 默认语言与本地存储配置。 */
export const LOCALE_CONFIG = Object.freeze({
  defaultLocale: APP_LOCALE.ENGLISH,
  storageKey: 'manga.locale',
})

/** 语言选择器展示项。 */
export const LANGUAGE_OPTIONS = Object.freeze([
  { locale: APP_LOCALE.ENGLISH, shortLabel: 'EN', label: 'English' },
  { locale: APP_LOCALE.SIMPLIFIED_CHINESE, shortLabel: '中', label: '简体中文' },
])
