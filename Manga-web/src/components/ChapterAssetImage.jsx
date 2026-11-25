import MediaImage from './MediaImage'

const SHEET_GRID_SIZE = 4

/** 展示示例图集中的图块；真实资产可直接提供图片地址。 */
export default function ChapterAssetImage({ preview, alt = '' }) {
  if (!preview?.tile) return <MediaImage src={preview?.src} alt={alt} />

  const [column, row] = preview.tile
  const step = 100 / (SHEET_GRID_SIZE - 1)
  return <span className="chapter-asset-art" role={alt ? 'img' : undefined} aria-label={alt || undefined}
    aria-hidden={alt ? undefined : true}>
    <span className="chapter-asset-art-cell" style={{
      backgroundImage: `url("${preview.src}")`,
      backgroundPosition: `${column * step}% ${row * step}%`,
      backgroundSize: `${SHEET_GRID_SIZE * 100}% ${SHEET_GRID_SIZE * 100}%`,
    }} />
  </span>
}
