package com.jacolp.converter;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown → HTML 纯解析引擎。
 * <p>
 * 职责：接收原始 Markdown 文本，输出结构化的 HTML 处理结果（{@link HtmlProcessResult}）。
 * 不涉及任何文件系统 I/O 操作，与外部资源完全解耦。
 * </p>
 */
public class MarkdownHtmlEngine {

    // ==================== 正则常量 ====================

    /**
     * 匹配 YAML Front-Matter 块。
     * <p>
     * 格式：文件以 "---" 起始，紧跟换行，内容区域以 "---" 结束。
     * <ul>
     *     <li>{@code (?s)} — 启用 DOTALL 模式使 '.' 能匹配换行符</li>
     *     <li>{@code \\R} — 匹配任意平台换行符 (\n, \r\n, \r)</li>
     *     <li>{@code (.*?)} — 非贪婪捕获 Front-Matter 正文内容</li>
     * </ul>
     */
    private static final Pattern FRONT_MATTER =
            Pattern.compile("(?s)^---\\R(.*?)\\R---\\R?");

    /**
     * 匹配 Obsidian 风格的 WikiLink：[[目标]] 或 [[目标|别名]]。
     * <p>
     * 捕获组 1 为双方括号内的全部内容（包含可能的 '|' 和别名部分）。
     * 例如：{@code [[Java 并发|JUC]]} 的捕获组 1 为 {@code "Java 并发|JUC"}。
     */
    private static final Pattern WIKILINK =
            Pattern.compile("\\[\\[([^\\]]+)]]");

    /**
     * 匹配不带 id 属性的 HTML 标题标签 (h1-h6)。
     * <p>
     * 用于在后处理阶段为标题添加锚点 id。
     * <ul>
     *     <li>捕获组 1 = 标题级别（数字 1-6）</li>
     *     <li>捕获组 2 = 标题内容（可含内联 HTML，如 {@code <code>}、{@code <a>} 等）</li>
     * </ul>
     * {@code Pattern.DOTALL} 使 '.' 能跨行匹配标题内容。
     */
    private static final Pattern HEADING =
            Pattern.compile("<h([1-6])>(.*?)</h\\1>", Pattern.DOTALL);

    /**
     * 匹配已携带 id 属性的 HTML 标题标签 (h1-h6)，用于生成 TOC 目录。
     * <p>
     * 此正则与 {@link #HEADING} 互补：HEADING 用于"添加 id"，本正则用于"读取 id 后生成 TOC"。
     * <ul>
     *     <li>捕获组 1 = 标题级别（数字 1-6）</li>
     *     <li>捕获组 2 = id 属性值（slug 化后的锚点标识符）</li>
     *     <li>捕获组 3 = 标题文本内容（可含内联 HTML）</li>
     * </ul>
     */
    private static final Pattern HEADING_WITH_ID =
            Pattern.compile("<h([1-6]) id=\"([^\"]+)\">(.*?)</h\\1>", Pattern.DOTALL);

    /**
     * 匹配 Obsidian Callout 语法经 Flexmark 渲染后的 HTML 结构。
     * <p>
     * 原始 Markdown 格式为 {@code > [!type] title}，换行后继续以 {@code >} 开头书写正文，
     * 经 Flexmark 渲染后变为 {@code <blockquote><p>[!type] ...</p>...</blockquote>}。
     * <ul>
     *     <li>捕获组 1 = callout 类型关键字（如 info、tip、warning、question、failure 等）</li>
     *     <li>捕获组 2 = 标题文本（{@code [!type]} 之后到 {@code </p>} 之间的内容）</li>
     *     <li>捕获组 3 = 正文 HTML 内容（首段 {@code </p>} 之后到 {@code </blockquote>} 之间）</li>
     * </ul>
     * {@code (?s)} 启用 DOTALL 使 '.' 能匹配正文中的换行。
     */
    private static final Pattern CALLOUT =
            Pattern.compile("(?s)<blockquote>\\s*<p>\\[!(\\w+)]\\s*(.*?)</p>(.*?)</blockquote>");

    /**
     * 匹配 Flexmark 渲染出的 Mermaid 代码块。
     * <p>
     * 原始 Markdown 为 {@code ```mermaid ... ```}，Flexmark 渲染后的 HTML 结构为
     * {@code <pre><code class="language-mermaid">...</code></pre>}。
     * 本正则捕获其中的图表源码，后续将其转换为可被 Mermaid.js 前端库识别的
     * {@code <div class="mermaid">} 结构。
     * <ul>
     *     <li>捕获组 1 = Mermaid 图表源码（需经 HTML 反转义后才可使用）</li>
     * </ul>
     */
    private static final Pattern MERMAID_BLOCK =
            Pattern.compile("(?s)<pre><code class=\"language-mermaid\">(.*?)</code></pre>");

