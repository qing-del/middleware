package com.jacolp.utils;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.jacolp.result.PageResult;

import java.util.List;
import java.util.function.Supplier;

/**
 * 分页工具类。
 * <p>统一封装 PageHelper 分页样板代码。全项目原本存在 14 处
 * 重复的分页逻辑（分布在 7 个 Service 中），现由此工具类统一处理。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 *   PageResult result = PageUtil.startPageAndReturn(dto.getPageNum(), dto.getPageSize(),
 *       () -> mapper.listByCondition(dto));
 * </pre>
 *
 * <p>默认值：pageNum 默认 1，pageSize 默认 10。</p>
 */
public class PageUtil {

    /** 默认页码（当传入参数为 null 或 ≤0 时回退） */
    private static final int DEFAULT_PAGE = 1;

    /** 默认每页条数（当传入参数为 null 或 ≤0 时回退） */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 启动 PageHelper 分页并执行查询，返回统一分页结果。
     * <p>PageHelper 通过 ThreadLocal 传递分页参数给紧随其后的
     * 第一条 SQL 查询。此方法确保 startPage 与查询在同一调用链中。</p>
     *
     * @param <T>      查询结果列表的元素类型
     * @param pageNum  页码（从 1 开始）；传 null 或 ≤0 时使用默认值 1
     * @param pageSize 每页条数；传 null 或 ≤0 时使用默认值 10
     * @param query    执行查询的 Supplier，通常为 Mapper 的列表查询方法引用
     * @return 包含 total（总记录数）和 list（当前页数据）的分页结果
     */
    public static <T> PageResult startPageAndReturn(Integer pageNum, Integer pageSize,
                                                     Supplier<List<T>> query) {
        // 参数防御：null 或非法值回退到默认值
        int pn = (pageNum == null || pageNum <= 0) ? DEFAULT_PAGE : pageNum;
        int ps = (pageSize == null || pageSize <= 0) ? DEFAULT_PAGE_SIZE : pageSize;

        // PageHelper.startPage 将分页参数写入 ThreadLocal
        PageHelper.startPage(pn, ps);

        // 紧跟 startPage 的第一条 SQL 自动被 PageHelper 拦截分页
        List<T> records = query.get();

        // PageInfo 封装分页元数据（total, pages, pageNum 等）
        PageInfo<T> pageInfo = new PageInfo<>(records);

        // 转换为项目统一的 PageResult 返回
        return new PageResult(pageInfo.getTotal(), pageInfo.getList());
    }
}
