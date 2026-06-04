/**
 * 统一反馈层
 *
 * 替代浏览器原生阻断弹窗以及散落各处的 `Message.xxx` 直接调用，
 * 统一走 Arco Design 的 Message / Modal，配合项目深色玻璃质感风格的全局样式覆盖
 * （见 src/style.css 中的 `.arco-message` / `.arco-modal` 段）。
 *
 * 设计原则：
 *   1. Toast 用于"无需用户决策"的瞬时反馈（成功、失败、校验提醒）。
 *   2. confirmAction 用于"需要用户决策"的危险操作，返回 Promise<boolean>，调用方必须 await。
 *   3. alertXxx 用于"需要用户阅读后点确定"的告知型弹窗（结果汇总、长文本提示）。
 *
 * 不要在 `request.ts` 之外直接 `import { Message } from '@arco-design/web-vue'`，
 * 通过本文件统一出口便于以后改样式/换实现。
 */
import { Message, Modal } from '@arco-design/web-vue'

// ── Toast (Message) ──────────────────────────────────────────────────────────

export function toastSuccess(content: string): void {
  Message.success({ content, duration: 2200 })
}

export function toastError(content: string): void {
  Message.error({ content, duration: 3000 })
}

export function toastWarning(content: string): void {
  Message.warning({ content, duration: 2600 })
}

export function toastInfo(content: string): void {
  Message.info({ content, duration: 2200 })
}

// ── 用户决策弹窗 ─────────────────────────────────────────────────────────────

export interface ConfirmOptions {
  /** 弹窗标题，默认"请确认" */
  title?: string
  /** 主体内容，支持字符串（多行用 \n） */
  content: string
  /** 确认按钮文案，默认"确定" */
  okText?: string
  /** 取消按钮文案，默认"取消" */
  cancelText?: string
  /** 危险操作（删除、批量删除、回滚等），按钮变红 */
  danger?: boolean
}

/**
 * 返回 Promise<boolean>。
 * 调用方必须 await，避免同步假象。
 *
 *   if (!await confirmAction({ content: '确定删除？', danger: true })) return
 */
export function confirmAction(options: ConfirmOptions): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    const openDecisionModal = Modal.confirm
    openDecisionModal({
      title: options.title ?? '请确认',
      content: options.content,
      okText: options.okText ?? '确定',
      cancelText: options.cancelText ?? '取消',
      hideCancel: false,
      maskClosable: false,
      okButtonProps: options.danger ? { status: 'danger' } : undefined,
      onOk: () => { resolve(true) },
      onCancel: () => { resolve(false) }
    })
  })
}

// ── 告知型弹窗 ───────────────────────────────────────────────────────────────

/**
 * 信息型弹窗，比 toast 更"重"，适合内容较长 / 需要用户主动关闭的提示，
 * 例如关联校验结果、变更摘要等多行文本。
 */
export function alertInfo(content: string, title = '提示'): void {
  Modal.info({
    title,
    content,
    okText: '我知道了',
    maskClosable: true
  })
}

export function alertWarning(content: string, title = '注意'): void {
  Modal.warning({
    title,
    content,
    okText: '我知道了',
    maskClosable: true
  })
}

export function alertError(content: string, title = '错误'): void {
  Modal.error({
    title,
    content,
    okText: '我知道了',
    maskClosable: true
  })
}

export function alertSuccess(content: string, title = '操作成功'): void {
  Modal.success({
    title,
    content,
    okText: '好的',
    maskClosable: true
  })
}
