package edu.swu.agrimart.gateway.documentation.service;

import edu.swu.agrimart.common.auth.service.RequestAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 获取独立服务文档，并为通过网关调试适配地址和 Bearer 认证。 */
@Service
public class DocumentationService {
    private final RestClient client;
    private final Map<String, String> services;

    /**
     * 创建使用现有业务路由地址的文档服务。
     * 
     * @param builder    HTTP 客户端构建器
     * @param userUrl    用户服务地址
     * @param tradeUrl   交易服务地址
     * @param paymentUrl 支付服务地址
     */
    public DocumentationService(
            RestClient.Builder builder,
            @Value("${agrimart.routes.user:http://localhost:8081}") String userUrl,
            @Value("${agrimart.routes.trade:http://localhost:8082}") String tradeUrl,
            @Value("${agrimart.routes.payment:http://localhost:8083}") String paymentUrl) {
        client = builder.build();
        services = Map.of("user", userUrl, "trade", tradeUrl, "payment", paymentUrl);
    }

    /**
     * 获取文档并设置网关调用前缀；内部接口仍保留在服务自身文档中。
     * 
     * @param service 固定列表中的业务服务标识
     * @param apiBase 网关业务接口前缀
     * @return 经过网关适配的 OpenAPI 文档
     */
    public Map<String, Object> document(String service, String apiBase) {
        String url = services.get(service);
        if (url == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未知文档服务");
        Map<String, Object> document;
        try {
            document = client.get().uri(url + "/v3/api-docs").retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "业务服务文档暂不可用", ex);
        }
        if (document == null || !(document.get("paths") instanceof Map)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "业务服务返回无效文档");
        }
        document.put("servers", List.of(Map.of("url", apiBase)));
        Map<String, Object> paths = object(document, "paths");
        paths.keySet().removeIf(path -> path.equals("/internal") || path.startsWith("/internal/"));
        object(object(document, "components"), "securitySchemes").put(
                "bearerAuth", Map.of("type", "http", "scheme", "bearer"));
        document.put("security", List.of(Map.of("bearerAuth", List.of())));
        for (String path : paths.keySet()) {
            for (String method : List.of("get", "post")) {
                if (RequestAuth.publicOperation(method.toUpperCase(java.util.Locale.ROOT), path)
                        && object(paths, path).containsKey(method))
                    object(object(paths, path), method).put("security", List.of());
            }
        }
        return document;
    }

    /**
     * 读取或补充 OpenAPI 文档中的对象节点。
     * 
     * @param parent 上级对象
     * @param key    子对象名称
     * @return 可修改的子对象
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
    }
}
