package edu.swu.agrimart.trade.order.controller;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.auth.service.RequestAuth;
import edu.swu.agrimart.common.payment.dto.RefundState;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** T03 只读取交易服务自己持久化的退款授权，不跨库访问资金记录。 */
@RestController
public class RefundAuthorizationController {
    private final JdbcTemplate jdbc;
    private final String token;
    public RefundAuthorizationController(JdbcTemplate jdbc,@Value("${agrimart.service-token:dev-service-token}") String token) {
        this.jdbc=jdbc;this.token=token;
    }
    @GetMapping("/internal/refunds/{no}/execution-authorization")
    public Api.Result<?> authorize(@PathVariable String no,HttpServletRequest request) {
        RequestAuth.requireService(request,token);
        return Api.Result.ok(jdbc.query("SELECT * FROM fund_refunds WHERE refund_no=?",(rs,n)->new RefundState(
                rs.getString("refund_no"),rs.getString("payment_no"),rs.getLong("order_id"),rs.getBigDecimal("amount").setScale(2).toPlainString(),
                "CNY",rs.getString("status"),rs.getLong("version"),null,null),no).stream().findFirst()
                .orElseThrow(()->new java.util.NoSuchElementException("RESOURCE_NOT_FOUND")));
    }
}
