/**
 * Mermaid 渲染封装
 *
 * 后端把 Markdown 代码块 ```mermaid ... ``` 转换为 <div class="mermaid">...</div>。
 * 本模块负责：
 *   1. 单例初始化 mermaid（startOnLoad=false / theme=default / securityLevel=strict）
 *   2. 在给定容器内、只渲染尚未处理的 .mermaid 节点（不污染 document 全局）
 *
 * 用法：
 *   import { renderMermaidIn } from '@/utils/mermaid'
 *   await renderMermaidIn(articleContentEl)
 */
import mermaid from 'mermaid'

// 由于 v-html 重新设置内容时旧 DOM 节点会被销毁，
// 残留的 data-processed 不会回到新节点上 —— 这里只用它防止同一次渲染中重复处理已被
// mermaid 替换的节点（mermaid 自身会在节点上写 data-processed="true"）。
const PROCESSED_ATTR = 'data-processed'

let initialized = false

function ensureInitialized(): void {
  if (initialized) return
  mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    securityLevel: 'strict',
  })
  initialized = true
}

/**
 * 在指定容器内渲染所有未处理的 .mermaid 节点。
 * 容器不存在或没有 .mermaid 节点时安静返回。
 */
export async function renderMermaidIn(container: HTMLElement | null | undefined): Promise<void> {
  if (!container) return
  const nodes = Array.from(
    container.querySelectorAll<HTMLElement>(`.mermaid:not([${PROCESSED_ATTR}="true"])`)
  )
  if (nodes.length === 0) return

  ensureInitialized()

  try {
    await mermaid.run({ nodes })
  } catch (err) {
    console.error('[mermaid] render failed:', err)
  }
}
