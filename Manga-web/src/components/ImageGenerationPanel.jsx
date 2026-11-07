import { useEffect, useRef, useState } from 'react'
import { createManualRun } from '../api/creativeRunApi'
import { listImageGenerationOptions } from '../api/generationApi'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import { IMAGE_SIZES } from '../constants/generation'
import ManualCreativeTaskPanel from './ManualCreativeTaskPanel'
import ProjectCreativeTaskHistory from './ProjectCreativeTaskHistory'
import GenerationJobsPanel from './GenerationJobsPanel'
import './generation.css'

/** 手动推进图片生成，不要求用户先打开聊天；仍先生成提案、预览并审批。 */
export default function ImageGenerationPanel({ accessToken, projectId, shots }) {
  const [options, setOptions] = useState([])
  const [optionKey, setOptionKey] = useState('')
  const [shotId, setShotId] = useState('')
  const [size, setSize] = useState(IMAGE_SIZES[0])
  const [prompt, setPrompt] = useState('')
  const [runId, setRunId] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const scope = useRef(0)
  useEffect(() => {
    const expected = ++scope.current
    setRunId(null); setShotId(''); setPrompt(''); setOptions([]); setOptionKey(''); setError(''); setBusy(false)
    listImageGenerationOptions(accessToken).then((items) => {
      if (scope.current !== expected) return
      setOptions(items); setOptionKey(items.length ? '0' : '')
    }).catch((failure) => { if (scope.current === expected) setError(failure.message) })
    return () => { scope.current += 1 }
  }, [accessToken, projectId])
  const selected = options[Number(optionKey)]
  /** 参数作为用户意图交给 Director；付费执行仍由服务端冻结后的审批内容决定。 */
  const submit = async (event) => {
    event.preventDefault()
    if (busy || !selected || !shotId || !prompt.trim() || selected.estimatedCost == null) return
    const expected = scope.current
    setBusy(true); setError('')
    try {
      const [width, height] = size.split('x').map(Number)
      const parameters = { shotId: Number(shotId), provider: selected.provider, configId: selected.configId,
        model: selected.model, prompt: prompt.trim(), width, height }
      const run = await createManualRun(accessToken, {
        requestId: globalThis.crypto.randomUUID(), projectId, operationType: CREATIVE_OPERATION.GENERATE_SHOT_IMAGE,
        permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        target: { resourceType: CREATIVE_RESOURCE.STORYBOARD_SHOT, resourceId: Number(shotId) },
        instruction: `请读取目标镜头，按以下明确选择的参数调用 propose_image_generation 提交单张图片提案，等待我预览费用并批准，不要直接执行生成。参数：${JSON.stringify(parameters)}`,
      })
      if (scope.current === expected) { setRunId(run.runId); setReloadKey((value) => value + 1) }
    } catch (failure) { if (scope.current === expected) setError(failure.message) }
    finally { if (scope.current === expected) setBusy(false) }
  }
  return <section className="image-generation-panel">
    <h3>AI 生成镜头图片</h3>
    <form className="image-generation-form" onSubmit={submit}>
      <label>目标镜头<select required value={shotId} onChange={(event) => {
        setShotId(event.target.value)
        const shot = shots.find((item) => String(item.id) === event.target.value)
        setPrompt(shot?.content || shot?.title || '')
      }}><option value="">选择镜头</option>{shots.map((shot) => <option key={shot.id} value={shot.id}>{shot.shotNumber} · {shot.title}</option>)}</select></label>
      <label>供应商配置<select value={optionKey} onChange={(event) => setOptionKey(event.target.value)}>{options.map((option, index) =>
        <option key={`${option.provider}.${option.configId}`} value={String(index)}>{option.name} · {option.model}</option>)}</select></label>
      <label>输出尺寸<select value={size} onChange={(event) => setSize(event.target.value)}>{IMAGE_SIZES.map((value) => <option key={value}>{value}</option>)}</select></label>
      <label>画面提示词<textarea required rows={3} maxLength={4000} value={prompt} onChange={(event) => setPrompt(event.target.value)} /></label>
      <small>单张 PNG，中等质量。{selected?.estimatedCost != null ? `预计 ${selected.estimatedCost} ${selected.currency}，实际账单以供应商为准。` : '真实生成尚未配置预计费用，请由管理员核价。'}生成前还需预览并批准。</small>
      <button type="submit" disabled={busy || !shotId || !prompt.trim() || selected?.estimatedCost == null}>{busy ? '创建提案中…' : '创建图片生成提案'}</button>
    </form>
    {error && <p role="alert">{error}</p>}
    <ProjectCreativeTaskHistory accessToken={accessToken} projectId={projectId} selectedRunId={runId} reloadKey={reloadKey}
      onSelect={setRunId} onActiveChanged={() => {}} />
    {runId && <ManualCreativeTaskPanel key={`${projectId}.${runId}`} accessToken={accessToken} projectId={projectId} runId={runId} onRunChanged={setRunId} />}
    {!runId && <GenerationJobsPanel accessToken={accessToken} projectId={projectId} />}
  </section>
}
