/**
 * 代码语法高亮封装
 *
 * 后端把 Markdown 代码块 ```lang ... ``` 转换为 <pre><code class="language-lang">...</code></pre>。
 * 本模块负责：
 *   1. 单例初始化 highlight.js，注册常用语言
 *   2. 在给定容器内，对 pre > code 调用 hljs.highlightElement 做语法高亮
 *   3. 跳过 Mermaid 代码块（class 含 language-mermaid），避免干扰 Mermaid 渲染
 *   4. 跳过已高亮的代码块（已有 hljs class），避免重复处理
 *
 * 用法：
 *   import { highlightCodeBlocksIn } from '@/utils/highlight'
 *   highlightCodeBlocksIn(articleContentEl)
 */
import hljs from 'highlight.js/lib/core'
import 'highlight.js/styles/github-dark.css'

// ── 注册常用语言 ──────────────────────────────────────
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'       // HTML / XML 共用
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import properties from 'highlight.js/lib/languages/properties'
import dockerfile from 'highlight.js/lib/languages/dockerfile'

hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('properties', properties)
hljs.registerLanguage('dockerfile', dockerfile)

// ── Mermaid 跳过判断 ──────────────────────────────────
const MERMAID_RE = /(?:^|\s)(?:language-|lang-)?mermaid(?:\s|$)/

function isMermaidBlock(el: HTMLElement): boolean {
  return MERMAID_RE.test(el.className)
}

// ── 已高亮判断 ────────────────────────────────────────
function isAlreadyHighlighted(el: HTMLElement): boolean {
  // highlight.js 高亮完后会给元素加 hljs class
  return el.classList.contains('hljs') || el.dataset.highlighted === 'yes'
}

/**
 * 在指定容器内，对 pre > code 代码块做语法高亮。
 * 容器不存在或没有代码块时安静返回。
 * 跳过 Mermaid 代码块和已高亮的代码块。
 */
export function highlightCodeBlocksIn(container: HTMLElement | null | undefined): void {
  if (!container) return
  const blocks = Array.from(
    container.querySelectorAll<HTMLElement>('pre code')
  )
  for (const block of blocks) {
    if (isMermaidBlock(block)) continue
    if (isAlreadyHighlighted(block)) continue
    try {
      hljs.highlightElement(block)
    } catch (err) {
      console.error('[highlight] highlightElement failed:', err)
    }
  }
}