    // ==================== Flexmark 解析器与渲染器 ====================

    private final Parser parser;
    private final HtmlRenderer renderer;

    // ==================== 数据结构（Record 类） ====================

    /**
     * YAML Front-Matter 元数据。
     * <p>
     * 该记录类封装了从 Markdown 文件头部 YAML 块中提取的结构化信息，
     * 供外部调用方（如 HTML 页面模板拼装层）读取和使用。
     *
     * @param title      文章标题（可为 null，表示 Front-Matter 中未声明 title 字段）
     * @param tags       标签列表（不为 null；若无标签则为空列表 {@code List.of()}）
     * @param createTime 创建时间字符串（可为 null，格式由作者自定义，如 "2025-01-01"）
     */
    public record FrontMatter(String title, List<String> tags, String createTime) {

        /**
         * 返回空的 FrontMatter 实例（所有字段为默认空值）。
         * <p>
         * 当 Markdown 文本不含 Front-Matter 块时，使用此工厂方法作为安全的默认返回值，
         * 避免外部调用方进行 null 检查。
         */
        public static FrontMatter empty() {
            return new FrontMatter(null, List.of(), null);
        }

        /**
         * 若当前 title 为空白，则使用 fallback 值创建新实例；否则返回自身。
         * <p>
         * 典型使用场景：当 Front-Matter 未声明 title 时，由外部 I/O 层传入文件名
         * （去掉 .md 扩展名后）作为兜底标题。Record 为不可变类型，故需创建新实例。
         *
         * @param fallback 备选标题（通常为文件名去扩展名，如 "Java并发编程"）
         * @return 带有标题的 FrontMatter（若原 title 已有效则返回 this）
         */
        public FrontMatter withFallbackTitle(String fallback) {
            if (title != null && !title.isBlank()) {
                return this;
            }
            return new FrontMatter(fallback, tags, createTime);
        }
    }

    /**
     * Markdown 解析的最终输出结果。
     * <p>
     * 聚合了引擎处理流水线的全部产物，供外部 I/O 层（如 {@code App.java}）
     * 直接读取并拼装为完整的 HTML 页面。各字段职责分离，互不耦合。
     *
     * @param meta     解析得到的 Front-Matter 元数据（标题、标签、创建时间等）
     * @param tocHtml  生成的 TOC 侧边栏 HTML 片段（可为空字符串 {@code ""} 表示无目录）
     * @param bodyHtml Flexmark 渲染并经后处理增强（锚点 id、Callout、Mermaid）的 HTML 正文
     */
    public record HtmlProcessResult(FrontMatter meta, String tocHtml, String bodyHtml) {
    }

    // ==================== 构造函数 ====================

