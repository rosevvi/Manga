/** 对配置字段做语义比较，能力集合顺序变化不会显示为业务差异。 */
export function compareAgentDefinitions(left, right) {
  if (!left || !right) return []
  const labels = { role: '角色', displayName: '名称', description: '职责', systemPrompt: '系统提示词', capabilities: '能力白名单',
    permissionPolicy: '权限上限', maxTurns: '推理轮数', model: '模型策略', budget: '预算上限' }
  const render = (key, value) => {
    if (typeof value === 'string') return value
    if (key === 'capabilities') return JSON.stringify([...value].sort(), null, 2)
    if (key === 'model') return JSON.stringify({ ...value, providers: [...value.providers].sort(), models: [...value.models].sort() }, null, 2)
    return JSON.stringify(value, null, 2)
  }
  return Object.entries(labels).flatMap(([key, label]) => {
    const before = render(key, left[key]); const after = render(key, right[key])
    return before === after ? [] : [{ key, label, before, after }]
  })
}
