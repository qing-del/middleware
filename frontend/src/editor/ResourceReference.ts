import { Node, mergeAttributes } from '@tiptap/core'

export interface ResourceReferenceAttributes {
  /** 引用节点自身的稳定 ID；example: {@code 'resource-ref-42'} */
  refId: string
  /** 被引用资源的类型；example: {@code 'note'} */
  resourceType: string | null
  /** 被引用资源的业务 ID；example: {@code '42'} */
  resourceId: string | null
  /** 编辑器中展示的引用文本；example: {@code '项目设计文档'} */
  displayText: string
  /** 用户自定义的显示别名；example: {@code '设计文档'} */
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

  /** 声明引用节点可持久化的最小属性集合。 */
  addAttributes() {
    return {
      refId: { default: null },
      resourceType: { default: null },
      resourceId: { default: null },
      displayText: { default: '' },
      alias: { default: null }
    }
  },

  /** 从带有资源引用 data 属性的 span 恢复 Tiptap 节点。 */
  parseHTML() {
    return [{ tag: 'span[data-document-resource-ref]' }]
  },

  /** 将引用属性渲染为稳定的 data 属性和可读文本。 */
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
