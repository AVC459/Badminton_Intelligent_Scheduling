package com.ape.badminton.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 本地 Ollama 客户端
 *
 * 用于「批量导入选手名单」场景：把 Excel / 粘贴文本的原始二维数据交给本地
 * 大模型做字段语义识别（列顺序不定、表头别名、性别写法不一、单位缺失等），
 * 归一化为系统统一的选手字段。
 *
 * 默认地址 http://127.0.0.1:11434，模型 qwen2.5:7b，
 * 可通过 application.properties 的 ollama.* 配置项调整。
 *
 * 设计原则：**可降级**。Ollama 未启动 / 超过超时 / 输出无法解析时，
 * 一律返回 null 由调用方走启发式解析兜底，绝不阻塞导入主流程。
 */
@Service
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    @Value("${ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${ollama.model:qwen2.5:7b}")
    private String model;

    @Value("${ollama.enabled:true}")
    private boolean enabled;

    @Value("${ollama.timeout-seconds:60}")
    private int timeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();

    private volatile HttpClient http;

    private HttpClient client() {
        if (http == null) {
            synchronized (this) {
                if (http == null) {
                    http = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .build();
                }
            }
        }
        return http;
    }

    public String getModel() {
        return model;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 探测本地 Ollama 是否可用（GET /api/tags） */
    public boolean available() {
        if (!enabled) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> res = client().send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama 不可用：{}", e.getMessage());
            return false;
        }
    }

    /** 单次推理结果：需要区分「超时」与「服务错误」，两者处理策略不同 */
    private static final class ChatResult {
        final String content;
        final boolean timedOut;

        ChatResult(String content, boolean timedOut) {
            this.content = content;
            this.timedOut = timedOut;
        }
    }

    /**
     * 请求模型输出 JSON 结果。
     *
     * 说明：模型冷启动（长时间未使用后首次调用，需把数 GB 权重载入内存）时，
     * Ollama 往往先返回一次 500，紧接着的第二次调用就正常了。
     * 这里对这种「假失败」做一次带间隔的重试 —— 但**仅限快速失败**。
     *
     * 超时绝不重试：超时说明模型正在推理，重试只会让用户再等一遍，
     * 这正是此前「智能识别一直转圈」的原因之一。
     *
     * @return 助手回复的文本内容；确实不可用时返回 null
     */
    public String chatJson(String systemPrompt, String userPrompt) {
        if (!enabled) return null;

        long started = System.currentTimeMillis();
        ChatResult r = doChat(systemPrompt, userPrompt);
        if (r.content != null) return r.content;
        if (r.timedOut) {
            log.warn("Ollama 推理超过 {} 秒仍未返回，降级为规则解析", timeoutSeconds);
            return null;
        }
        // 已经耗掉一半预算就不再重试，避免总时长翻倍
        long elapsed = System.currentTimeMillis() - started;
        if (elapsed > timeoutSeconds * 1000L / 2) {
            log.warn("Ollama 首次调用耗时 {}ms 且失败，不再重试", elapsed);
            return null;
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        log.info("Ollama 首次调用未成功（多为模型冷启动），重试一次…");
        return doChat(systemPrompt, userPrompt).content;
    }

    private ChatResult doChat(String systemPrompt, String userPrompt) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");
            ObjectNode options = body.putObject("options");
            options.put("temperature", 0);
            // 当前只让模型输出「列映射」，正常仅几十个 token；
            // 设上限是为了模型跑飞时能尽快收尾，别把时间耗在无意义的生成上
            options.put("num_predict", 512);

            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            ObjectNode usr = messages.addObject();
            usr.put("role", "user");
            usr.put("content", userPrompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> res = client().send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                String detail = res.body() == null ? "" : res.body();
                if (detail.length() > 300) detail = detail.substring(0, 300);
                log.warn("Ollama 返回非 200：{} {}", res.statusCode(), detail);
                return new ChatResult(null, false);
            }
            JsonNode root = mapper.readTree(res.body());
            JsonNode content = root.path("message").path("content");
            return new ChatResult(content.isMissingNode() ? null : content.asText(), false);
        } catch (HttpTimeoutException e) {
            return new ChatResult(null, true);
        } catch (Exception e) {
            log.warn("调用本地 Ollama 失败，将使用规则解析兜底：{}", e.getMessage());
            return new ChatResult(null, false);
        }
    }
}
