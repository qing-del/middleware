export function markdownContentToFile(content: string, title: string): File {
  const filename = title.endsWith('.md') ? title : `${title}.md`
  return new File([content], filename, { type: 'text/markdown' })
}

export function formatCharCount(n: number): string {
  if (n < 1000) return `${n} 字符`
  return `${(n / 1000).toFixed(1)}k 字符`
}

export function estimateByteSize(content: string): number {
  return new TextEncoder().encode(content).length
}

export function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, index)).toFixed(index > 0 ? 1 : 0) + ' ' + units[index]
}

/** Backend limit: 300KB */
export const MAX_NOTE_FILE_SIZE = 300 * 1024
