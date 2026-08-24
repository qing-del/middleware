package com.jacolp.document.application.yjs;

/** 合并服务无法返回有效的合并后 Yjs 状态时抛出。 */
public class YjsMergeException extends RuntimeException {

    public YjsMergeException(String message) {
        super(message);
    }

    public YjsMergeException(String message, Throwable cause) {
        super(message, cause);
    }
}
