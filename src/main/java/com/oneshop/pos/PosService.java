package com.oneshop.pos;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.inventory.InventoryService;
import com.oneshop.order.*;
import com.oneshop.promotion.Coupon;
import com.oneshop.promotion.CouponRepository;
import com.oneshop.promotion.CouponUsage;
import com.oneshop.promotion.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosService {

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final com.oneshop.user.UserRepository userRepository;

    @Transactional
    public Order createPosOrder(PosOrderForm form, Long branchId, String staffName, String staffEmail) {
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng chưa có sản phẩm nào. Vui lòng chọn ít nhất 1 sản phẩm!");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh: " + branchId));

        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String randomDigits = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        String orderCode = "OS-POS-" + dateStr + "-" + randomDigits;

        String customerPhone = StringUtils.hasText(form.getCustomerPhone()) ? form.getCustomerPhone().trim() : "";
        com.oneshop.user.User linkedCustomer = null;
        if (StringUtils.hasText(customerPhone)) {
            String cleanPhone = customerPhone.replaceAll("[^0-9+]", "");
            linkedCustomer = userRepository.findFirstByPhone(cleanPhone).orElse(null);
            if (linkedCustomer == null && cleanPhone.startsWith("0")) {
                linkedCustomer = userRepository.findFirstByPhone("+84" + cleanPhone.substring(1)).orElse(null);
            }
        }

        String customerName;
        if (StringUtils.hasText(form.getCustomerName())) {
            customerName = form.getCustomerName().trim();
        } else if (linkedCustomer != null) {
            customerName = linkedCustomer.getFullName();
        } else {
            customerName = "Khách mua tại quầy";
        }

        PaymentMethod paymentMethod = form.getPaymentMethod() != null ? form.getPaymentMethod() : PaymentMethod.STORE_PAYMENT;

        Order order = Order.builder()
                .orderCode(orderCode)
                .user(linkedCustomer) // Link to registered customer account if found, otherwise null
                .branch(branch)
                .fulfillmentType(FulfillmentType.PICKUP)
                .status(OrderStatus.PICKED_UP)
                .paymentStatus(PaymentStatus.PAID)
                .paymentMethod(paymentMethod)
                .recipientName(customerName)
                .cashierName(staffName)
                .recipientPhone(customerPhone)
                .shippingAddress("Bán trực tiếp tại quầy: " + branch.getName() + " (" + branch.getAddress() + ")")
                .note(form.getNote())
                .subtotal(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (PosOrderItemDto itemDto : form.getItems()) {
            if (itemDto.getProductId() == null || itemDto.getQuantity() == null || itemDto.getQuantity() <= 0) {
                continue;
            }

            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại: " + itemDto.getProductId()));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalStateException("Sản phẩm '" + product.getName() + "' hiện đã ngừng kinh doanh");
            }

            // Deduct stock via FEFO batch allocation
            try {
                inventoryService.allocateStockFEFO(branch.getId(), product.getId(), itemDto.getQuantity());
            } catch (Exception ex) {
                log.debug("POS FEFO allocation note: {}", ex.getMessage());
            }

            // Deduct stock from Branch Inventory
            inventoryService.deductStockForOrder(
                    branch.getId(),
                    product.getId(),
                    itemDto.getQuantity(),
                    orderCode,
                    "Thu ngân POS: " + staffName
            );

            BigDecimal unitPrice = product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .skuSnapshot(product.getSku())
                    .imageUrl(product.getPrimaryImageUrl())
                    .unitPrice(unitPrice)
                    .quantity(itemDto.getQuantity())
                    .lineTotal(lineTotal)
                    .build();

            order.addItem(orderItem);
        }

        if (order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn số lượng hợp lệ cho các sản phẩm trong đơn");
        }

        // Apply coupon if valid
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        if (StringUtils.hasText(form.getCouponCode())) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(form.getCouponCode().trim()).orElse(null);
            if (coupon != null && coupon.isValid(subtotal)) {
                discount = coupon.calculateDiscount(subtotal);
                coupon.setUsedCount(coupon.getUsedCount() + 1);
                couponRepository.save(coupon);
                order.setCouponCode(coupon.getCode());
                appliedCoupon = coupon;
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discount).max(BigDecimal.ZERO);
        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        // Record coupon usage
        if (appliedCoupon != null) {
            CouponUsage usage = CouponUsage.builder()
                    .coupon(appliedCoupon)
                    .order(savedOrder)
                    .discountAmount(discount)
                    .build();
            couponUsageRepository.save(usage);
        }

        // Record successful payment
        Payment payment = Payment.builder()
                .order(savedOrder)
                .amount(totalAmount)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PAID)
                .transactionCode(orderCode)
                .payDate(LocalDateTime.now())
                .note("Đã thu tiền tại quầy POS bởi: " + staffName +
                        (form.getAmountGivenByCustomer() != null ? " (Tiền khách đưa: " + form.getAmountGivenByCustomer() + "đ)" : ""))
                .build();
        paymentRepository.save(payment);

        // Record order history
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .order(savedOrder)
                .fromStatus(null)
                .toStatus(OrderStatus.PICKED_UP)
                .changedBy(staffName + " (" + staffEmail + ")")
                .note("Bán lẻ trực tiếp tại quầy thu ngân chi nhánh [" + branch.getName() + "]. Hoàn tất và giao hàng cho khách.")
                .build());

        log.info("POS Order created successfully: {} with total: {}", savedOrder.getOrderCode(), savedOrder.getTotalAmount());
        return savedOrder;
    }
}
