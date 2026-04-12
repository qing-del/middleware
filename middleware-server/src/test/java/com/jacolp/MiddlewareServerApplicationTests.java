package com.jacolp;

import com.jacolp.converter.MarkdownHtmlEngine;
import com.jacolp.converter.MarkdownHtmlEngine.*;
import com.jacolp.converter.MarkdownPublishService;
import com.jacolp.io.LocalMarkdownScanner;
import com.jacolp.utils.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

@SpringBootTest
@TestPropertySource(properties = "")
class MiddlewareServerApplicationTests {
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${jacolp.default-password}")
    private String defaultPassword;
//    @Test
    public void testPasswordEncode() {
        System.out.println(defaultPassword);
        System.out.println(passwordEncoder.encode(defaultPassword));
    }


    @Autowired private LocalMarkdownScanner localMarkdownScanner;
    @Autowired private MarkdownProperty markdownProperty;
    @Autowired private MarkdownPublishService publishService;

//    @Test
    public void testFlexmark() {
        Path inputDir = Path.of(markdownProperty.getInputDir());
        // 递归遍历目录，筛选 .md 和 .markdown 后缀的文件
        try (Stream<Path> files = Files.walk(inputDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".md") || name.endsWith(".markdown");
                    })
                    .forEach(markdownPath -> {
                        try {
                            // 读取源文件内容
                            String rawMarkdown = Files.readString(markdownPath, StandardCharsets.UTF_8);

                            String[] tags = MarkdownHtmlEngine.extractTagsFromMarkdown(rawMarkdown);
                            System.out.println(Arrays.toString(tags));

                            publishService.publish(markdownPath, rawMarkdown, inputDir);

                            System.out.println("[flexmark] OK: " + markdownPath);
                        } catch (Exception e) {
                            // 单个文件失败不中断整体流程
                            System.err.println("[flexmark] FAIL: " + markdownPath);
                            e.printStackTrace(System.err);
                        }
                    });
        } catch (IOException exception) {
            throw new UncheckedIOException("遍历输入目录失败: " + inputDir, exception);
        }
    }

    @Autowired private MarkdownHtmlEngine markdownHtmlEngine;

    @Test
    public void testHtmlProcessResult() {
        String inputDir = markdownProperty.getInputDir();
        String fileName = "JUC基础与线程核心概念.md";
        Path markdownFilePath = Path.of(inputDir, fileName);

        try {
            String rawMarkdown = Files.readString(markdownFilePath);
            HtmlProcessResult result = markdownHtmlEngine.process(rawMarkdown);

            System.out.println(result);

        } catch (Exception e) {
            // 单个文件失败不中断整体流程
            System.err.println("[flexmark] FAIL: " + markdownFilePath);
            e.printStackTrace(System.err);
        }

    }
}
