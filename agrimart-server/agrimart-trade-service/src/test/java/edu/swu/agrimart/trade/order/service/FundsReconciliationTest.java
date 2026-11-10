package edu.swu.agrimart.trade.order.service;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.payment.dto.RefundState;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

/** HTTP 对账能够在没有 broker 的情况下修复投影，并重建同号退款命令。 */
@SpringBootTest
@Transactional
class FundsReconciliationTest {
    @Autowired OrderRepository orders;
    @Autowired OrderFundsService funds;
    @Autowired MessageStore messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    TradeOrder order() {
        var order=new TradeOrder();order.setOrderNo("O"+UUID.randomUUID());order.setPaymentNo("P"+UUID.randomUUID());
        order.setBuyerId(3L);order.setMerchantId(8L);order.setTotalAmount(new BigDecimal("12.30"));
        return orders.saveAndFlush(order);
    }
    FundsEvent paid(TradeOrder order) {
        return FundsEvent.of(FundsEvent.Kind.PAYMENT_STATE,order.getPaymentNo(),2,order.getId(),order.getPaymentNo(),null,
                order.getTotalAmount(),"SUCCEEDED","provider-paid",OffsetDateTime.now());
    }
    @Test
    void p03AndP04RepairMissingPaymentAndRefundEvents() throws Exception {
        var order=order(); order.setStatus(TradeOrder.Status.CLOSING); orders.flush();
        FundsEvent state=paid(order); String refund="R"+order.getPaymentNo();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Service-Token")).isEqualTo("test-service");
            Object body=exchange.getRequestURI().getPath().startsWith("/internal/payments/") ? state :
                    new RefundState(refund,order.getPaymentNo(),order.getId(),"12.30","CNY","SUCCEEDED",2,"provider-refund",OffsetDateTime.now());
            byte[] bytes=JsonMapper.builder().build().writeValueAsBytes(Api.Result.ok(body));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            new FundsReconciliation(jdbc,funds,messages,transactionManager,"http://127.0.0.1:"+server.getAddress().getPort(),"test-service").reconcile();
            assertThat(order.getStatus()).isEqualTo(TradeOrder.Status.REFUNDED);
            assertThat(jdbc.queryForObject("SELECT status FROM fund_refunds WHERE refund_no=?",String.class,refund)).isEqualTo("SUCCEEDED");
        } finally { server.stop(0); }
    }
    @Test
    void missingP04RecordReissuesExistingRefundNumber() throws Exception {
        var order=order(); order.setStatus(TradeOrder.Status.CLOSING); orders.flush();
        var state=paid(order);funds.accept(state);orders.flush();String refund="R"+order.getPaymentNo();
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE routing_key='refund.execute.v1' AND payload LIKE ?",Long.class,"%"+refund+"%");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            if (exchange.getRequestURI().getPath().startsWith("/internal/refunds/")) { exchange.sendResponseHeaders(404,-1);exchange.close();return; }
            byte[] bytes=JsonMapper.builder().build().writeValueAsBytes(Api.Result.ok(state));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            new FundsReconciliation(jdbc,funds,messages,transactionManager,"http://127.0.0.1:"+server.getAddress().getPort(),"test-service").reconcile();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE routing_key='refund.execute.v1' AND payload LIKE ?",Long.class,"%"+refund+"%")).isEqualTo(before+1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fund_refunds WHERE refund_no=?",Long.class,refund)).isEqualTo(1);
        } finally { server.stop(0); }
    }
}
