package com.trading.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.admin.service.DataAnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 数据分析 API：将交易数据发送给 SiliconFlow 大模型，
 * 由 AI 生成自然语言分析报告和交易建议。
 * <p>
 * 用户需自行提供 SiliconFlow API Key。
 */
@RestController
@RequestMapping("/api/ai")
public class AiAnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AiAnalyticsController.class);
    private static final String DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct";
    private static final String SILICONFLOW_API = "https://api.siliconflow.cn/v1/chat/completions";

    private final DataAnalyticsService analyticsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiAnalyticsController(DataAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * AI 分析交易数据并生成报告。
     * POST /api/ai/analyze
     * Body: { "apiKey": "sk-xxx", "model": "Qwen/Qwen2.5-7B-Instruct", "question": "分析当前交易情况" }
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "请提供 SiliconFlow API Key（apiKey）"));
        }

        String model = request.getOrDefault("model", DEFAULT_MODEL);
        String question = request.getOrDefault("question", "请分析当前的交易数据，给出详细的分析报告和建议。");

        try {
            // 获取交易数据摘要
            Map<String, Object> report = analyticsService.getFullReport();
            String dataJson = objectMapper.writeValueAsString(report);

            // 构建 prompt
            String systemPrompt = "你是一个专业的量化交易分析师，专注于 A 股模拟交易系统的数据分析。"
                    + "你会收到系统的交易数据（JSON 格式），包括总体概览、按证券分组统计和按交易员分组统计。"
                    + "请用中文进行分析，使用 Markdown 格式输出，包含以下方面："
                    + "1. 整体交易概况总结 2. 交易活跃度分析 3. 成交效率分析（成交率、撤单率）"
                    + "4. 各证券品种表现分析 5. 各交易员交易行为分析 6. 风险提示和交易建议"
                    + "请确保分析专业、有深度，并给出可操作的建议。";

            String userMessage = "以下是当前交易系统的数据：\n\n```json\n" + dataJson + "\n```\n\n" + question;

            // 调用 SiliconFlow API
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));
            payload.put("temperature", 0.7);
            payload.put("max_tokens", 4096);
            payload.put("stream", false);

            String payloadJson = objectMapper.writeValueAsString(payload);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(SILICONFLOW_API))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                    .build();

            HttpResponse<String> httpResp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpResp.statusCode() != 200) {
                log.warn("SiliconFlow API error: status={}, body={}", httpResp.statusCode(), httpResp.body());
                // 尝试解析错误信息
                String errorMsg = "AI 服务返回错误 (" + httpResp.statusCode() + ")";
                try {
                    JsonNode errNode = objectMapper.readTree(httpResp.body());
                    if (errNode.has("message")) {
                        errorMsg = errNode.get("message").asText();
                    } else if (errNode.has("error") && errNode.get("error").has("message")) {
                        errorMsg = errNode.get("error").get("message").asText();
                    }
                } catch (Exception ignored) {}
                return ResponseEntity.ok(Map.of("code", httpResp.statusCode(), "message", errorMsg));
            }

            JsonNode respNode = objectMapper.readTree(httpResp.body());
            String aiContent = respNode.path("choices").path(0).path("message").path("content").asText("");
            JsonNode usage = respNode.path("usage");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("analysis", aiContent);
            result.put("model", model);
            result.put("usage", Map.of(
                    "promptTokens", usage.path("prompt_tokens").asInt(0),
                    "completionTokens", usage.path("completion_tokens").asInt(0),
                    "totalTokens", usage.path("total_tokens").asInt(0)
            ));

            return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", result));

        } catch (Exception e) {
            log.error("AI analysis failed", e);
            return ResponseEntity.ok(Map.of("code", 500, "message", "AI 分析失败: " + e.getMessage()));
        }
    }

    /**
     * 获取可用的模型列表。
     * GET /api/ai/models
     */
    @GetMapping("/models")
    public ResponseEntity<?> getModels() {
        List<Map<String, String>> models = List.of(
                Map.of("id", "Qwen/Qwen2.5-7B-Instruct", "name", "Qwen2.5 7B (免费)"),
                Map.of("id", "Qwen/Qwen2.5-72B-Instruct", "name", "Qwen2.5 72B"),
                Map.of("id", "deepseek-ai/DeepSeek-V3", "name", "DeepSeek V3"),
                Map.of("id", "deepseek-ai/DeepSeek-R1", "name", "DeepSeek R1 (推理)"),
                Map.of("id", "THUDM/glm-4-9b-chat", "name", "GLM-4 9B"),
                Map.of("id", "meta-llama/Meta-Llama-3.1-8B-Instruct", "name", "Llama 3.1 8B")
        );
        return ResponseEntity.ok(Map.of("code", 0, "data", models));
    }
}
