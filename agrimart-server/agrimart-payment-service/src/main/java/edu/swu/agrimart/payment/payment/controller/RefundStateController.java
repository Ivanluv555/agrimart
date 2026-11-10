package edu.swu.agrimart.payment.payment.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.payment.payment.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** P04 保留 HTTP 查询，供交易服务定期对账使用。 */
@RestController
public class RefundStateController {
    private final RefundService refunds;
    private final String token;
    public RefundStateController(RefundService refunds,@Value("${agrimart.service-token:dev-service-token}") String token) {
        this.refunds=refunds; this.token=token;
    }
    @GetMapping("/internal/refunds/{no}")
    public Api.Result<?> state(@PathVariable String no,HttpServletRequest request) {
        RequestAuth.requireService(request,token);
        return Api.Result.ok(refunds.get(no));
    }
}
