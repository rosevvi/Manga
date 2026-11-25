/** 章节资产示例的图片参数，为接入生成服务保留独立字段。 */
export const CHAPTER_IMAGE_SIZES = [
  { value: '1536x1024', label: '1536 × 1024（3:2）' },
  { value: '1024x1024', label: '1024 × 1024（1:1）' },
  { value: '1024x1536', label: '1024 × 1536（2:3）' },
]

export const CHAPTER_IMAGE_MODELS = [
  { value: 'comic-v3', label: '国风漫剧 / Comic V3' },
  { value: 'ink-v3', label: '水墨国风 / Comic V3' },
]

export const CHAPTER_IMAGE_LIMITS = Object.freeze({ prompt: 500, negativePrompt: 300 })
export const CHAPTER_IMAGE_DEFAULTS = Object.freeze({
  size: CHAPTER_IMAGE_SIZES[0].value,
  model: CHAPTER_IMAGE_MODELS[0].value,
  prompt: '',
  negativePrompt: '',
  referenceImageIds: [],
})

/** 将设定图和它的三视图等子分组展平，用于定位图片和参考图。 */
export function flattenChapterImageGroups(groups) {
  return groups.flatMap((group) => [group, ...flattenChapterImageGroups(group.sections || [])])
}

/** 更新当前资产的图片配置，已有图片保持在原来的顺序。 */
export function upsertChapterImage(groups, groupId, image) {
  return groups.map((group) => {
    if (group.id !== groupId) {
      return group.sections ? { ...group, sections: upsertChapterImage(group.sections, groupId, image) } : group
    }
    const exists = group.images.some((item) => item.id === image.id)
    return {
      ...group,
      images: exists ? group.images.map((item) => item.id === image.id ? image : item) : [...group.images, image],
    }
  })
}
