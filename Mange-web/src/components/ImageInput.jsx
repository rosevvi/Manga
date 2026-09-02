import { useRef, useState } from 'react'
import { ImagePlus, Images, Link, LoaderCircle, Upload, X } from 'lucide-react'
import { uploadProjectImage } from '../api/projectApi'
import { useLanguage } from '../i18n/LanguageContext'
import './image-input.css'

/** 图片输入组件，支持本地拖拽上传、点击上传、URL 手动填写和预览清除。 */
function ImageInput({
  accessToken,
  value,
  onChange,
  maxLength,
  uploadSubDir = 'images',
  placeholder,
  previewClassName = '',
  onError,
}) {
  const { translate } = useLanguage()
  const fileRef = useRef(null)
  const [mode, setMode] = useState('upload')
  const [uploading, setUploading] = useState(false)

  const uploadFile = async (file) => {
    if (!file.type.startsWith('image/')) {
      onError?.(translate('common.imageTypeUnsupported'))
      return
    }
    setUploading(true)
    try {
      const result = await uploadProjectImage(accessToken, file, uploadSubDir)
      onChange(result.url)
    } catch (requestError) {
      onError?.(requestError.message)
    } finally {
      setUploading(false)
    }
  }

  const handleFileChange = (event) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    void uploadFile(file)
  }

  const handleDrop = (event) => {
    event.preventDefault()
    if (uploading) return
    const file = event.dataTransfer.files?.[0]
    if (!file) return
    void uploadFile(file)
  }

  const clearImage = () => {
    onChange('')
    if (fileRef.current) fileRef.current.value = ''
  }

  const commitUrl = (rawValue) => {
    const nextValue = rawValue.trim()
    if (nextValue !== value) {
      onChange(nextValue)
    }
  }

  return (
    <div className="manga-image-input">
      <div className="manga-image-tabs" role="group" aria-label={translate('common.imageInputMode')}>
        <button className={mode === 'upload' ? 'active' : ''} type="button" onClick={() => setMode('upload')}>
          <Upload size={12} />{translate('common.upload')}
        </button>
        <button className={mode === 'url' ? 'active' : ''} type="button" onClick={() => setMode('url')}>
          <Link size={12} />{translate('common.link')}
        </button>
      </div>

      {value ? (
        <div className={`manga-image-preview ${previewClassName}`}>
          <img src={value} alt="" />
          <button type="button" aria-label={translate('common.clearImage')} onClick={clearImage}>
            <X size={13} />
          </button>
        </div>
      ) : mode === 'upload' ? (
        <button
          className={uploading ? 'manga-image-drop uploading' : 'manga-image-drop'}
          type="button"
          disabled={uploading}
          onClick={() => fileRef.current?.click()}
          onDragOver={(event) => event.preventDefault()}
          onDrop={handleDrop}
        >
          {uploading ? <LoaderCircle className="spin" size={21} /> : <ImagePlus size={22} />}
          <span>{translate(uploading ? 'common.uploading' : 'common.dropImage')}</span>
        </button>
      ) : (
        <div className="manga-image-empty">
          <Images size={22} />
          <span>{translate('common.noImage')}</span>
        </div>
      )}

      <input
        ref={fileRef}
        hidden
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        onChange={handleFileChange}
      />

      {mode === 'url' && (
        <input
          className="manga-image-url"
          key={value || 'empty-url'}
          defaultValue={value}
          maxLength={maxLength}
          onBlur={(event) => commitUrl(event.currentTarget.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault()
              commitUrl(event.currentTarget.value)
              event.currentTarget.blur()
            }
          }}
          placeholder={placeholder ?? translate('common.imageUrlPlaceholder')}
        />
      )}
    </div>
  )
}

export default ImageInput
