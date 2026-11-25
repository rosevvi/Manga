import { CHAPTER_ASSET_CATEGORY } from '../constants/project'
import { CHAPTER_IMAGE_DEFAULTS } from './scriptChapterAssetImages'

/** 章节资产暂由前端示例提供，接入接口后可按相同结构传入真实资产。 */
const PREVIEW_SHEET = '/assets/chapter-assets-preview.jpg'
const tile = (column, row) => ({ src: PREVIEW_SHEET, tile: [column, row] })

const art = {
  front: tile(0, 0), side: tile(1, 0), back: tile(2, 0), expressions: { ...tile(3, 0), fit: 'contain' },
  disciple: tile(0, 1), scholar: tile(1, 1), temple: tile(2, 1), spring: tile(3, 1),
  sword: tile(0, 2), pendant: tile(1, 2), vial: tile(2, 2), letter: tile(3, 2),
  wind: tile(0, 3), weary: tile(1, 3), valley: tile(2, 3), turnaround: { ...tile(3, 3), fit: 'contain' },
}

const negativePrompt = '低清晰度，五官比例错误，背景杂乱，现代服饰，水印'
const image = (id, label, type, prompt, referenceImageIds = []) => ({
  ...CHAPTER_IMAGE_DEFAULTS,
  id, label, type, prompt, referenceImageIds, preview: art[id],
  negativePrompt, generatedAt: '2026-11-24T23:50:00', version: 'v1.0',
})

const characterPrompt = '银发高髻的山门长老，眉眼沉静，身着浅色云纹道袍，青灰滚边。国风动画，颜色层次克制，保留服装纹样与面部特征。'
const characterReferences = ['side', 'back']
const CHARACTER_DESIGN_TYPE = Object.freeze({ FRONT: '角色设定图 · 正面', SIDE: '角色设定图 · 侧面', BACK: '角色设定图 · 背面' })

/** 即使资产暂无对应图片，也保留完整的角色图片分组。 */
function characterGroups({ design, turnaround = [], expressions = [], states = [], environment = [] }) {
  return [
    { id: 'design', title: '角色设定图', types: Object.values(CHARACTER_DESIGN_TYPE), images: design,
      sections: [{ id: 'turnaround', title: '角色三视图', images: turnaround }] },
    { id: 'expressions', title: '角色表情集', images: expressions },
    { id: 'states', title: '角色状态图', images: states },
    { id: 'environment', title: '角色场景图', images: environment },
  ]
}

