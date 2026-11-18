package com.ape.badminton.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 全局 JSON 日期处理
 *
 * <p>问题背景：小程序前端与页面表单传上来的日期是「2026-10-01 09:00:00」这类
 * 自然格式，而 Jackson 默认只接受 ISO-8601（2026-10-01T09:00:00），
 * 会导致 400/500 反序列化失败。这里统一做两件事：</p>
 *
 * <ul>
 *   <li><b>入参宽松解析</b>：依次尝试常见格式，最后兼容毫秒时间戳；</li>
 *   <li><b>出参统一格式</b>：所有 Date 一律序列化为 {@code yyyy-MM-dd HH:mm:ss}（GMT+8），
 *       避免前端拿到时间戳还要自己格式化。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /** 入参可接受的日期格式，按优先级排列 */
    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd"
    };

    private static final String OUT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final TimeZone ZONE = TimeZone.getTimeZone("GMT+8");

    /**
     * 宽松日期反序列化器：多格式尝试 + 时间戳兜底
     */
    public static class LenientDateDeserializer extends JsonDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String raw = p.getText();
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            String text = raw.trim();
            for (String pattern : PATTERNS) {
                SimpleDateFormat f = new SimpleDateFormat(pattern);
                f.setLenient(false);
                f.setTimeZone(ZONE);
                try {
                    return f.parse(text);
                } catch (Exception ignored) {
                    // 换下一种格式
                }
            }
            try {
                return new Date(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                // 不是时间戳
            }
            throw InvalidFormatException.from(p,
                    "无法解析的日期格式：「" + text + "」，请使用 yyyy-MM-dd HH:mm(:ss)", text, Date.class);
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer badmintonDateCustomizer() {
        return builder -> {
            SimpleDateFormat out = new SimpleDateFormat(OUT_PATTERN);
            out.setTimeZone(ZONE);
            builder.dateFormat(out);
            builder.deserializerByType(Date.class, new LenientDateDeserializer());
        };
    }
}
