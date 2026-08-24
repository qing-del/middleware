import { Node, mergeAttributes } from '@tiptap/core'

export interface ResourceReferenceAttributes {
  refId: string
  resourceType: string | null
  resourceId: string | null
  displayText: string
  alias: string | null
}

/**
 * Inline atom persisted in the Y.XmlFragment through Tiptap's collaboration
 * binding. The first version records the reference only; a later relation
 * projector may consume the same attributes without rewriting editor content.
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
