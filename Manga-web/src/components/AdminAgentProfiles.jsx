import { useEffect, useRef, useState } from 'react'
import { listAdminAgents, getAdminAgent, getAgentVersion, getAgentCapabilities, getAgentModelProviders, getAgentAudits,
  createAgentProfile, createAgentDraft, updateAgentDraft, publishAgentVersion, rollbackAgentVersion, setAgentEnabled,
  previewAgentVersion, testAgentVersion } from '../api/agentProfileApi'
import { getAiProviderConfigs } from '../api/aiProviderApi'
import { getCreativeConversationHistory } from '../api/creativeConversationApi'
import { cancelCreativeRun } from '../api/creativeRunApi'
import { AGENT_VERSION_STATUS, AGENT_AUDIT_PAGE_SIZE, AGENT_ROLE_LABEL, AGENT_MODEL_SELECTION_LABEL,
  AGENT_BUDGET_LABEL, AGENT_ACTION_LABEL, newAgentDefinition } from '../constants/agentProfile'
import { APPROVAL_POLL_MS } from '../constants/changeSet'
import { compareAgentDefinitions } from './agentProfileComparison'
import CreativeRunGraph from './CreativeRunGraph'
import './admin-agent-profiles.css'

/** 管理员维护纯数据 Agent 定义，发布历史只读，测试走独立只读 Run。 */
export default function AdminAgentProfiles({ accessToken, projects = [] }) {
  const [items, setItems] = useState([])
  const [options, setOptions] = useState([])
  const [providers, setProviders] = useState([])
  const [models, setModels] = useState([])
  const [key, setKey] = useState('')
  const [newKey, setNewKey] = useState('')
  const [creating, setCreating] = useState(false)
  const [detail, setDetail] = useState(null)
  const [number, setNumber] = useState(null)
  const [version, setVersion] = useState(null)
  const [definition, setDefinition] = useState(newAgentDefinition)
  const [modelNamesText, setModelNamesText] = useState('')
  const [compareNumber, setCompareNumber] = useState('')
  const [comparison, setComparison] = useState(null)
  const [audits, setAudits] = useState([])
  const [moreAudits, setMoreAudits] = useState(false)
  const [comment, setComment] = useState('')
  const [testProject, setTestProject] = useState('')
  const [testModel, setTestModel] = useState('')
  const [testInstruction, setTestInstruction] = useState('检查当前项目的剧本结构，并给出专业建议。')
  const [preview, setPreview] = useState(null)
  const [testRun, setTestRun] = useState(null)
  const [testMessages, setTestMessages] = useState([])
  const [testActive, setTestActive] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const scope = useRef(0)
  const canEdit = creating || version?.status === AGENT_VERSION_STATUS.DRAFT

  useEffect(() => {
    let stopped = false
    Promise.all([listAdminAgents(accessToken), getAgentCapabilities(accessToken), getAgentModelProviders(accessToken), getAiProviderConfigs(accessToken)])
      .then(([list, caps, protocols, configs]) => { if (!stopped) { setItems(list); setOptions(caps); setProviders(protocols); setModels(configs); setKey(list[0]?.profileId || '') } })
      .catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true; scope.current += 1 }
  }, [accessToken])

  useEffect(() => {
    let stopped = false
    scope.current += 1
    setDetail(null); setVersion(null); setPreview(null); setComparison(null); setCompareNumber(''); setTestRun(null); setTestMessages([]); setTestActive(false)
    if (!key || creating) return () => { stopped = true }
    Promise.all([getAdminAgent(accessToken, key), getAgentAudits(accessToken, key)])
      .then(([value, log]) => { if (!stopped) { setDetail(value); setNumber(value.profile.activeVersion || value.versions[0]?.version); setAudits(log); setMoreAudits(log.length === AGENT_AUDIT_PAGE_SIZE) } })
      .catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true; scope.current += 1 }
  }, [accessToken, key, creating])

  useEffect(() => {
    let stopped = false
    setVersion(null); setPreview(null); setComparison(null); setCompareNumber('')
    if (!key || !number || creating || detail?.profile.profileId !== key) return () => { stopped = true }
    getAgentVersion(accessToken, key, number).then((value) => {
      if (!stopped) { setVersion(value); setDefinition(value.definition); setModelNamesText(value.definition.model.models.join(', ')) }
    }).catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true }
  }, [accessToken, key, number, creating, detail?.profile.profileId])

  useEffect(() => {
    let stopped = false
    setComparison(null)
    if (compareNumber && key) getAgentVersion(accessToken, key, Number(compareNumber))
      .then((value) => { if (!stopped) setComparison(value.definition) })
      .catch((failure) => { if (!stopped) setError(failure.message) })
    return () => { stopped = true }
  }, [accessToken, key, compareNumber])

  useEffect(() => {
    if (!testRun?.conversationId) return undefined
    let stopped = false; let timer
    const refresh = async () => {
      try {
        const history = await getCreativeConversationHistory(accessToken, testRun.conversationId)
        if (stopped) return
        setTestMessages(history.messages); setTestActive(Boolean(history.activeRun))
        if (history.activeRun) timer = window.setTimeout(refresh, APPROVAL_POLL_MS)
      } catch (failure) { if (!stopped) { setError(failure.message); timer = window.setTimeout(refresh, APPROVAL_POLL_MS) } }
    }
    refresh()
    return () => { stopped = true; window.clearTimeout(timer) }
  }, [accessToken, testRun])

  /** 修改表单后清除旧测试预览，发布版本本身仍保持只读。 */
  const update = (field, value) => { setDefinition((current) => ({ ...current, [field]: value })); setPreview(null) }
  /** 刷新指针、目录和审计，保留当前查看的精确版本。 */
  const reload = async (nextNumber) => {
    const [value, list, log] = await Promise.all([getAdminAgent(accessToken, key), listAdminAgents(accessToken), getAgentAudits(accessToken, key)])
    setDetail(value); setItems(list); setAudits(log); setMoreAudits(log.length === AGENT_AUDIT_PAGE_SIZE)
    const next = nextNumber || number
    setNumber(next)
    const data = await getAgentVersion(accessToken, key, next)
    setVersion(data); setDefinition(data.definition); setModelNamesText(data.definition.model.models.join(', ')); setPreview(null)
  }
  /** 所有管理动作串行提交，冲突保留本地编辑并提示刷新。 */
  const perform = async (action, message) => {
    if (busy) return
    const expected = scope.current
    setBusy(true); setError(''); setNotice('')
    try { await action(); if (scope.current === expected) setNotice(message) }
    catch (failure) { if (scope.current === expected) setError(failure.message) }
    finally { if (scope.current === expected) setBusy(false) }
  }
  /** 保存只更新草稿，新建和已有身份使用不同的 revision 契约。 */
  const save = async (event) => {
    event.preventDefault()
    await perform(async () => {
      if (creating) {
        const value = await createAgentProfile(accessToken, { profileId: newKey, definition })
        setItems(await listAdminAgents(accessToken)); setCreating(false); setKey(value.profile.profileId); setBusy(false)
      } else {
        await updateAgentDraft(accessToken, key, number, { expectedRevision: detail.profile.revision, definition }); await reload()
      }
    }, '草稿已保存，发布后才会影响新的运行。')
  }
  /** 明确区分复制草稿、发布、回滚和启停，不将编辑内容夹带进发布请求。 */
  const mutate = (kind) => perform(async () => {
    const revision = detail.profile.revision
    if (kind === 'clone') { const value = await createAgentDraft(accessToken, key, { expectedRevision: revision, definition: version.definition }); await reload(value.profile.lastVersion) }
    if (kind === 'publish') { await publishAgentVersion(accessToken, key, number, { expectedRevision: revision, comment }); await reload() }
    if (kind === 'rollback') { await rollbackAgentVersion(accessToken, key, number, { expectedRevision: revision, comment }); await reload() }
    if (kind === 'enable') { await setAgentEnabled(accessToken, key, { expectedRevision: revision, enabled: !detail.profile.enabled, comment }); await reload() }
  }, '操作已记录；已有运行继续使用原快照。')
  /** 测试绑定页面已保存的指纹；编辑中的未保存内容必须先保存。 */
  const testPayload = () => ({ requestId: crypto.randomUUID(), projectId: testProject ? Number(testProject) : null,
    modelConfigId: testModel ? Number(testModel) : null, instruction: testInstruction, definitionFingerprint: version.fingerprint })
  const runTest = (start) => perform(async () => {
    if (compareAgentDefinitions(version.definition, definition).length) throw new Error('请先保存草稿，再预览或测试。')
    if (start) { const run = await testAgentVersion(accessToken, key, number, testPayload()); setTestRun(run); setTestActive(true); setTestMessages([]) }
    else setPreview(await previewAgentVersion(accessToken, key, number, testPayload()))
  }, start ? '已启动只读测试；可随时停止。' : '编译校验通过，此次预览没有调用模型。')
  const diffs = compareAgentDefinitions(comparison, definition)

  return <section className="workspace-module agent-admin">
    <header className="workspace-heading"><div><h1>Agent 配置管理</h1><p>维护专业定义与发布版本。已发布内容不可修改，业务写入继续经过用户审批。</p></div>
      <button className="workspace-primary" type="button" disabled={busy} onClick={() => { setCreating(true); setDefinition(newAgentDefinition()); setModelNamesText(''); setNewKey(''); setError(''); setNotice('') }}>新增专业 Agent</button></header>
    {error && <p className="agent-admin-error" role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    <div className="agent-admin-layout">
      <nav aria-label="Agent 配置列表">{items.map((item) => <button type="button" key={item.profileId} disabled={busy}
        className={!creating && item.profileId === key ? 'workspace-secondary selected' : 'workspace-secondary'} onClick={() => { setCreating(false); setKey(item.profileId); setError('') }}>
        <strong>{item.profileId}</strong><small>{item.enabled ? '启用' : '禁用'} · {item.activeVersion ? `发布 v${item.activeVersion}` : '尚未发布'}</small></button>)}</nav>
      <main>
        {!creating && detail && <div className="agent-admin-toolbar"><strong>{key}</strong><span>revision {detail.profile.revision}</span>
          <select aria-label="查看定义版本" value={number || ''} disabled={busy} onChange={(event) => setNumber(Number(event.target.value))}>
            {detail.versions.map((item) => <option key={item.version} value={item.version}>v{item.version} · {item.status === AGENT_VERSION_STATUS.DRAFT ? '草稿' : '已发布'}{detail.profile.activeVersion === item.version ? ' · 当前生效' : ''}</option>)}</select>
          <button className="workspace-secondary" type="button" disabled={busy || !version} onClick={() => mutate('clone')}>基于此版建草稿</button>
          <button className="workspace-secondary" type="button" disabled={busy} onClick={() => mutate('enable')}>{detail.profile.enabled ? '禁用新调用' : '启用新调用'}</button>
          <button className="workspace-secondary" type="button" disabled={busy} onClick={() => perform(() => reload(), '已刷新服务器版本。')}>刷新</button>
        </div>}
        {(creating || version) && <form onSubmit={save}>
          <fieldset disabled={busy || !canEdit}>
            <legend>{creating ? '创建专业定义' : `定义 v${number}${canEdit ? ' · 可编辑草稿' : ' · 发布内容只读'}`}</legend>
            {creating && <label>稳定标识<input required value={newKey} pattern="[a-z][a-z0-9_]{2,63}" placeholder="character_consistency_expert" onChange={(event) => setNewKey(event.target.value)} /></label>}
            <div className="agent-admin-fields">
              <label>名称<input required maxLength={120} value={definition.displayName} onChange={(event) => update('displayName', event.target.value)} /></label>
              <label>固定角色族<select value={definition.role} disabled={detail?.profile.builtIn && !creating} onChange={(event) => { const role = event.target.value; update('role', role); update('permissionPolicy', 'READ_ONLY'); update('capabilities', newAgentDefinition().capabilities); if (role === 'manga_director' && definition.model.selection === 'INHERIT_PARENT') update('model', { ...definition.model, selection: 'USER_SELECTABLE' }) }}>
                {Object.entries(AGENT_ROLE_LABEL).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              <label>业务权限<select value={definition.permissionPolicy} onChange={(event) => { const policy = event.target.value; update('permissionPolicy', policy); if (policy === 'READ_ONLY') update('capabilities', definition.capabilities.filter((name) => options.find((item) => item.name === name)?.requiredPermission === 'READ_ONLY')) }}>
                <option value="READ_ONLY">只读</option>{definition.role === 'manga_director' && <option value="PROPOSE_CHANGES">可提交审批提案</option>}</select></label>
              <label>推理轮数上限<input required type="number" min="1" max="32" value={definition.maxTurns} onChange={(event) => update('maxTurns', Number(event.target.value))} /></label>
            </div>
            <label>职责说明<textarea required rows="2" maxLength={2000} value={definition.description} onChange={(event) => update('description', event.target.value)} /></label>
            <label>系统提示词<textarea required rows="10" maxLength={24000} value={definition.systemPrompt} onChange={(event) => update('systemPrompt', event.target.value)} /></label>
            <section><h3>能力白名单</h3><div className="agent-capabilities">{options.filter((item) => definition.role === 'manga_director' || newAgentDefinition().capabilities.includes(item.name) || item.name === 'list_storyboard_shots')
              .map((item) => <label key={item.name}><input type="checkbox" checked={definition.capabilities.includes(item.name)} onChange={(event) => update('capabilities', event.target.checked ? [...definition.capabilities, item.name] : definition.capabilities.filter((name) => name !== item.name))} /><span>{item.name}<small>{item.description}</small></span></label>)}</div></section>
            <section><h3>模型策略</h3><label>选择方式<select value={definition.model.selection} onChange={(event) => update('model', { ...definition.model, selection: event.target.value })}>
              {Object.entries(AGENT_MODEL_SELECTION_LABEL).filter(([value]) => definition.role !== 'manga_director' || value !== 'INHERIT_PARENT').map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
              <div className="agent-capabilities">{providers.map((provider) => <label key={provider}><input type="checkbox" checked={definition.model.providers.includes(provider)} onChange={(event) => update('model', { ...definition.model,
                providers: event.target.checked ? [...definition.model.providers, provider] : definition.model.providers.filter((value) => value !== provider) })} />{provider}</label>)}</div>
              <small>不勾选表示允许平台已接入的全部对话协议。Worker 使用父模型，并继续检查白名单。</small>
              <label>允许的模型名称（逗号分隔，留空不限）<input value={modelNamesText} onChange={(event) => { setModelNamesText(event.target.value); update('model', { ...definition.model, models: event.target.value.split(',').map((value) => value.trim()).filter(Boolean) }) }} /></label>
            </section>
            <section><h3>预算上限</h3><div className="agent-admin-fields">{Object.entries(AGENT_BUDGET_LABEL).map(([field, label]) => <label key={field}>{label}<input required type="number" min={field === 'maxRepairRounds' ? 0 : 1} value={definition.budget[field]} onChange={(event) => update('budget', { ...definition.budget, [field]: Number(event.target.value) })} /></label>)}</div></section>
            {canEdit && <button className="workspace-primary" type="submit">保存草稿</button>}
          </fieldset>
        </form>}
        {!creating && version && <>
          <section><h3>发布与回滚</h3><label>操作说明<input maxLength={1000} value={comment} disabled={busy} onChange={(event) => setComment(event.target.value)} /></label>
            {version.status === AGENT_VERSION_STATUS.DRAFT
              ? <button className="workspace-secondary" type="button" disabled={busy || compareAgentDefinitions(version.definition, definition).length > 0} onClick={() => mutate('publish')}>校验并发布 v{number}</button>
              : <button className="workspace-secondary" type="button" disabled={busy || detail?.profile.activeVersion === number} onClick={() => mutate('rollback')}>回滚发布指针至 v{number}</button>}
          </section>
          <section><h3>版本比较</h3><label>与此历史版本比较<select value={compareNumber} onChange={(event) => setCompareNumber(event.target.value)}><option value="">选择基准版本</option>{detail?.versions.map((item) => <option key={item.version} value={item.version}>v{item.version}</option>)}</select></label>
            {comparison && !diffs.length && <p>定义内容一致。</p>}{diffs.map((diff) => <article key={diff.key}><h4>{diff.label}</h4><div className="agent-diff"><pre aria-label="原版本">{diff.before}</pre><pre aria-label="当前版本">{diff.after}</pre></div></article>)}
          </section>
          <section><h3>只读测试</h3><p>预览只编译，不调用模型。“启动模型测试”会使用你的模型产生调用费用，推理轮数受版本和平台测试预算限制；不开放提案、委派或媒体生成。</p>
            <div className="agent-admin-fields"><label>测试项目<select value={testProject} disabled={busy} onChange={(event) => { setTestProject(event.target.value); setPreview(null) }}><option value="">不绑定项目</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></label>
              <label>你的文本模型配置<select value={testModel} disabled={busy} onChange={(event) => { setTestModel(event.target.value); setPreview(null) }}><option value="">使用默认文本模型</option>{models.filter((model) => model.enabled && model.modelType === 'TEXT').map((model) => <option key={model.id} value={model.id}>{model.name} · {model.defaultModel}</option>)}</select></label></div>
            <label>测试问题<textarea rows="3" maxLength={12000} value={testInstruction} disabled={busy} onChange={(event) => setTestInstruction(event.target.value)} /></label>
            <button className="workspace-secondary" type="button" disabled={busy || !testInstruction.trim() || !detail?.profile.enabled} onClick={() => runTest(false)}>校验并预览</button>
            <button className="workspace-secondary" type="button" disabled={busy || testActive || !preview || !detail?.profile.enabled} onClick={() => runTest(true)}>启动模型测试（产生费用）</button>
            {preview && <p>Kernel 指纹：<code>{preview.fingerprint}</code><br />模型：{preview.model} · 轮数上限：{preview.maxTurns}<br />只读工具：{preview.capabilities.join('、') || '无'}</p>}
            {testRun && <div><p>Run：{testRun.runId}</p>{testActive && <button className="workspace-secondary" type="button" disabled={busy} onClick={() => perform(() => cancelCreativeRun(accessToken, testRun.runId), '已请求停止测试。')}>停止测试</button>}
              {testMessages.filter((message) => message.role === 'ASSISTANT').map((message) => <article key={message.projectionKey}><p>{message.content || '正在执行…'}</p><small>{message.status}</small></article>)}
              <CreativeRunGraph accessToken={accessToken} runId={testRun.runId} /></div>}
          </section>
          <section><h3>操作审计</h3><table><thead><tr><th>操作</th><th>版本</th><th>发布指针</th><th>操作者</th><th>时间与说明</th></tr></thead><tbody>{audits.map((audit) => <tr key={audit.id}><td>{AGENT_ACTION_LABEL[audit.action] || audit.action}</td><td>{audit.version || '—'}</td><td>{audit.previousActiveVersion || '—'} → {audit.activeVersion || '—'}</td><td>{audit.actor}</td><td>{audit.at}<br />{audit.comment}</td></tr>)}</tbody></table>
            {moreAudits && <button className="workspace-secondary" type="button" disabled={busy} onClick={() => perform(async () => { const page = await getAgentAudits(accessToken, key, audits.at(-1)?.id); setAudits((current) => [...current, ...page]); setMoreAudits(page.length === AGENT_AUDIT_PAGE_SIZE) }, '已加载审计记录。')}>更早记录</button>}
          </section>
        </>}
      </main>
    </div>
  </section>
}
