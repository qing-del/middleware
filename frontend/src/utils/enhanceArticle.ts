/**
 * 文章内容增强 —— 统一入口
 *
 * 后端把 Markdown 转换成 HTML 后通过 v-html 渲染到 .article-content 容器。
 * v-html 写入 DOM 后还需要后续增强：
 *   1. enhanceTables       — 给 <table> 包裹 .table-wrapper 实现横向滚动
 *   2. highlightCodeBlocks — 对 pre > code 做语法高亮（跳过 Mermaid）
 *   3. renderMermaid       — 渲染 Mermaid 图表
 *
 * 用法：
 *   import { enhanceArticleContent } from '@/utils/enhanceArticle'
 *   await enhanceArticleContent(articleContentRef.value)
 *
 * 所有增强操作只作用于传入的容器元素，不做全局 document 查询。
 */
import { wrapTablesIn } from './table'
import { highlightCodeBlocksIn } from './highlight'
import { renderMermaidIn } from './mermaid'

/**
 * 对文章容器执行全部增强操作。
 * 按顺序：表格包装 → 代码高亮 → Mermaid 渲染。
 * 容器不存在时安静返回。
 */
export async function enhanceArticleContent(
  container: HTMLElement | null | undefined
): Promise<void> {
  if (!container) return

  // 1. 表格：给 <table> 包裹 .table-wrapper（防重复）
  wrapTablesIn(container)

  // 2. 代码块：对 pre > code 做语法高亮（跳过 Mermaid 和已高亮）
  highlightCodeBlocksIn(container)

  // 3. Mermaid：渲染 .mermaid 图表节点（跳过已渲染）
  await renderMermaidIn(container)
}
