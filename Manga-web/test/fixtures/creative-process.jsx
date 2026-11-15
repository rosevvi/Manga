import React from 'react'
import { createRoot } from 'react-dom/client'
import CreativeRunDetails from '../../src/components/CreativeRunDetails'
import '../../src/styles.css'
import '../../src/components/creative-workbench.css'

/** 只由隔离浏览器测试加载，所有 API 指向测试进程的假服务。 */
function Preview() {
  const runId = new URLSearchParams(location.search).get('run') || 'stream-run'
  return <div className="task-center" style={{ position: 'relative', width: '100%', height: '100vh', inset: 'auto', borderRadius: 0 }}>
    <div className="task-center-scroll"><CreativeRunDetails accessToken="fixture-token" runId={runId} /></div>
  </div>
}

createRoot(document.getElementById('root')).render(<Preview />)
