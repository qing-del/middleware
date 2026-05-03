package com.jacolp.utils;

import com.jacolp.constant.NoteConstant;
import com.jacolp.exception.BaseException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * Markdown 文件操作工具类。
 * <p>从各 Service 层提取的公共文件操作，涵盖：</p>
 * <ul>
 *   <li>Spring {@link MultipartFile} → UTF-8 字符串转换</li>
 *   <li>上传文件名规范化（去除浏览器路径前缀）</li>
 *   <li>{@code .md} 扩展名剥离（用于标题推导）</li>
 * </ul>
 * <p>所有方法均为静态方法，无需实例化。</p>
 *
 * @see NoteConstant
 */
public class MarkdownFileUtil {

    /**
     * 将 Spring MVC 上传的 MultipartFile 读取为 UTF-8 字符串。
     * <p>一次性将文件字节全部读入内存并解码。调用方需确保文件大小
     * 已在 Controller 层通过 {@code @NoteFileLimit} 校验（上限 300KB）。</p>
     *
     * @param file 上传的 Markdown 文件（不可为 null）
     * @return 文件的 UTF-8 文本内容
     * @throws BaseException 当 IO 读取失败时（错误码: NOTE_FILE_READ_ERROR）
     */
    public static String readMultipartAsString(MultipartFile file) {
        try {
            // getBytes() 一次性读入全部字节，适合小文件（≤300KB）
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BaseException(NoteConstant.NOTE_FILE_READ_ERROR);
        }
    }

    /**
     * 规范化上传文件名，去除浏览器携带的客户端路径前缀。
     * <p>例如 {@code "C:\Users\xxx\note.md"} → {@code "note.md"}。
     * 通过 {@link Paths#get(String)} 提取路径末端的纯文件名，
     * 自动兼容 Windows ({@code \}) 和 Linux ({@code /}) 分隔符。</p>
     *
     * @param originalFilename {@link MultipartFile#getOriginalFilename()} 的原始值
     * @return 纯文件名（不含路径前缀）
     * @throws BaseException 当文件名为空或 null 时（错误码: NOTE_INVALID_FORMAT）
     */
    public static String normalizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BaseException(NoteConstant.NOTE_INVALID_FORMAT);
        }
        // Paths.get().getFileName() 自动提取路径末端的纯文件名部分
        return Paths.get(originalFilename).getFileName().toString();
    }

    /**
     * 去掉 {@code .md} 扩展名，用于从文件名推导笔记标题。
     * <p>仅当文件名以 {@code .md}（大小写不敏感）结尾时才剥离；
     * 否则原样返回，不抛出异常。例如：</p>
     * <pre>
     *   "my-note.md" → "my-note"
     *   "my-note.MD" → "my-note"
     *   "my-note"    → "my-note"
     * </pre>
     *
     * @param filename 文件名（可能含 {@code .md} 扩展名）
     * @return 去掉 {@code .md} 后缀的名称；非 .md 结尾时返回原值
     */
    public static String stripMarkdownExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return filename;
        }
        // 统一转小写后比较后缀，兼容 .md / .MD / .Md 等写法
        String lower = filename.toLowerCase();
        if (lower.endsWith(NoteConstant.ALLOWED_NOTE_FORMAT)) {
            return filename.substring(0, filename.length() - NoteConstant.ALLOWED_NOTE_FORMAT.length());
        }
        return filename;
    }
}
