package com.trading.core.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.enums.RejectCode;
import com.trading.common.model.request.OrderRequest;
import com.trading.common.model.response.OrderRejectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 校验股东号是否在 Admin 中存在且启用。
 * 若 Admin 不可用或查询失败，默认允许通过（避免因 Admin 故障导致交易中断），但会记录警告日志。
 */
@Component
public class UserValidator {

    private static final Logger log = LoggerFactory.getLogger(UserValidator.class);

    @Value("${trading.admin.base-url:}")
    private String adminBaseUrl;

    @Value("${trading.admin.validate-shareholder:true}")
    private boolean validateShareholder;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public UserValidator(@Qualifier("adminRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验股东号是否存在且启用。若校验失败返回非法回报，通过则返回 null。
     * 若 Admin 不可用或未配置 base-url，默认允许通过（仅记录警告）。
     */
    public OrderRejectResponse validateShareholder(OrderRequest req) {
        if (!validateShareholder) {
            return null; // 配置关闭校验时直接通过
        }
        if (adminBaseUrl == null || adminBaseUrl.isBlank()) {
            log.warn("Admin base-url not configured, skip shareholder validation for clOrderId={}", req != null ? req.getClOrderId() : null);
            return null; // 未配置 Admin 地址时允许通过
        }
        if (req == null || req.getShareholderId() == null) {
            return null; // 已在 OrderValidation 中校验，这里不再重复
        }
        String shareholderId = req.getShareholderId();
        String url = adminBaseUrl.replaceAll("/$", "") + "/api/users/shareholder/" + shareholderId;
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                JsonNode root = objectMapper.readTree(resp.getBody());
                if (root.has("code") && root.get("code").asInt() == 0 && root.has("data")) {
                    JsonNode data = root.get("data");
                    if (data != null && !data.isNull()) {
                        // 用户存在，检查 status
                        if (data.has("status")) {
                            int status = data.get("status").asInt();
                            if (status != 1) {
                                log.info("Shareholder validation rejected: shareholderId={}, status={}", shareholderId, status);
                                return reject(req, RejectCode.INVALID_SHAREHOLDER_ID, "股东号已禁用");
                            }
                        }
                        // status=1 或未返回 status 字段，视为启用
                        log.debug("Shareholder validation passed: shareholderId={}", shareholderId);
                        return null;
                    }
                }
            }
            // 用户不存在（非 2xx 响应）
            log.info("Shareholder validation rejected: shareholderId={}, response status={}", shareholderId, resp.getStatusCode());
            return reject(req, RejectCode.INVALID_SHAREHOLDER_ID, "股东号不存在");
        } catch (HttpClientErrorException.NotFound e) {
            // 404 明确表示用户不存在
            log.info("Shareholder validation rejected: shareholderId={} not found", shareholderId);
            return reject(req, RejectCode.INVALID_SHAREHOLDER_ID, "股东号不存在");
        } catch (HttpClientErrorException e) {
            // 其他 4xx 错误（如 401、403 等），可能是 Admin 配置问题
            log.warn("Validate shareholder failed (Admin returned {}): shareholderId={}, error={}", e.getStatusCode(), shareholderId, e.getMessage());
            // 4xx 错误可能是配置问题，拒绝订单
            return reject(req, RejectCode.INVALID_SHAREHOLDER_ID, "股东号校验失败: " + e.getStatusCode());
        } catch (RestClientException e) {
            // 网络错误、超时等，可能是 Admin 不可用
            log.warn("Validate shareholder failed (Admin unavailable?): shareholderId={}, error={}", shareholderId, e.getMessage());
            // Admin 不可用时默认允许通过，避免因 Admin 故障导致交易中断
            return null;
        } catch (Exception e) {
            log.warn("Parse Admin response failed: shareholderId={}, error={}", shareholderId, e.getMessage());
            return null; // 解析失败时允许通过
        }
    }

    private OrderRejectResponse reject(OrderRequest req, RejectCode code, String text) {
        OrderRejectResponse r = new OrderRejectResponse();
        if (req != null) {
            r.setClOrderId(req.getClOrderId());
            r.setMarket(req.getMarket());
            r.setSecurityId(req.getSecurityId());
            r.setSide(req.getSide());
            r.setQty(req.getQty());
            r.setPrice(req.getPrice());
            r.setShareholderId(req.getShareholderId());
        }
        r.setRejectCode(code.getCode());
        r.setRejectText(text);
        return r;
    }
}