    /**
     * 构造 MarkdownHtmlEngine 实例。
     * <p>
     * 初始化 Flexmark 解析器（{@link Parser}）和渲染器（{@link HtmlRenderer}），
     * 并启用以下 GitHub Flavored Markdown (GFM) 扩展：
     * <ul>
     *     <li>{@link TablesExtension} — GFM 表格语法（{@code | col | col |}）</li>
     *     <li>{@link StrikethroughExtension} — 删除线语法（{@code ~~text~~}）</li>
     *     <li>{@link TaskListExtension} — 任务列表语法（{@code - [x]} / {@code - [ ]}）</li>
     * </ul>
     * <p>
     * 创建后的实例是无状态的（Parser 和 HtmlRenderer 均为线程安全的不可变对象），
     * 可在多个文件之间安全复用，无需为每个 Markdown 文件创建新引擎。
     */
    public MarkdownHtmlEngine() {
        // 使用 MutableDataSet 配置 Flexmark 的解析选项
        MutableDataSet options = new MutableDataSet();

        // 注册 GFM 扩展列表：表格、删除线、任务列表
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create()
        ));

        // 基于相同配置构建解析器和渲染器，确保两者的扩展设置一致
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    // ==================== 公开主流程 ====================

    /**
     * 将原始 Markdown 文本解析并转换为结构化的 HTML 结果。
     * <p>
     * 处理流水线（按顺序执行）：
     * <ol>
     *     <li>提取并移除 YAML Front-Matter</li>
     *     <li>替换 Obsidian WikiLink 为标准 Markdown 链接</li>
     *     <li>使用 Flexmark 解析 Markdown 并渲染为 HTML</li>
     *     <li>后处理：为标题添加锚点 id、转换 Callout、转换 Mermaid</li>
     *     <li>根据标题层级生成 TOC 目录 HTML</li>
     * </ol>
     * <p>
     * 本方法为纯函数式调用，不涉及任何文件 I/O。
     * 若 Front-Matter 中未声明 title，{@link FrontMatter#title()} 将为 null，
     * 由外部调用方（如 I/O 层）通过 {@link FrontMatter#withFallbackTitle(String)} 自行兜底。
     *
     * @param rawMarkdown 原始 Markdown 文本
     * @return 包含元数据、TOC 和正文 HTML 的处理结果
     */
    public HtmlProcessResult process(String rawMarkdown) {
        // ① 提取 Front-Matter 元数据（title、tags、create_time）
        FrontMatter meta = parseFrontMatter(rawMarkdown);

        // ② 移除 Front-Matter 块，使后续解析器不会将其视为正文内容
        String cleaned = FRONT_MATTER.matcher(rawMarkdown).replaceFirst("");

        // ③ 将 Obsidian WikiLink 语法替换为标准 Markdown 链接
        String preprocessed = replaceWikiLinks(cleaned);

        // ④ 使用 Flexmark 解析 Markdown AST 并渲染为原始 HTML
        Node document = parser.parse(preprocessed);
        String htmlBody = renderer.render(document);

        // ⑤ 后处理增强：添加标题锚点 id → 转换 Callout → 转换 Mermaid
        htmlBody = postProcessHtml(htmlBody);

        // ⑥ 基于已添加 id 的标题，生成 TOC 侧边栏 HTML
        String tocHtml = buildTocHtml(htmlBody);

        return new HtmlProcessResult(meta, tocHtml, htmlBody);
    }

    // ==================== 预处理方法 ====================

    /**
     * 从 Markdown 文本头部提取 YAML Front-Matter 元数据。
     * <p>
     * 支持解析的字段：
     * <ul>
     *     <li>{@code title} — 文章标题</li>
     *     <li>{@code create_time} — 创建时间</li>
     *     <li>{@code tags} — 标签列表（支持 YAML 多行列表语法 {@code "- tag"} 或行内值 {@code "tags: java"}）</li>
     * </ul>
     * 若文本不包含 Front-Matter 块，返回 {@link FrontMatter#empty()}。
     * <p>
     * 解析策略：逐行扫描 Front-Matter 块内容，通过 {@code currentKey} 变量
     * 追踪当前正在解析的 YAML 键名，以支持 {@code tags} 的多行列表格式（连续的 {@code "- xxx"} 行）。
     *
     * @param markdown 原始 Markdown 文本
     * @return 解析后的 FrontMatter 实例
     */
    private FrontMatter parseFrontMatter(String markdown) {
        Matcher matcher = FRONT_MATTER.matcher(markdown);
        if (!matcher.find()) {
            return FrontMatter.empty();
        }

        String block = matcher.group(1);
        String title = null;
        String createTime = null;
        List<String> tags = new ArrayList<>();
        // currentKey 用于追踪当前正在解析的 YAML 键名，以支持 tags 的多行列表格式
        String currentKey = "";

        for (String rawLine : block.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }

            // 处理 YAML 列表项：当前键为 "tags" 时，以 "- " 开头的行视为标签值
            if (line.startsWith("- ") && "tags".equals(currentKey)) {
                tags.add(line.substring(2).trim());
                continue;
            }

            // 解析 "key: value" 格式的行
            int colonIndex = line.indexOf(':');
            if (colonIndex < 0) {
                continue;
            }

            currentKey = line.substring(0, colonIndex).trim();
            String value = line.substring(colonIndex + 1).trim();

            if ("title".equals(currentKey)) {
                title = value;
            } else if ("create_time".equals(currentKey)) {
                createTime = value;
            } else if ("tags".equals(currentKey) && !value.isEmpty()) {
                // 支持行内标签值，如 "tags: java"
                tags.add(value);
            }
        }

        return new FrontMatter(title, tags, createTime);
    }

    /**
     * 将 Obsidian 风格的 WikiLink 替换为标准 Markdown 链接语法。
     * <p>
     * 支持的 WikiLink 格式：
     * <ul>
     *     <li>{@code [[页面名]]} → {@code [页面名](页面名.html)}</li>
     *     <li>{@code [[页面名|显示文本]]} → {@code [显示文本](页面名.html)}</li>
     *     <li>{@code [[#章节]]} → {@code [章节](#slugified-章节)}</li>
     *     <li>{@code [[页面名#章节]]} → {@code [页面名 > 章节](页面名.html#slugified-章节)}</li>
     * </ul>
     * <p>
     * 使用 {@link Matcher#appendReplacement} 和 {@link Matcher#appendTail} 进行逐匹配替换，
     * 确保非 WikiLink 部分的文本原样保留。
     *
     * @param markdown 包含 WikiLink 的 Markdown 文本
     * @return WikiLink 替换为标准链接后的文本
     */
    private String replaceWikiLinks(String markdown) {
        Matcher matcher = WIKILINK.matcher(markdown);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            String replacement = buildMarkdownLink(raw);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * 将单个 WikiLink 的内部内容构建为标准 Markdown 链接。
     * <p>
     * 解析逻辑：
     * <ol>
     *     <li>检测 {@code |} 分隔符，拆分为 target（链接目标）和 alias（显示文本）</li>
     *     <li>若 target 以 {@code #} 开头，视为当前页面内锚点跳转</li>
     *     <li>否则检测 target 中的 {@code #} 分隔页面名与章节名</li>
     *     <li>页面名追加 {@code .html} 后缀，章节名经 {@link #slugify} 处理后作为锚点</li>
     * </ol>
     *
     * @param raw WikiLink 双方括号内的原始内容（已 trim）
     * @return 标准 Markdown 链接字符串，如 {@code [text](url)}
     */
    private String buildMarkdownLink(String raw) {
        String target = raw;
        String alias = null;

        // 拆分别名：[[目标|别名]] 中 '|' 之后为显示文本
        int aliasIndex = raw.indexOf('|');
        if (aliasIndex >= 0) {
            target = raw.substring(0, aliasIndex).trim();
            alias = raw.substring(aliasIndex + 1).trim();
        }

        // 情况 1：纯锚点链接，如 [[#章节标题]]
        if (target.startsWith("#")) {
            String section = target.substring(1).trim();
            String text = (alias == null || alias.isEmpty()) ? section : alias;
            return "[" + text + "](#" + slugify(section) + ")";
        }

        // 情况 2：页面链接，可能带章节锚点，如 [[页面]] 或 [[页面#章节]]
        String page = target;
        String section = null;
        int hashIndex = target.indexOf('#');
        if (hashIndex >= 0) {
            page = target.substring(0, hashIndex).trim();
            section = target.substring(hashIndex + 1).trim();
        }

        // 确定显示文本：优先使用别名，其次使用 "页面 > 章节" 或单独页面名
        String text = alias;
        if (text == null || text.isEmpty()) {
            text = section == null ? page : page + " > " + section;
        }

        // 拼接 URL：页面名.html[#slugified-章节]
        StringBuilder url = new StringBuilder(page).append(".html");
        if (section != null && !section.isEmpty()) {
            url.append('#').append(slugify(section));
        }
        return "[" + text + "](" + url + ")";
    }

    // ==================== 后处理方法 ====================

    /**
     * 对 Flexmark 渲染后的 HTML 进行二次增强处理。
     * <p>
     * 处理流水线（顺序执行）：
     * <ol>
     *     <li>{@link #addHeadingIds} — 为所有标题标签添加锚点 id 属性</li>
     *     <li>{@link #transformCallouts} — 将 blockquote 中的 Obsidian Callout 转为语义化 div</li>
     *     <li>{@link #transformMermaid} — 将 Mermaid 代码块转换为可渲染的 div</li>
     * </ol>
     * <p>
     * 每一步的输出作为下一步的输入，形成管线处理模式。
     *
     * @param htmlBody Flexmark 渲染输出的原始 HTML
     * @return 增强后的 HTML 正文
     */
    private String postProcessHtml(String htmlBody) {
        String withHeadingIds = addHeadingIds(htmlBody);
        String withCallouts = transformCallouts(withHeadingIds);
        return transformMermaid(withCallouts);
    }

    /**
     * 为 HTML 中所有 h1-h6 标题标签添加唯一的 id 属性。
     * <p>
     * id 值由标题纯文本（去除内联 HTML 标签后）经 {@link #slugify} 处理后生成。
     * 例如：{@code <h2>Java 并发</h2>} → {@code <h2 id="java-并发">Java 并发</h2>}
     * <p>
     * 仅处理尚未携带 id 属性的标题标签（匹配 {@link #HEADING} 正则），
     * 已有 id 的标题不会被重复处理。
     *
     * @param htmlBody 待处理的 HTML 正文
     * @return 所有标题均已携带 id 属性的 HTML
     */
    private String addHeadingIds(String htmlBody) {
        Matcher matcher = HEADING.matcher(htmlBody);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String level = matcher.group(1);
            String content = matcher.group(2);
            // 去除标题内容中的内联 HTML 标签（如 <code>、<a> 等），仅保留纯文本用于生成 slug
            String plain = content.replaceAll("<[^>]+>", "").trim();
            String id = slugify(plain);
            String replacement = "<h" + level + " id=\"" + id + "\">" + content + "</h" + level + ">";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * 将 Obsidian Callout 语法（经 Flexmark 渲染后的 blockquote 结构）转换为语义化的 div 容器。
     * <p>
     * 转换前（Flexmark 渲染的 blockquote）：
     * <pre>{@code <blockquote><p>[!warning] 注意事项</p><p>详细内容...</p></blockquote>}</pre>
     * 转换后（语义化 div）：
     * <pre>{@code <div class="callout callout-warning"><div class="callout-title">注意事项</div>
     *   <div class="callout-content"><p>详细内容...</p></div></div>}</pre>
     * <p>
     * 支持的 callout 类型包括但不限于：info、tip、warning、question、failure。
     * 若标题为空，自动以类型名首字母大写作为默认标题（如 {@code "Warning"}）。
     *
     * @param htmlBody 包含 blockquote 式 Callout 的 HTML
     * @return Callout 已转为语义化 div 的 HTML
     */
    private String transformCallouts(String htmlBody) {
        Matcher matcher = CALLOUT.matcher(htmlBody);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String type = matcher.group(1).toLowerCase(Locale.ROOT);
            String title = matcher.group(2).trim();
            String body = matcher.group(3).trim();
            // 若作者未指定标题，使用 callout 类型首字母大写作为默认标题
            if (title.isEmpty()) {
                title = Character.toUpperCase(type.charAt(0)) + type.substring(1);
            }

            String replacement = """
                    <div class=\"callout callout-%s\">
                      <div class=\"callout-title\">%s</div>
                      <div class=\"callout-content\">%s</div>
                    </div>
                    """.formatted(type, title, body);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * 将 Flexmark 渲染出的 Mermaid 代码块转换为可被 Mermaid.js 前端库识别的 div 元素。
     * <p>
     * 转换前：{@code <pre><code class="language-mermaid">graph TD; A-->B;</code></pre>}
     * 转换后：{@code <div class="mermaid">graph TD; A-->B;</div>}
     * <p>
     * 注意：代码块内容会先经过 {@link #decodeHtml} 反转义，
     * 因为 Flexmark 会对代码块中的 {@code <, >, &} 等字符进行 HTML 实体编码，
     * 而 Mermaid.js 需要原始字符才能正确解析图表语法。
     *
     * @param htmlBody 包含 Mermaid pre/code 块的 HTML
     * @return Mermaid 块已转为 div 的 HTML
     */
    private String transformMermaid(String htmlBody) {
        Matcher matcher = MERMAID_BLOCK.matcher(htmlBody);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String code = decodeHtml(matcher.group(1).trim());
            String replacement = "<div class=\"mermaid\">\n" + code + "\n</div>";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * 扫描已添加 id 的标题标签（h2-h4），生成带有折叠/展开功能的 TOC 侧边栏 HTML。
     * <p>
     * 仅收录 2-4 级标题（h1 通常为页面主标题，h5/h6 层级过深不适合放入目录）。
     * 每个目录项按层级添加对应的 CSS 类（{@code toc-level-2}, {@code toc-level-3},
     * {@code toc-level-4}）以实现缩进效果。若无可用标题，返回空字符串。
     * <p>
     * 生成的 TOC 包含：
     * <ul>
     *     <li>固定侧边栏容器 {@code <aside id="tocSidebar">}，支持收起/展开</li>
     *     <li>浮动操作按钮 {@code <button id="tocFab">}，收起后可通过点击展开</li>
     * </ul>
     *
     * @param htmlBody 标题已带 id 属性的 HTML 正文（需先经过 {@link #addHeadingIds} 处理）
     * @return TOC 侧边栏 HTML 片段，或空字符串
     */
    private String buildTocHtml(String htmlBody) {
        Matcher matcher = HEADING_WITH_ID.matcher(htmlBody);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            // 仅收录 h2 ~ h4 级别的标题作为目录项
            if (level < 2 || level > 4) {
                continue;
            }
            String id = matcher.group(2);
            // 去除标题中的内联 HTML 标签，只保留纯文本
            String text = matcher.group(3).replaceAll("<[^>]+>", "").trim();
            if (text.isEmpty()) {
                continue;
            }
            sb.append("<a class=\"toc-link toc-level-")
                    .append(level)
                    .append("\" href=\"#")
                    .append(escapeHtml(id))
                    .append("\">")
                    .append(escapeHtml(text))
                    .append("</a>");
        }

        if (sb.isEmpty()) {
            return "";
        }

        return """
                <aside id="tocSidebar" class="toc-sidebar">
                    <div class="toc-header">
                        <strong>目录</strong>
                        <button id="tocCollapseBtn" class="toc-collapse-btn" type="button" aria-label="收起目录">×</button>
                    </div>
                    <nav class="toc-nav">
                        %s
                    </nav>
                </aside>
                <button id="tocFab" class="toc-fab" type="button" aria-label="展开目录" title="目录">☰</button>
                """.formatted(sb);
    }

    // ==================== 工具方法 ====================

    /**
     * 将文本转换为 URL 友好的 slug（锚点标识符）。
     * <p>
     * 处理规则：
     * <ol>
     *     <li>使用 NFKC 规范化并转为小写</li>
     *     <li>保留字母、数字和 CJK 统一汉字</li>
     *     <li>其他字符替换为连字符 {@code -}，连续的非法字符仅产生一个连字符</li>
     *     <li>去除首尾的连字符</li>
     *     <li>若结果为空白，返回 {@code "section"} 作为兜底值</li>
     * </ol>
     * <p>
     * 示例：{@code "Java 并发编程"} → {@code "java-并发编程"}，
     * {@code "  ##special!! chars  "} → {@code "special-chars"}。
     *
     * @param input 原始文本（通常为标题纯文本）
     * @return URL 安全的 slug 字符串
     */
    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        boolean prevDash = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c) || isCjk(c)) {
                sb.append(c);
                prevDash = false;
            } else if (!prevDash) {
                sb.append('-');
                prevDash = true;
            }
        }
        // 去掉首尾可能残留的连字符
        String slug = sb.toString().replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "section" : slug;
    }

    /**
     * 判断字符是否属于 CJK 统一汉字区域。
     * <p>
     * 覆盖的 Unicode 区块：
     * <ul>
     *     <li>CJK Unified Ideographs (U+4E00 – U+9FFF)</li>
     *     <li>CJK Unified Ideographs Extension A (U+3400 – U+4DBF)</li>
     *     <li>CJK Unified Ideographs Extension B (U+20000 – U+2A6DF)</li>
     * </ul>
     * 用于 {@link #slugify} 在生成锚点时保留汉字字符而非替换为连字符。
     *
     * @param c 待判断的字符
     * @return 若属于 CJK 汉字区域返回 true
     */
    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    /**
     * 对文本进行 HTML 实体编码（XSS 安全转义）。
     * <p>
     * 转义规则：{@code & → &amp;}, {@code < → &lt;}, {@code > → &gt;},
     * {@code " → &quot;}, {@code ' → &#39;}。
     * <p>
     * 注意：{@code &} 必须最先替换，避免对后续替换产生的 {@code &} 做二次转义。
     * 例如若先将 {@code <} 替换为 {@code &lt;}，再替换 {@code &} 就会错误地
     * 将 {@code &lt;} 变为 {@code &amp;lt;}。
     *
     * @param value 原始文本
     * @return HTML 安全的转义文本
     */
    static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 将 HTML 实体编码还原为原始字符（与 {@link #escapeHtml} 互为逆操作）。
     * <p>
     * 主要用于 Mermaid 代码块：Flexmark 渲染时会将代码内容进行实体编码，
     * 而 Mermaid.js 需要原始字符才能正确解析图表语法（如箭头 {@code -->}
     * 被编码为 {@code --&gt;} 后无法识别）。
     *
     * @param value 包含 HTML 实体的文本
     * @return 已还原的原始文本
     */
    private static String decodeHtml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
