package edu.swu.agridemo.module.order.service;

import edu.swu.agridemo.core.exception.BusinessException;
import edu.swu.agridemo.core.result.PageResult;
import edu.swu.agridemo.module.order.dto.*;
import edu.swu.agridemo.module.order.entity.*;
import edu.swu.agridemo.module.order.repository.*;
import edu.swu.agridemo.module.product.entity.Product;
import edu.swu.agridemo.module.product.repository.ProductRepository;
import edu.swu.agridemo.module.user.entity.User;
import edu.swu.agridemo.module.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                       UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OrderVo createOrder(Long userId, OrderCreateDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUser(user);
        order.setShippingAddress(dto.shippingAddress());
        order.setReceiverName(dto.receiverName());
        order.setReceiverPhone(dto.receiverPhone());
        order.setRemark(dto.remark());
        order.setStatus(Order.OrderStatus.PENDING_PAYMENT);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto itemDto : dto.items()) {
            Product product = productRepository.findById(itemDto.productId())
                    .orElseThrow(() -> new BusinessException("商品不存在：" + itemDto.productId()));

            if (product.getStock() < itemDto.quantity()) {
                throw new BusinessException("商品库存不足：" + product.getName());
            }

            product.setStock(product.getStock() - itemDto.quantity());
            product.setSalesCount(product.getSalesCount() + itemDto.quantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setProductName(product.getName());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(itemDto.quantity());

            BigDecimal subtotal = product.getPrice().multiply(new BigDecimal(itemDto.quantity()));
            orderItem.setSubtotal(subtotal);

            if (product.getImages() != null && !product.getImages().isEmpty()) {
                orderItem.setProductImage(product.getImages().split(",")[0]);
            }

            order.getOrderItems().add(orderItem);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setTotalAmount(totalAmount);
        order = orderRepository.save(order);

        return convertToVo(order);
    }

    public OrderVo getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        return convertToVo(order);
    }

    public OrderVo getOrderByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        return convertToVo(order);
    }

    public PageResult<OrderVo> getUserOrders(Long userId, Order.OrderStatus status, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage;

        if (status != null) {
            orderPage = orderRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            orderPage = orderRepository.findByUserId(userId, pageable);
        }

        List<OrderVo> records = orderPage.getContent().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return PageResult.of(records, orderPage.getTotalElements(), page, size);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        order.setStatus(status);
        orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!order.getUser().getId().equals(userId)) {
            throw new BusinessException("无权操作此订单");
        }

        if (order.getStatus() != Order.OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("订单状态不允许取消");
        }

        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            product.setSalesCount(product.getSalesCount() - item.getQuantity());
            productRepository.save(product);
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%04d", new Random().nextInt(10000));
        return "ORD" + timestamp + random;
    }

    private OrderVo convertToVo(Order order) {
        List<OrderItemVo> itemVos = order.getOrderItems().stream()
                .map(this::convertItemToVo)
                .collect(Collectors.toList());

        return new OrderVo(
            order.getId(),
            order.getOrderNo(),
            order.getUser().getId(),
            order.getUser().getUsername(),
            itemVos,
            order.getTotalAmount(),
            order.getStatus(),
            order.getShippingAddress(),
            order.getReceiverName(),
            order.getReceiverPhone(),
            order.getRemark(),
            order.getTrackingNumber(),
            order.getCreatedAt()
        );
    }

    private OrderItemVo convertItemToVo(OrderItem item) {
        return new OrderItemVo(
            item.getId(),
            item.getProduct().getId(),
            item.getProductName(),
            item.getPrice(),
            item.getQuantity(),
            item.getSubtotal(),
            item.getProductImage()
        );
    }
}
