package com.jacolp.middleware.module.media.biz.application.dto;

/** Temporary local pagination contract until shared transport primitives leave middleware-pojo. */
public interface PageParamProvider {
    int DEFAULT_PAGE = 1;
    int DEFAULT_PAGE_SIZE = 15;

    Integer getPageNum();
    Integer getPageSize();

    default int getPageNumOrDefault() {
        return getPageNum() == null ? DEFAULT_PAGE : getPageNum();
    }

    default int getPageSizeOrDefault() {
        return getPageSize() == null ? DEFAULT_PAGE_SIZE : getPageSize();
    }
}
