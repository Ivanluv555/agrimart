package edu.swu.agrimart.gateway.documentation.controller;

import edu.swu.agrimart.gateway.documentation.service.DocumentationService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 为统一 Swagger UI 提供各业务服务的 OpenAPI 文档。 */
@Hidden
@RestController
public class DocumentationController {
    private final DocumentationService documentation;

    /**
     * 创建文档代理接口。
     * 
     * @param documentation 文档获取及网关适配服务
     */
    public DocumentationController(DocumentationService documentation) {
        this.documentation = documentation;
    }

    /**
     * 返回适配网关调用地址及认证方式的业务文档。
     * 
     * @param service 业务服务标识
     * @param request 当前 HTTP 请求
     * @return 供 Swagger UI 展示和调试的 OpenAPI 文档
     */
    @GetMapping("/docs/{service}")
    public Map<String, Object> document(@PathVariable String service, HttpServletRequest request) {
        return documentation.document(service, request.getContextPath() + "/api");
    }
}
