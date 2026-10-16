package com.luminous.api.search;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** 搜索内容按普通文本解释，布尔表达式仅由服务端从字母数字构造。 */
public record SearchTerms(String text, String key, String booleanQuery, List<String> literals, String forcedKind, Long userId) {
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+(?:[+#]+)?");
    public static SearchTerms parse(String input) {
        if (input == null || input.length() > 200) throw invalid("请输入1至50个字符的关键词");
        String text = Normalizer.normalize(input, Normalizer.Form.NFKC).replaceAll("[\\p{Z}\\s]+", " ").trim();
        if (text.codePointCount(0, text.length()) < 1 || text.codePointCount(0, text.length()) > 50) throw invalid("请输入1至50个字符的关键词");
        String forced = null;
        Long userId = null;
        if (text.startsWith("@")) { forced = "users"; text = text.substring(1).trim(); }
        else if (text.startsWith("c/")) { forced = "communities"; text = text.substring(2).trim(); }
        else if (text.startsWith("id:")) {
            forced = "users";
            try { userId = Long.valueOf(text.substring(3)); if (userId <= 0) throw invalid("用户ID必须为正整数"); }
            catch (NumberFormatException ex) { throw invalid("用户ID必须为正整数"); }
            text = text.substring(3);
        }
        if (text.isBlank()) throw invalid("请补充名称或关键词");
        text = text.toLowerCase(Locale.ROOT);
        var matcher = WORDS.matcher(text);
        Set<String> full = new LinkedHashSet<>(), literals = new LinkedHashSet<>();
        while (matcher.find()) {
            String word = matcher.group(), base = word.replaceAll("[+#]+$", "");
            if (base.codePointCount(0, base.length()) >= 2) full.add(base);
            else literals.add(word);
        }
        if (full.isEmpty() && literals.isEmpty()) throw invalid("关键词至少包含一个文字或数字");
        if (full.size() + literals.size() > 6) throw invalid("请使用不超过6个关键词");
        String expression = full.stream().map(word -> "+\"" + word + "\"").collect(java.util.stream.Collectors.joining(" "));
        String key = (forced == null ? "all" : forced) + ":" + (userId == null ? "text:" : "id:") + text;
        return new SearchTerms(text, key, expression, List.copyOf(literals), forced, userId);
    }
    public String prefix() { return text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
    public String hint() { return booleanQuery.isBlank() && userId == null ? "短词仅匹配名称和标题开头，可补充关键词扩大范围。" : ""; }
    public static BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SEARCH", message); }
}
