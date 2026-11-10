package edu.swu.agrimart.gateway.routing.controller;

import edu.swu.agrimart.gateway.routing.service.GatewayService;

import io.swagger.v3.oas.annotations.Hidden;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/** 提供统一 API 入口，将请求交给网关路由服务。 */
@RestController
@Hidden
public class GatewayController {
    private final GatewayService gateway;

    /**
     * 创建统一网关接口。
     *
     * @param gateway 网关路由及身份校验服务
     */
    public GatewayController(GatewayService gateway) {
        this.gateway = gateway;
    }

    /**
     * 返回网关健康状态。
     *
     * @return 当前网关的可用状态
     */
    @GetMapping("/actuator/health")
    public Map<String, String> health() {
        return gateway.health();
    }

    /**
     * 将统一 API 请求交给网关服务进行身份校验和业务转发。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @throws IOException 读取请求或写入响应时发生异常
     */
    @RequestMapping(value = "/api/**", method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE
    })
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        gateway.proxy(request, response);
    }
}
