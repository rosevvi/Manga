import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

const plugins = [remarkGfm]
const components = {
  a: ({ node, ...props }) => <a {...props} target="_blank" rel="noreferrer" />,
  img: ({ node, ...props }) => <img {...props} loading="lazy" referrerPolicy="no-referrer" />,
  table: ({ node, ...props }) => <div className="assistant-markdown-table"><table {...props} /></div>,
}

/** 懒加载 GFM 解析，模型输出以普通 Markdown 节点渲染。 */
export default function AssistantMarkdownRenderer({ text }) {
  return <ReactMarkdown remarkPlugins={plugins} components={components} skipHtml>{text}</ReactMarkdown>
}
