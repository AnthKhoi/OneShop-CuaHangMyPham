package com.oneshop.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    // Default Bank config for VietQR
    private static final String DEFAULT_BANK_CODE = "MB";
    private static final String DEFAULT_ACCOUNT_NO = "0349846114";
    private static final String DEFAULT_ACCOUNT_NAME = "LE HUYNH ANH KHOI";

    @Transactional
    public Payment createPayment(Order order, PaymentMethod method, BigDecimal amount) {
        PaymentStatus initialStatus;
        if (method == PaymentMethod.COD) {
            initialStatus = PaymentStatus.UNPAID;
        } else {
            initialStatus = PaymentStatus.PENDING;
        }

        Payment payment = Payment.builder()
                .order(order)
                .amount(amount != null ? amount : order.getTotalAmount())
                .paymentMethod(method)
                .paymentStatus(initialStatus)
                .transactionCode(order.getOrderCode())
                .payDate(null)
                .note("Khởi tạo thanh toán cho đơn hàng #" + order.getOrderCode())
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment confirmPayment(Long orderId, String transactionCode, String actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + orderId));

        Payment payment = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseGet(() -> createPayment(order, order.getPaymentMethod(), order.getTotalAmount()));

        payment.setPaymentStatus(PaymentStatus.PAID);
        payment.setPayDate(LocalDateTime.now());
        if (transactionCode != null && !transactionCode.isBlank()) {
            payment.setTransactionCode(transactionCode);
        }
        payment.setNote("Xác nhận thanh toán thành công bởi " + actor);
        Payment savedPayment = paymentRepository.save(payment);

        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        log.info("Payment confirmed for Order {} with amount {} by {}", order.getOrderCode(), payment.getAmount(), actor);
        return savedPayment;
    }

    public List<Payment> getPaymentsByOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    public Optional<Payment> getLatestPayment(Long orderId) {
        return paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);
    }

    public String generateVietQrUrl(Order order) {
        if (order == null || order.getTotalAmount() == null) {
            return "";
        }
        long amount = order.getTotalAmount().longValue();
        String description = order.getOrderCode();
        String encodedAccountName = URLEncoder.encode(DEFAULT_ACCOUNT_NAME, StandardCharsets.UTF_8);
        String encodedDesc = URLEncoder.encode(description, StandardCharsets.UTF_8);

        return String.format("https://img.vietqr.io/image/%s-%s-compact2.png?amount=%d&addInfo=%s&accountName=%s",
                DEFAULT_BANK_CODE, DEFAULT_ACCOUNT_NO, amount, encodedDesc, encodedAccountName);
    }

    public String getBankInfo() {
        return "Ngân hàng Quân Đội (MB Bank) - STK: " + DEFAULT_ACCOUNT_NO + " - Chủ TK: " + DEFAULT_ACCOUNT_NAME;
    }
}
