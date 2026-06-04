/**
 * Table 渲染增强
 *
 * 后端把 Markdown 表格转换成标准 <table> 标签，但前端需要：
 *   1. 给每个 <table> 包裹一层 <div class="table-wrapper"> 实现横向滚动
 *   2. 避免重复包装（父元素已是 .table-wrapper 则跳过）
 *
 * 用法：
 *   import { wrapTablesIn } from '@/utils/table'
 *   wrapTablesIn(articleContentEl)
 */

/**
 * 在指定容器内，给所有 <table> 包裹 .table-wrapper 容器。
 * 容器不存在或没有 <table> 时安静返回。
 * 如果 table 的父元素已经是 .table-wrapper，则跳过（防重复包装）。
 */
export function wrapTablesIn(container: HTMLElement | null | undefined): void {
  if (!container) return
  const tables = Array.from(
    container.querySelectorAll<HTMLTableElement>('table')
  )
  for (const table of tables) {
    if (table.parentElement?.classList.contains('table-wrapper')) continue
    const wrapper = document.createElement('div')
    wrapper.className = 'table-wrapper'
    table.parentNode?.insertBefore(wrapper, table)
    wrapper.appendChild(table)
  }
}
