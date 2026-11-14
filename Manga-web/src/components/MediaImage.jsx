import { useState } from 'react'
import { ImageOff } from 'lucide-react'
import { resolveMediaUrl } from '../api/mediaUrl'
import { useLanguage } from '../i18n/LanguageContext'

/** 图片读取失败时显示本地化占位。 */
export default function MediaImage({ src, alt = '', fallback, showErrorText = false }) {
  const { translate } = useLanguage()
  const url = resolveMediaUrl(src)
  return <ImagePreview key={url || src || ''} url={url} alt={alt} fallback={fallback}
    unavailable={Boolean(src)} errorText={translate('common.imageUnavailable')} showErrorText={showErrorText} />
}

/** 地址变更时重新挂载，使新图片可以独立加载。 */
function ImagePreview({ url, alt, fallback, unavailable, errorText, showErrorText }) {
  const [failed, setFailed] = useState(false)
  if (url && !failed) return <img src={url} alt={alt} referrerPolicy="no-referrer" onError={() => setFailed(true)} />
  if (!unavailable) return fallback ?? <ImageOff aria-hidden="true" size={24} />

  return <span className="media-image-unavailable" role="img" aria-label={errorText} title={errorText}>
    <ImageOff aria-hidden="true" size={24} />
    {showErrorText && <span>{errorText}</span>}
  </span>
}
