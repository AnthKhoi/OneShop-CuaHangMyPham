package com.oneshop.order;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartItemDto;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.inventory.InventoryService;
import com.oneshop.promotion.Coupon;
import com.oneshop.promotion.CouponRepository;
import com.oneshop.promotion.CouponUsage;
import com.oneshop.promotion.CouponUsageRepository;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final InventoryService inventoryService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final PaymentService paymentService;

    @Transactional
    public Order createOrder(CartDto cart, CheckoutForm form, Long userId) {
        if (cart == null || cart.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng của bạn đang trống");
        }

        Branch branch = branchRepository.findById(form.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Chi nhánh được chọn không tồn tại"));

        User customer = null;
        if (userId != null) {
            customer = userRepository.findById(userId).orElse(null);
        }

        // Generate unique Order Code with collision retry: OS-YYYYMMDD-XXXXXX
        String dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String orderCode;
        int codeAttempts = 0;
        do {
            String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            orderCode = "OS-" + dateStr + "-" + randomSuffix;
            codeAttempts++;
        } while (orderRepository.existsByOrderCode(orderCode) && codeAttempts < 10);

        boolean isPickup = form.getFulfillmentType() == FulfillmentType.PICKUP;
        if (!isPickup) {
            if (!StringUtils.hasText(form.getShippingAddress()) || form.getShippingAddress().trim().length() < 5) {
                throw new IllegalArgumentException("Vui lòng nhập địa chỉ giao hàng hợp lệ (tối thiểu 5 ký tự)");
            }
        }
        String address = isPickup ? "Nhận tại cửa hàng: " + branch.getName() + " - " + branch.getAddress() : form.getShippingAddress().trim();
        String accessToken = UUID.randomUUID().toString().replace("-", "");

        Order order = Order.builder()
                .orderCode(orderCode)
                .accessToken(accessToken)
                .user(customer)
                .branch(branch)
                .fulfillmentType(form.getFulfillmentType())
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(form.getPaymentMethod())
                .recipientName(form.getRecipientName().trim())
                .recipientPhone(form.getRecipientPhone().trim())
                .recipientEmail(form.getRecipientEmail())
                .shippingAddress(address)
                .note(form.getNote())
                .subtotal(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        // Process each item: verify price from DB, allocate FEFO batches + deduct branch stock, create snapshot
        for (CartItemDto itemDto : cart.getItems()) {
            if (itemDto.getQuantity() <= 0 || itemDto.getQuantity() > 100) {
                throw new IllegalArgumentException("Số lượng mua cho mỗi sản phẩm phải từ 1 đến 100");
            }

            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại: " + itemDto.getProductId()));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalStateException("Sản phẩm '" + product.getName() + "' hiện không còn kinh doanh");
            }

            // Attempt FEFO batch allocation first (if batches are registered)
            try {
                inventoryService.allocateStockFEFO(branch.getId(), product.getId(), itemDto.getQuantity());
            } catch (Exception e) {
                log.debug("Batch allocation info for product {}: {}", product.getName(), e.getMessage());
            }

            // Deduct stock in selected branch inventory
            inventoryService.deductStockForOrder(
                    branch.getId(),
                    product.getId(),
                    itemDto.getQuantity(),
                    orderCode,
                    form.getRecipientName()
            );

            BigDecimal unitPrice = product.getPrice(); // Snapshot current price
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

        // Calculate discount from coupon
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        if (StringUtils.hasText(cart.getCouponCode())) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(cart.getCouponCode().trim()).orElse(null);
            if (coupon != null && coupon.isValid(subtotal)) {
                boolean alreadyUsed = false;
                if (userId != null) {
                    alreadyUsed = couponUsageRepository.findByUserId(userId).stream()
                            .anyMatch(u -> u.getCoupon().getId().equals(coupon.getId()));
                }
                if (!alreadyUsed) {
                    discount = coupon.calculateDiscount(subtotal);
                    coupon.setUsedCount(coupon.getUsedCount() + 1);
                    couponRepository.save(coupon);
                    order.setCouponCode(coupon.getCode());
                    appliedCoupon = coupon;
                }
            }
        }

        // Calculate shipping fee according to store policy:
        // - In-store pickup: 0₫
        // - Inner City (Nội thành TP.HCM bao gồm Quận 1, Thủ Đức, v.v.): MIỄN PHÍ (0₫)
        // - Outer City (Ngoại thành TP.HCM): 25.000₫ (Miễn phí từ 500k)
        // - Province / Toàn quốc: 35.000₫ (Miễn phí từ 500k)
        BigDecimal shippingFee = BigDecimal.ZERO;
        if (!isPickup) {
            String region = form.getShippingRegion();
            String rawAddr = (form.getShippingAddress() != null) ? form.getShippingAddress().toLowerCase() : "";
            if (!rawAddr.isBlank()) {
                String cleanAddr = java.text.Normalizer.normalize(rawAddr, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                        .replace('đ', 'd');
                boolean isOuter = cleanAddr.matches(".*\\b(cu\\s*chi|hoc\\s*mon|binh\\s*chanh|nha\\s*be|can\\s*gio)\\b.*");
                boolean isInner = cleanAddr.matches(".*\\b(quan\\s*1|q\\s*1|q\\.1|quan\\s*2|q\\s*2|q\\.2|quan\\s*3|q\\s*3|quan\\s*4|q\\s*4|quan\\s*5|q\\s*5|quan\\s*6|q\\s*6|quan\\s*7|q\\s*7|quan\\s*8|q\\s*8|quan\\s*9|q\\s*9|q\\.9|quan\\s*10|q\\s*10|quan\\s*11|q\\s*11|quan\\s*12|q\\s*12|thu\\s*duc|binh\\s*tan|binh\\s*thanh|go\\s*vap|phu\\s*nhuan|tan\\s*binh|tan\\s*phu)\\b.*")
                        || rawAddr.contains("bến nghé") || rawAddr.contains("thảo điền") || rawAddr.contains("an phú") || rawAddr.contains("bình tân")
                        || rawAddr.contains("quận 2") || rawAddr.contains("quận 9") || rawAddr.contains("quận 12")
                        || rawAddr.contains("hồ chí minh") || rawAddr.contains("tp.hcm") || rawAddr.contains("tphcm") || rawAddr.contains("sài gòn");

                if (isOuter) {
                    region = "OUTER_CITY";
                } else if (isInner) {
                    region = "INNER_CITY";
                } else {
                    region = "PROVINCE";
                }
            } else if (region == null || region.isBlank()) {
                region = "INNER_CITY";
            }

            if ("INNER_CITY".equalsIgnoreCase(region)) {
                shippingFee = BigDecimal.ZERO; // Nội thành hoàn toàn miễn phí
            } else if ("OUTER_CITY".equalsIgnoreCase(region)) {
                shippingFee = subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(25000);
            } else {
                shippingFee = subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(35000);
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discount).add(shippingFee).max(BigDecimal.ZERO);

        order.setSubtotal(subtotal);
        order.setDiscountAmount(discount);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        // Record Coupon Usage if applied
        if (appliedCoupon != null) {
            CouponUsage usage = CouponUsage.builder()
                    .coupon(appliedCoupon)
                    .user(customer)
                    .order(savedOrder)
                    .discountAmount(discount)
                    .build();
            couponUsageRepository.save(usage);
        }

        // Initialize Payment record
        paymentService.createPayment(savedOrder, form.getPaymentMethod(), totalAmount);

        // Record Initial Order Status History
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .order(savedOrder)
                .fromStatus(null)
                .toStatus(OrderStatus.PENDING)
                .changedBy(customer != null ? customer.getEmail() : "KHÁCH HÀNG (" + form.getRecipientName() + ")")
                .note("Đơn hàng mới được khởi tạo với tổng thanh toán " + totalAmount + " VND")
                .build());

        log.info("Order successfully created: {} with total {}", savedOrder.getOrderCode(), savedOrder.getTotalAmount());
        return savedOrder;
    }

    @Transactional
    public Order cancelOrder(Long orderId, String cancelReason, String actor) {
        Order order = getOrderById(orderId);

        if (!order.getStatus().canCancel()) {
            throw new IllegalStateException("Đơn hàng ở trạng thái '" + order.getStatus().getDescription() + "' không thể hủy");
        }

        // Restore stock to the branch
        for (OrderItem item : order.getItems()) {
            if (item.getProduct() != null) {
                inventoryService.restoreStockForCancelledOrder(
                        order.getBranch().getId(),
                        item.getProduct().getId(),
                        item.getQuantity(),
                        order.getOrderCode(),
                        actor
                );
            }
        }

        OrderStatus fromStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(cancelReason != null ? cancelReason : "Hủy bởi " + actor);
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        Order savedOrder = orderRepository.save(order);

        // Record status history
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .order(savedOrder)
                .fromStatus(fromStatus)
                .toStatus(OrderStatus.CANCELLED)
                .changedBy(actor)
                .note("Hủy đơn hàng: " + order.getCancelReason())
                .build());

        log.info("Order {} cancelled by {}. Reason: {}", order.getOrderCode(), actor, cancelReason);
        return savedOrder;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, String actor) {
        Order order = getOrderById(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Đơn hàng đã hủy, không thể thay đổi trạng thái");
        }
        if (order.getStatus() == OrderStatus.COMPLETED && newStatus != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Đơn hàng đã hoàn tất, không thể lùi trạng thái");
        }
        if (newStatus == OrderStatus.CANCELLED) {
            return cancelOrder(orderId, "Hủy trạng thái bởi " + actor, actor);
        }

        validateStateTransition(order, newStatus);

        OrderStatus fromStatus = order.getStatus();
        order.setStatus(newStatus);
        if (newStatus == OrderStatus.COMPLETED || newStatus == OrderStatus.PICKED_UP) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        Order savedOrder = orderRepository.save(order);

        // Record status history
        orderStatusHistoryRepository.save(OrderStatusHistory.builder()
                .order(savedOrder)
                .fromStatus(fromStatus)
                .toStatus(newStatus)
                .changedBy(actor)
                .note("Cập nhật trạng thái sang " + newStatus.getDescription())
                .build());

        log.info("Order {} status updated to {} by {}", order.getOrderCode(), newStatus, actor);
        return savedOrder;
    }

    private void validateStateTransition(Order order, OrderStatus next) {
        OrderStatus current = order.getStatus();
        if (current == next) return;

        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> (order.getFulfillmentType() == FulfillmentType.PICKUP)
                    ? (next == OrderStatus.READY_FOR_PICKUP || next == OrderStatus.CANCELLED)
                    : (next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED);
            case SHIPPED -> next == OrderStatus.COMPLETED || next == OrderStatus.CANCELLED;
            case READY_FOR_PICKUP -> next == OrderStatus.PICKED_UP || next == OrderStatus.CANCELLED;
            case PICKED_UP -> next == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };

        if (!valid) {
            throw new IllegalStateException("Không thể chuyển trạng thái từ '" + current.getDescription() + "' sang '" + next.getDescription() + "'");
        }
    }

    @Transactional
    public Order updatePaymentStatus(Long orderId, PaymentStatus newPaymentStatus, String actor) {
        Order order = getOrderById(orderId);
        order.setPaymentStatus(newPaymentStatus);
        return orderRepository.save(order);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + id));
    }

    public Order getOrderByCode(String orderCode) {
        return orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với mã: " + orderCode));
    }

    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Page<Order> filterOrders(Long branchId, OrderStatus status, String keyword, Pageable pageable) {
        return orderRepository.filterOrders(branchId, status, keyword, pageable);
    }

    public List<OrderStatusHistory> getOrderStatusHistory(Long orderId) {
        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }
}