export const CHAPTER_ASSET_PREVIEW = {
  isPreview: true,
  cover: art.temple,
  [CHAPTER_ASSET_CATEGORY.CHARACTER]: [
    {
      id: 'guardian', name: '沈观澜', role: '主角', preview: art.front, baselineImageId: 'front',
      details: [['身份', '云岫山门长老'], ['年龄段', '中年'], ['外观特征', '银发高髻，面容清瘦'], ['服饰风格', '浅色云纹道袍，青灰滚边']],
      groups: characterGroups({
        design: [
          image('front', '正面', CHARACTER_DESIGN_TYPE.FRONT, characterPrompt, characterReferences),
          image('side', '侧面', CHARACTER_DESIGN_TYPE.SIDE, `${characterPrompt} 侧面轮廓，完整保留发髻与领口。`, ['front']),
          image('back', '背面', CHARACTER_DESIGN_TYPE.BACK, `${characterPrompt} 背面视角，展示道袍剪裁和纹样。`, ['front']),
        ],
        turnaround: [
          image('turnaround', '正侧背三视图', '角色三视图', `${characterPrompt} 全身正面、侧面和背面并排，统一比例和站姿。`, characterReferences),
        ],
        expressions: [
          image('expressions', '四款表情', '角色表情集', `${characterPrompt} 以平静、担忧、专注、惊讶的四种神态绘制面部表情集。`, characterReferences),
        ],
        states: [
          image('wind', '御风状态', '角色状态图', `${characterPrompt} 周身流动青蓝风息，衣袖被风扬起。`, characterReferences),
          image('weary', '疲惫状态', '角色状态图', `${characterPrompt} 衣袍沾染薄尘，在石阶边略作歇息。`, characterReferences),
        ],
        environment: [
          image('valley', '山门远眺', '角色场景图', `${characterPrompt} 站在山门石崖远眺雾色山谷，中远景。`, characterReferences),
        ],
      }),
    },
    {
      id: 'disciple', name: '林行舟', role: '主要角色', preview: art.disciple,
      details: [['身份', '外门弟子'], ['年龄段', '青年'], ['外观特征', '黑发束起，眉眼凌厉'], ['服饰风格', '深青劲装，便于行走']],
      groups: characterGroups({ design: [
        image('disciple', '正面', CHARACTER_DESIGN_TYPE.FRONT, '黑发束起的年轻剑士，深青劲装，清楚描绘眼神与领口。'),
      ] }),
    },
    {
      id: 'scholar', name: '许知微', role: '重要角色', preview: art.scholar,
      details: [['身份', '藏书阁弟子'], ['年龄段', '青年'], ['外观特征', '发间缀花饰，神情从容'], ['服饰风格', '青白长衫']],
      groups: characterGroups({ design: [
        image('scholar', '正面', CHARACTER_DESIGN_TYPE.FRONT, '青白长衫的年轻女学士，发间缀白色花饰，柔和侧光。'),
      ] }),
    },
  ],
  [CHAPTER_ASSET_CATEGORY.SCENE]: [
    {
      id: 'mountain', name: '云岫山门', preview: art.temple,
      details: [['地点', '云岫山脉主峰'], ['环境', '雾气、石阶、临崖殿宇'], ['光照', '清晨散射光']],
      groups: [{ id: 'scene-design', title: '场景设定图', images: [
        image('temple', '山门全景', '场景设定图', '层雾间的山门古殿，石阶通往临崖平台，远景山峦与少量淡紫花木。'),
      ] }],
    },
    {
      id: 'spring', name: '碧泉洞', preview: art.spring,
      details: [['地点', '山脉深处'], ['环境', '洞壁、泉水、湿润岩石'], ['光照', '灵泉蓝色反光']],
      groups: [{ id: 'scene-design', title: '场景设定图', images: [
        image('spring', '灵泉洞全景', '场景设定图', '幽深洞窟内的青蓝泉眼，水面微亮，洞壁由深至浅分出层次。'),
      ] }],
    },
  ],
  [CHAPTER_ASSET_CATEGORY.PROP]: [
    { id: 'sword', name: '长剑', preview: art.sword, details: [['材质', '精钢与缠绳']],
      groups: [{ id: 'prop-design', title: '道具设定图', images: [
        image('sword', '长剑全貌', '道具设定图', '斜置的细长银色长剑，深色素底，剑刃、剑柄和缠绳纹理清晰。'),
      ] }] },
    { id: 'pendant', name: '灵玉', preview: art.pendant, details: [['材质', '青玉']],
      groups: [{ id: 'prop-design', title: '道具设定图', images: [
        image('pendant', '灵玉全貌', '道具设定图', '半透青玉圆佩，边缘圆润，弱光下可见温润的玉质。'),
      ] }] },
    { id: 'vial', name: '药瓶', preview: art.vial, details: [['材质', '琥珀玻璃与黄铜']],
      groups: [{ id: 'prop-design', title: '道具设定图', images: [
        image('vial', '药瓶全貌', '道具设定图', '暖色透明药瓶，黄铜瓶塞，深底突出器型与玻璃反光。'),
      ] }] },
    { id: 'letter', name: '密函', preview: art.letter, details: [['材质', '旧纸与火漆']],
      groups: [{ id: 'prop-design', title: '道具设定图', images: [
        image('letter', '密函全貌', '道具设定图', '折叠的旧纸密函，残留红色火漆印，柔和的暖色边光。'),
      ] }] },
  ],
  relationships: [
    { id: 'mentor', from: 'guardian', to: 'disciple', label: '师徒' },
    { id: 'school', from: 'guardian', to: 'scholar', label: '同门' },
  ],
}

