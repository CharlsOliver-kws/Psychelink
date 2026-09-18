package com.psychic.agent.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 解析工具
 */
public class MarkdownParser {

    private static final Pattern CODE_BLOCK = Pattern.compile("```(\\w+)?\\n([\\s\\S]*?)```");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$", Pattern.MULTILINE);
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");

    /**
     * 解析代码块
     */
    public static String parseCodeBlocks(String markdown) {
        Matcher matcher = CODE_BLOCK.matcher(markdown);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
            sb.append("<pre><code>");
            sb.append(matcher.group(2));
            sb.append("</code></pre>");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 解析标题
     */
    public static String parseHeadings(String markdown) {
        return markdown.replaceAll("^#{1,6}\\s+(.*)$", "<h>$1</h>");
    }

    /**
     * 解析粗体
     */
    public static String parseBold(String markdown) {
        return markdown.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
    }

    /**
     * 转换为 HTML
     */
    public static String toHtml(String markdown) {
        String result = markdown;
        result = parseCodeBlocks(result);
        result = parseHeadings(result);
        result = parseBold(result);
        return result;
    }
}