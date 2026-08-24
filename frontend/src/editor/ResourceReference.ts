import { Node, mergeAttributes } from '@tiptap/core'

export interface ResourceReferenceAttributes {
  refId: string
  resourceType: string | null
  resourceId: string | null
  displayText: string
  alias: string | null
}

/**
 * 经由 Tiptap 协作绑定写入 Y.XmlFragment 的行内原子节点。
 * 第一版只记录引用属性；后续关系投影器可直接消费这些属性，无需改写编辑器正文。
 */
export const ResourceReference = Node.create({
  name: 'resourceReference',

  inline: true,
  group: 'inline',
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      refId: { default: null },
      resourceType: { default: null },
      resourceId: { default: null },
      displayText: { default: '' },
      alias: { default: null }
    }
  },

  parseHTML() {
    return [{ tag: 'span[data-document-resource-ref]' }]
  },

  renderHTML({ HTMLAttributes }) {
    const attributes = HTMLAttributes as ResourceReferenceAttributes
    return ['span', mergeAttributes(
      {
        'data-document-resource-ref': '',
        'data-ref-id': attributes.refId,
        'data-resource-type': attributes.resourceType,
        'data-resource-id': attributes.resourceId,
        'data-alias': attributes.alias,
        class: 'document-resource-reference'
      },
      HTMLAttributes
    ), attributes.alias || attributes.displayText || '未命名引用']
  }
})
