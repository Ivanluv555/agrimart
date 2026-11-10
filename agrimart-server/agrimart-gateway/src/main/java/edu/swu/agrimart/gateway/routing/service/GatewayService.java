package edu.swu.agrimart.gateway.routing.service;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/** 负责目标路由、会话校验及可信身份转发的网关服务。 */
@Service
public class GatewayService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String userUrl, tradeUrl, paymentUrl, serviceToken, gatewayToken;

    /**
     * 创建负责目标路由、会话校验及可信身份转发的网关服务。
     *
     * @param builder HTTP 客户端构建器
     * @param mapper JSON 序列化器
     * @param userUrl 用户服务地址
     * @param tradeUrl 交易服务地址
     * @param paymentUrl 支付服务地址
     * @param serviceToken 内部调用凭据
     * @param gatewayToken 网关调用凭据
     */
    public GatewayService(
            RestClient.Builder builder,
            ObjectMapper mapper,
            @Value("${agrimart.routes.user:http://localhost:8081}") String userUrl,
            @Value("${agrimart.routes.trade:http://localhost:8082}") String tradeUrl,
            @Value("${agrimart.routes.payment:http://localhost:8083}") String paymentUrl,
            @Value("${agrimart.service-token:dev-service-token}") String serviceToken,
            @Value("${agrimart.gateway-token:dev-gateway-token}") String gatewayToken) {
        this.client = builder.build();
        this.mapper = mapper;
        this.userUrl = userUrl;
        this.tradeUrl = tradeUrl;
        this.paymentUrl = paymentUrl;
        this.serviceToken = serviceToken;
        this.gatewayToken = gatewayToken;
    }

    /**
     * 返回当前服务的健康状态。
     *
     * @return 接口响应数据
     */
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /**
     * 校验用户身份并向目标业务服务转发请求。
     *
     * @param request 请求参数或请求上下文
     * @param response 当前 HTTP 响应
     * @throws IOException 处理请求时发生异常
     */
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/internal") || path.startsWith("/api/internal/") || path.startsWith("/internal/")) {
            response.sendError(404);
            return;
        }
        String target = target(path);
        if (target == null) {
            response.sendError(404);
            return;
        }
        String bearer = RequestAuth.bearer(request);
        boolean publicPath = RequestAuth.publicOperation(request.getMethod(), path.substring(4));
        String userId = null, role = null;
        if (!publicPath) {
            if (bearer == null) {
                writeError(response, 401, "UNAUTHENTICATED");
                return;
            }
            try {
                Map<?, ?> body =
                        mapper.readValue(
                                Objects.requireNonNull(
                                        client.post()
                                                .uri(userUrl + "/internal/auth/introspections")
                                                .header("X-Service-Token", serviceToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .body(Map.of("accessToken", bearer))
                                                .retrieve()
                                                .body(String.class)),
                                Map.class);
                Map<?, ?> data = (Map<?, ?>) body.get("data");
                if (data == null || data.get("userId") == null || data.get("role") == null)
                    throw new SecurityException("UNAUTHENTICATED");
                userId = String.valueOf(data.get("userId"));
                role = String.valueOf(data.get("role"));
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value() == 401 ? 401 : 503;
                writeError(response, status, status == 401 ? "UNAUTHENTICATED" : "DEPENDENCY_UNAVAILABLE");
                return;
            } catch (SecurityException ex) {
                writeError(response, 401, "UNAUTHENTICATED");
                return;
            } catch (Exception ex) {
                writeError(response, 503, "DEPENDENCY_UNAVAILABLE");
                return;
            }
        }
        try {
            byte[] body = request.getInputStream().readAllBytes();
            final String forwardedUserId = userId;
            final String forwardedRole = role;
            RestClient.RequestBodySpec spec =
                    client.method(HttpMethod.valueOf(request.getMethod()))
                            .uri(target + path.substring(4) + query(request))
                            .headers(
                                    h -> {
                                        Collections.list(request.getHeaderNames())
                                                .forEach(
                                                        n -> {
                                                            if (!Set.of("authorization", "x-service-token", "x-gateway-token",
                                                                            "host", "content-length", "connection", "transfer-encoding")
                                                                    .contains(n.toLowerCase(Locale.ROOT))
                                                                    && !n.equalsIgnoreCase(
                                                                            "X-User-Id")
                                                                    && !n.equalsIgnoreCase(
                                                                            "X-User-Role"))
                                                                h.add(n, request.getHeader(n));
                                                        });
                                        h.set("X-Gateway-Token", gatewayToken);
                                        if (target.equals(userUrl) && bearer != null && !publicPath)
                                            h.setBearerAuth(bearer);
                                        if (forwardedUserId != null) {
                                            h.set("X-User-Id", forwardedUserId);
                                            h.set("X-User-Role", forwardedRole);
                                        }
                                    });
            ResponseEntity<byte[]> result =
                    (body.length == 0
                            ? spec.retrieve().toEntity(byte[].class)
                            : spec.body(body)
                                    .retrieve()
                                    .toEntity(byte[].class));
            response.setStatus(result.getStatusCode().value());
            if (result.getHeaders().getContentType() != null)
                response.setContentType(result.getHeaders().getContentType().toString());
            if (result.getBody() != null) response.getOutputStream().write(result.getBody());
        } catch (RestClientResponseException ex) {
            response.setStatus(ex.getStatusCode().value());
            if (ex.getResponseHeaders() != null && ex.getResponseHeaders().getContentType() != null)
                response.setContentType(ex.getResponseHeaders().getContentType().toString());
            response.getOutputStream().write(ex.getResponseBodyAsByteArray());
        } catch (Exception ex) {
            writeError(response, 503, "DEPENDENCY_UNAVAILABLE");
        }
    }

    /**
     * 根据业务路径选择目标服务地址。
     *
     * @param path 当前请求路径
     * @return 接口响应数据
     */
    private String target(String path) {
        if (path.startsWith("/api/orders/") && path.contains("/payments")) return paymentUrl;
        if (path.startsWith("/api/payments/") || path.startsWith("/api/payment-callbacks/"))
            return paymentUrl;
        if (path.startsWith("/api/users/")
                || path.startsWith("/api/merchant-applications")
                || path.startsWith("/api/admin/merchant-applications")) return userUrl;
        return tradeUrl;
    }

    /**
     * 提取需要保留的查询字符串。
     *
     * @param r 当前 HTTP 请求
     * @return 接口响应数据
     */
    private String query(HttpServletRequest r) {
        String q = r.getQueryString();
        return q == null ? "" : "?" + q;
    }

    /**
     * 向客户端写入统一网关错误响应。
     *
     * @param r 当前 HTTP 请求
     * @param status 业务状态
     * @param code 响应状态码
     * @throws IOException 处理请求时发生异常
     */
    private void writeError(HttpServletResponse r, int status, String code) throws IOException {
        r.setStatus(status);
        r.setContentType("application/json");
        r.getWriter()
                .write(
                        mapper.writeValueAsString(
                                new Api.ErrorBody(status, code, code, null, UUID.randomUUID().toString())));
    }
}
