import { CHAPTER_ASSET_CATEGORY } from '../constants/project'

export const CHAPTER_IMAGE_LIMITS = Object.freeze({ prompt: 500, negativePrompt: 300, references: 16 })

const designTypes = [
  { value: 'FRONT', label: '角色设定图 · 正面' },
  { value: 'SIDE', label: '角色设定图 · 侧面' },
  { value: 'BACK', label: '角色设定图 · 背面' },
]
const group = (id, title, value, label = title) => ({ id, title, types: [{ value, label }] })
const visualGroups = {
  [CHAPTER_ASSET_CATEGORY.CHARACTER]: [
    { id: 'design', title: '角色设定图', types: designTypes, sections: [group('turnaround', '角色三视图', 'TURNAROUND')] },
    group('expressions', '角色表情集', 'EXPRESSIONS'),
    group('states', '角色状态图', 'STATE'),
    group('environment', '角色场景图', 'CONTEXT'),
  ],
  [CHAPTER_ASSET_CATEGORY.SCENE]: [group('scene-design', '场景设定图', 'SCENE_DESIGN')],
  [CHAPTER_ASSET_CATEGORY.PROP]: [group('prop-design', '道具设定图', 'PROP_DESIGN')],
}

export const ASSET_DETAILS = {
  [CHAPTER_ASSET_CATEGORY.CHARACTER]: [
    ['identityDescription', '身份', 300], ['ageGroup', '年龄段', 40], ['appearanceDescription', '外观特征', 800],
    ['costumeDescription', '服饰风格', 800], ['roleLabel', '角色定位', 40],
  ],
  [CHAPTER_ASSET_CATEGORY.SCENE]: [
    ['locationDescription', '地点', 300], ['environmentDescription', '环境', 800], ['lightingDescription', '光照', 300],
  ],
  [CHAPTER_ASSET_CATEGORY.PROP]: [['materialDescription', '材质', 300]],
}

/** 将真实图片映射到工作台分组，空分组也有生成入口。 */
export function chapterAssetView(asset, category) {
  const makeGroups = (groups) => groups.map((item) => ({
    ...item,
    images: asset.visuals.filter((visual) => item.types.some((type) => type.value === visual.visualKind)).map((visual) => ({
      ...visual, label: visual.displayName,
      preview: visual.previewUrl ? { src: visual.previewUrl } : null,
    })),
    sections: item.sections ? makeGroups(item.sections) : undefined,
  }))
  const groups = makeGroups(visualGroups[category])
  const cover = asset.visuals.find((visual) => visual.id === asset.primaryVisualId && visual.previewUrl)
    || asset.visuals.find((visual) => visual.previewUrl)
  return {
    ...asset, category, groups, name: asset.fields.displayName, role: asset.fields.roleLabel,
    preview: cover ? { src: cover.previewUrl } : null,
    details: [['概述', asset.fields.overview], ...ASSET_DETAILS[category]
      .filter(([key]) => key !== 'roleLabel').map(([key, label]) => [label, asset.fields[key]])].filter(([, value]) => value),
  }
}

export function flattenChapterImageGroups(groups) {
  return groups.flatMap((item) => [item, ...flattenChapterImageGroups(item.sections || [])])
}
