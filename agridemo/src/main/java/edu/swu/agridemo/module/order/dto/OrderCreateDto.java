package edu.swu.agridemo.module.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateDto(
    @NotEmpty(message = "订单项不能为空")
    @Valid
    List<OrderItemDto> items,

    @NotBlank(message = "收货地址不能为空")
    @Size(max = 200, message = "收货地址长度不超过200个字符")
    String shippingAddress,

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名长度不超过50个字符")
    String receiverName,

    @NotBlank(message = "收货人电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    String receiverPhone,

    String remark
) {}
