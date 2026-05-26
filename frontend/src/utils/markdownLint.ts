import { linter, lintGutter, type Diagnostic } from '@codemirror/lint'
import type { EditorView } from '@codemirror/view'
import type { Extension } from '@codemirror/state'

interface LineCtx {
  text: string
  from: number
  to: number
  number: number
}

function scanLines(doc: ReturnType<EditorView['state']['doc']['line']>['constructor'] extends never ? never : EditorView['state']['doc'], cb: (line: LineCtx) => void) {
  // Walk every line via the doc API
  for (let i = 1; i <= (doc as any).lines; i++) {
    const line = (doc as any).line(i)
    cb({ text: line.text, from: line.from, to: line.to, number: line.number })
  }
}

function buildDiagnostics(view: EditorView): Diagnostic[] {
  const diagnostics: Diagnostic[] = []
  const doc = view.state.doc

  let insideFence = false
  let fenceStartPos: number | null = null
  let lastHeadingLevel = 0

  scanLines(doc as any, (line) => {
    const text = line.text

    // Rule 3: track fenced code blocks (``` toggling)
    const fenceMatch = text.match(/^\s*```/)
    if (fenceMatch) {
      if (!insideFence) {
        insideFence = true
        fenceStartPos = line.from
      } else {
        insideFence = false
        fenceStartPos = null
      }
      return
    }

    if (insideFence) return

    // Rule 1: heading-level jump
    const headingMatch = text.match(/^(#{1,6})\s+\S/)
    if (headingMatch) {
      const level = headingMatch[1].length
      if (lastHeadingLevel > 0 && level - lastHeadingLevel > 1) {
        diagnostics.push({
          from: line.from,
          to: line.from + headingMatch[1].length,
          severity: 'warning',
          message: `标题层级跳跃：从 H${lastHeadingLevel} 直接跳到 H${level}，建议按层级递进。`,
        })
      }
      lastHeadingLevel = level
      return
    }

    // Rule 2: unordered list marker must be followed by a space
    const badListMatch = text.match(/^(\s*)([-*+])(\S)/)
    if (badListMatch) {
      const markerOffset = badListMatch[1].length
      diagnostics.push({
        from: line.from + markerOffset,
        to: line.from + markerOffset + 1,
        severity: 'warning',
        message: `列表标记 "${badListMatch[2]}" 后应有一个空格。`,
      })
    }

    // Rule 5: image syntax — flag empty alt or empty url
    let imgMatch: RegExpExecArray | null
    const imgRe = /!\[([^\]]*)\]\(([^)]*)\)/g
    while ((imgMatch = imgRe.exec(text)) !== null) {
      const alt = imgMatch[1]
      const url = imgMatch[2]
      const start = line.from + imgMatch.index
      const end = start + imgMatch[0].length
      if (!url.trim()) {
        diagnostics.push({
          from: start,
          to: end,
          severity: 'error',
          message: '图片链接 URL 为空：`![alt](url)` 中 url 不能省略。',
        })
      } else if (!alt.trim()) {
        diagnostics.push({
          from: start,
          to: end,
          severity: 'info',
          message: '图片缺少 alt 文本，建议补全以便辅助阅读。',
        })
      }
    }
  })

  // Rule 3 (final): if doc ended while still inside a fence, report it
  if (insideFence && fenceStartPos !== null) {
    diagnostics.push({
      from: fenceStartPos,
      to: Math.min(fenceStartPos + 3, doc.length),
      severity: 'error',
      message: '代码块未闭合：以 ``` 开始但缺少对应的结束 ```。',
    })
  }

  // Rule 4: wiki-link [[ ]] pairing (skip content inside fenced code blocks)
  const fullText = doc.toString()
  insideFence = false
  let i = 0
  const openStack: number[] = []
  while (i < fullText.length) {
    // Detect fenced toggles only at line start
    if ((i === 0 || fullText[i - 1] === '\n')) {
      const slice = fullText.slice(i, i + 16)
      const m = slice.match(/^\s*```/)
      if (m) {
        insideFence = !insideFence
        i += m[0].length
        continue
      }
    }
    if (!insideFence) {
      if (fullText[i] === '[' && fullText[i + 1] === '[') {
        openStack.push(i)
        i += 2
        continue
      }
      if (fullText[i] === ']' && fullText[i + 1] === ']') {
        if (openStack.length > 0) openStack.pop()
        i += 2
        continue
      }
    }
    i++
  }
  for (const openPos of openStack) {
    diagnostics.push({
      from: openPos,
      to: openPos + 2,
      severity: 'error',
      message: '双链未闭合：`[[` 缺少对应的 `]]`。',
    })
  }

  return diagnostics
}

export const markdownLinter: Extension = linter((view) => buildDiagnostics(view), {
  delay: 600,
})

export const markdownLintGutter: Extension = lintGutter()
