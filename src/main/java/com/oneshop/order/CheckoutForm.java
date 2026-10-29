package com.oneshop.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutForm {

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    private String recipientName;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    private String recipientPhone;

    private String recipientEmail;

    private String shippingAddress;

    private String shippingRegion = "INNER_CITY";

    @NotNull(message = "Vui lòng chọn chi nhánh xử lý đơn hàng")
    private Long branchId;

    @NotNull(message = "Vui lòng chọn hình thức nhận hàng")
    private FulfillmentType fulfillmentType = FulfillmentType.DELIVERY;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    private String note;
}
