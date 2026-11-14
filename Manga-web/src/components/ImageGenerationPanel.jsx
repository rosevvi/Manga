import { useEffect, useRef, useState } from 'react'
import { createManualRun } from '../api/creativeRunApi'
import { listImageGenerationOptions, listVideoGenerationOptions } from '../api/generationApi'
import { CREATIVE_OPERATION, CREATIVE_PERMISSION, CREATIVE_RESOURCE } from '../constants/creative'
import { IMAGE_SIZES } from '../constants/generation'
import WorkspaceSelect from './WorkspaceSelect'
import { openCreativeTask } from './creativeTaskEvents'
import './generation.css'

/** 镜头生成表单按媒体类型只列出对应用户配置，所有提交先审批。 */
export default function ImageGenerationPanel({ accessToken, projectId, shots = [], selectedShot, mediaType = 'IMAGE' }) {
  const video = mediaType === 'VIDEO'
  const [seconds, setSeconds] = useState(5)
  const [aspectRatio, setAspectRatio] = useState('16:9')
  const [options, setOptions] = useState([])
  const [optionKey, setOptionKey] = useState('')
  const [shotId, setShotId] = useState('')
  const [size, setSize] = useState(IMAGE_SIZES[0])
  const [prompt, setPrompt] = useState('')
  const [runId, setRunId] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const scope = useRef(0)
  useEffect(() => {
    const expected = ++scope.current
    setOptions([]); setOptionKey(''); setError('')
    const listOptions = video ? listVideoGenerationOptions : listImageGenerationOptions
    listOptions(accessToken).then((items) => {
      if (scope.current !== expected) return
      const preferred = items.findIndex((item) => item.configId && item.estimatedCost != null)
      setOptions(items); setOptionKey(items.length ? String(Math.max(0, preferred)) : '')
    }).catch((failure) => { if (scope.current === expected) setError(failure.message) })
    return () => { scope.current += 1 }
  }, [accessToken, projectId, video])
  useEffect(() => { setShotId(selectedShot ? String(selectedShot.id) : ''); setPrompt(selectedShot?.content || selectedShot?.title || '') }, [selectedShot?.id])
  const selected = options[Number(optionKey)]
  /** 视频直接使用已选参数创建提案；图片保留 Director 提案流程。 */
  const submit = async (event) => {
    event.preventDefault()
    if (busy || !selected || !shotId || !prompt.trim() || selected.estimatedCost == null) return
    const expected = scope.current
    setBusy(true); setError('')
    try {
      const [width, height] = size.split('x').map(Number)
      const imageParameters = { shotId: Number(shotId), provider: selected.provider, configId: selected.configId,
        model: selected.model, prompt: prompt.trim(), width, height }
      const videoParameters = { shotId: Number(shotId), configId: selected.configId, prompt: prompt.trim(), seconds, aspectRatio }
      const run = await createManualRun(accessToken, {
        requestId: globalThis.crypto.randomUUID(), projectId,
        operationType: video ? CREATIVE_OPERATION.GENERATE_SHOT_VIDEO : CREATIVE_OPERATION.GENERATE_SHOT_IMAGE,
        permissionPolicy: CREATIVE_PERMISSION.PROPOSE_CHANGES,
        target: { resourceType: CREATIVE_RESOURCE.STORYBOARD_SHOT, resourceId: Number(shotId) },
        instruction: video ? JSON.stringify(videoParameters)
          : `请读取目标镜头，按以下明确选择的参数调用 propose_image_generation 提交单张图片提案，等待我预览费用并批准，不要直接执行生成。参数：${JSON.stringify(imageParameters)}`,
      })
      if (scope.current === expected) { setRunId(run.runId); openCreativeTask(run.runId, projectId) }
    } catch (failure) { if (scope.current === expected) setError(failure.message) }
    finally { if (scope.current === expected) setBusy(false) }
  }
  return <section className="image-generation-panel">
    <h3>{video ? '视频生成' : '图片生成'}</h3>
    <form className="image-generation-form" onSubmit={submit}>
      {!selectedShot && <label>目标镜头<WorkspaceSelect label="目标镜头" value={shotId} onChange={(id) => {
        setShotId(id)
        const shot = shots.find((item) => String(item.id) === String(id))
        setPrompt(shot?.content || shot?.title || '')
      }} options={shots.map((shot) => ({ value: String(shot.id), label: `${shot.shotNumber} · ${shot.title}` }))} /></label>}
      <label>使用的{video ? '视频' : '图片'}模型<WorkspaceSelect label="媒体模型配置" value={optionKey} onChange={setOptionKey}
        options={options.map((option, index) => ({ value: String(index), label: `${option.name} · ${option.model}` }))} /></label>
      {video ? <>
        <label>画幅<WorkspaceSelect label="视频画幅" value={aspectRatio} onChange={setAspectRatio}
          options={['16:9', '9:16', '1:1', '4:3', '3:4', '21:9'].map((ratio) => ({ value: ratio, label: ratio }))} /></label>
        <label>时长（秒）<input type="number" required min={4} max={12} value={seconds}
          onChange={(event) => setSeconds(Number(event.target.value))} /></label>
      </> : <label>输出尺寸<WorkspaceSelect label="图片尺寸" value={size} onChange={setSize} options={IMAGE_SIZES.map((value) => ({ value, label: value }))} /></label>}
      <label>画面提示词<textarea required rows={3} maxLength={4000} value={prompt} onChange={(event) => setPrompt(event.target.value)} /></label>
      <small>{video ? '单个 720P MP4。' : '单张 PNG，中等质量。'}{selected?.estimatedCost != null
        ? `预计 ${video ? (selected.estimatedCost * seconds).toFixed(3) : selected.estimatedCost} ${selected.currency}，批准后才会提交。`
        : '请在 AI 配置页启用对应类型的模型。'}</small>
      <button className="workspace-primary" type="submit" disabled={busy || !shotId || !prompt.trim() || selected?.estimatedCost == null}>
        {busy ? '创建提案中…' : video ? '创建视频生成提案' : '创建图片生成提案'}
      </button>
    </form>
    {error && <p role="alert">{error}</p>}
    {runId && <button type="button" className="workspace-secondary" onClick={() => openCreativeTask(runId, projectId)}>查看生成任务</button>}
  </section>
}
