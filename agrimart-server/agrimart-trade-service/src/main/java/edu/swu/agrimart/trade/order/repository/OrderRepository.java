package edu.swu.agrimart.trade.order.repository;

import edu.swu.agrimart.trade.order.entity.TradeOrder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

/** 订单持久化仓储，支持按业务订单号查询。 */
public interface OrderRepository extends JpaRepository<TradeOrder, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from TradeOrder p where p.id = :key")
    Optional<TradeOrder> lock(@org.springframework.data.repository.query.Param("key") Long key);

    /**
     * 按业务订单号查找订单。
     *
     * @param no 业务编号
     * @return 查询结果，记录不存在时为空
     */
    Optional<TradeOrder> findByOrderNo(String no);
}
