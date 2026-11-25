import MediaImage from './MediaImage'

/** 从当前产物的预览地址显示资产图。 */
export default function ChapterAssetImage({ preview, alt = '' }) {
  return <MediaImage src={preview?.src} alt={alt} />
}
