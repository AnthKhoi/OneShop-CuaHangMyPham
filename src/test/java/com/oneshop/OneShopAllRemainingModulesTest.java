package com.oneshop;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.branch.BranchService;
import com.oneshop.branch.BranchStockDto;
import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartItemDto;
import com.oneshop.catalog.Brand;
import com.oneshop.catalog.BrandRepository;
import com.oneshop.catalog.Category;
import com.oneshop.catalog.CategoryRepository;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.dashboard.DashboardService;
import com.oneshop.dashboard.DashboardStatsDto;
import com.oneshop.inventory.BranchInventory;
import com.oneshop.inventory.BranchInventoryRepository;
import com.oneshop.order.*;
import com.oneshop.promotion.Coupon;
import com.oneshop.promotion.CouponDiscountType;
import com.oneshop.promotion.CouponRepository;
import com.oneshop.promotion.CouponUsageRepository;
import com.oneshop.storage.CloudinaryService;
import com.oneshop.storage.MediaAsset;
import com.oneshop.storage.MediaAssetRepository;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class OneShopAllRemainingModulesTest {

    @Autowired
    private BranchService branchService;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private CouponUsageRepository couponUsageRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private BranchInventoryRepository branchInventoryRepository;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private CloudinaryService cloudinaryService;
    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Test
    @DisplayName("Module 3: Multi-Branch management, status toggling, and cross-branch stock inquiry")
    @Transactional
    void testModule3BranchManagement() {
        Branch branch = branchRepository.findAll().stream().findFirst().orElseThrow();
        boolean initialActive = Boolean.TRUE.equals(branch.getActive());

        // Toggle status
        Branch toggled = branchService.toggleBranchStatus(branch.getId());
        assertEquals(!initialActive, toggled.getActive());

        // Cross-branch stock query
        Product product = productRepository.findAll().stream().findFirst().orElseThrow();
        List<BranchStockDto> stockAcrossBranches = branchService.checkProductStockAcrossBranches(product.getId());
        assertNotNull(stockAcrossBranches);
        assertFalse(stockAcrossBranches.isEmpty());
    }

    @Test
    @DisplayName("Module 5 & 6: Order Lifecycle, OrderStatusHistory, Payment & VietQR, CouponUsage")
    @Transactional
    void testModule5And6OrderPaymentPromotion() {
        Branch branch = branchRepository.findAll().stream().findFirst().orElseThrow();
        Product product = productRepository.findAll().stream().findFirst().orElseThrow();

        // Ensure stock at branch
        BranchInventory inventory = branchInventoryRepository.findByBranchIdAndProductId(branch.getId(), product.getId())
                .orElseGet(() -> BranchInventory.builder().branch(branch).product(product).quantityOnHand(50).quantityReserved(0).build());
        inventory.setQuantityOnHand(50);
        branchInventoryRepository.save(inventory);

        // Create coupon
        Coupon coupon = Coupon.builder()
                .code("TESTCOUPON10")
                .discountType(CouponDiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(10))
                .minOrderAmount(BigDecimal.valueOf(50000))
                .active(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(10))
                .build();
        couponRepository.save(coupon);

        CartDto cart = new CartDto();
        CartItemDto item = new CartItemDto();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setPrice(product.getPrice());
        item.setQuantity(2);
        cart.getItems().add(item);
        cart.setCouponCode("TESTCOUPON10");

        CheckoutForm form = new CheckoutForm();
        form.setBranchId(branch.getId());
        form.setFulfillmentType(FulfillmentType.DELIVERY);
        form.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        form.setRecipientName("Khách Hàng Test");
        form.setRecipientPhone("0988776655");
        form.setRecipientEmail("khachhang@test.com");
        form.setShippingAddress("123 Phố Huế, Hà Nội");
        form.setNote("Đóng gói cẩn thận");

        // 1. Create order
        Order order = orderService.createOrder(cart, form, null);
        assertNotNull(order);
        assertNotNull(order.getOrderCode());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertTrue(order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0);

        // 2. Verify OrderStatusHistory audit trail
        List<OrderStatusHistory> histories = orderService.getOrderStatusHistory(order.getId());
        assertFalse(histories.isEmpty());
        assertEquals(OrderStatus.PENDING, histories.get(0).getToStatus());

        // 3. Verify Payment Record & VietQR URL
        List<Payment> payments = paymentService.getPaymentsByOrder(order.getId());
        assertFalse(payments.isEmpty());
        assertEquals(PaymentMethod.BANK_TRANSFER, payments.get(0).getPaymentMethod());

        String vietQrUrl = paymentService.generateVietQrUrl(order);
        assertNotNull(vietQrUrl);
        assertTrue(vietQrUrl.contains("img.vietqr.io"));

        // 4. Verify CouponUsage
        assertEquals(1, couponUsageRepository.findByOrderId(order.getId()).size());

        // 5. Update Order Status to CONFIRMED
        Order confirmed = orderService.updateOrderStatus(order.getId(), OrderStatus.CONFIRMED, "Admin");
        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(2, orderService.getOrderStatusHistory(order.getId()).size());
    }

    @Test
    @DisplayName("Module 8: Dashboard stats, top-selling products & branch revenues")
    @Transactional
    void testModule8DashboardStats() {
        DashboardStatsDto stats = dashboardService.getDashboardStats(null);
        assertNotNull(stats);
        assertNotNull(stats.getTopSellingProducts());
        assertNotNull(stats.getBranchRevenues());
    }

    @Test
    @DisplayName("Module 9: Cloudinary Service upload fallback and MediaAsset tracking")
    @Transactional
    void testModule9MediaManagement() throws IOException {
        byte[] pngBytes = new byte[] {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-serum.png",
                "image/png",
                pngBytes
        );

        CloudinaryService.UploadResult result = cloudinaryService.uploadImage(file, "products", "TestUser");
        assertNotNull(result);
        assertNotNull(result.secureUrl());
        assertNotNull(result.publicId());

        // Verify MediaAsset saved in database
        List<MediaAsset> assets = cloudinaryService.getAllMediaAssets();
        assertFalse(assets.isEmpty());
        assertTrue(assets.stream().anyMatch(a -> a.getFileName().equals("test-serum.png")));

        // Delete test
        cloudinaryService.deleteImage(result.publicId());
    }
}
