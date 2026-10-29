package com.oneshop.pos;

import com.oneshop.order.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PosOrderForm {
    private Long branchId;
    private String customerName = "Khách mua tại quầy";
    private String customerPhone;
    private PaymentMethod paymentMethod = PaymentMethod.STORE_PAYMENT;
    private BigDecimal amountGivenByCustomer; // Số tiền khách đưa
    private String couponCode;
    private String note;
    private List<PosOrderItemDto> items = new ArrayList<>();
}
