package com.oneshop.cart;

import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.inventory.InventoryService;
import com.oneshop.promotion.Coupon;
import com.oneshop.promotion.CouponRepository;
import com.oneshop.security.SecurityUtils;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    public static final String SESSION_CART_KEY = "ONESHOP_CART";
    public static final int MAX_ITEM_QUANTITY = 99;

    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final InventoryService inventoryService;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public CartDto getCart(HttpSession session) {
        CartDto cart = (CartDto) session.getAttribute(SESSION_CART_KEY);
        Long currentUserId = SecurityUtils.getCurrentUserId();

        if (currentUserId != null) {
            final CartDto sessionCart = cart;
            List<CartItemEntity> dbItems = cartItemRepository.findByUserId(currentUserId);

            // If session had guest items before login, migrate them to DB
            if (sessionCart != null && !sessionCart.isEmpty() && dbItems.isEmpty()) {
                userRepository.findById(currentUserId).ifPresent(user -> {
                    for (CartItemDto item : sessionCart.getItems()) {
                        productRepository.findById(item.getProductId()).ifPresent(prod -> {
                            cartItemRepository.save(CartItemEntity.builder()
                                    .user(user)
                                    .product(prod)
                                    .quantity(item.getQuantity())
                                    .build());
                        });
                    }
                });
                dbItems = cartItemRepository.findByUserId(currentUserId);
            }

            // Sync CartDto from DB
            cart = new CartDto();
            for (CartItemEntity entity : dbItems) {
                Product p = entity.getProduct();
                if (Boolean.TRUE.equals(p.getActive())) {
                    cart.getItems().add(CartItemDto.builder()
                            .productId(p.getId())
                            .productName(p.getName())
                            .sku(p.getSku())
                            .slug(p.getSlug())
                            .imageUrl(p.getPrimaryImageUrl())
                            .price(p.getPrice())
                            .quantity(entity.getQuantity())
                            .build());
                }
            }
            session.setAttribute(SESSION_CART_KEY, cart);
            return cart;
        }

        if (cart == null) {
            cart = new CartDto();
            session.setAttribute(SESSION_CART_KEY, cart);
        }
        return cart;
    }

    @Transactional
    public void addToCart(HttpSession session, Long productId, int quantity, Long branchId) {
        if (quantity <= 0 || quantity > MAX_ITEM_QUANTITY) {
            throw new IllegalArgumentException("Số lượng sản phẩm phải từ 1 đến " + MAX_ITEM_QUANTITY);
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalStateException("Sản phẩm hiện đang tạm ngừng kinh doanh");
        }

        CartDto cart = getCart(session);
        Optional<CartItemDto> existingItem = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        int targetQuantity = quantity;
        if (existingItem.isPresent()) {
            try {
                targetQuantity = Math.addExact(existingItem.get().getQuantity(), quantity);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Số lượng sản phẩm vượt quá giới hạn cho phép");
            }
        }

        if (targetQuantity > MAX_ITEM_QUANTITY) {
            throw new IllegalArgumentException("Mỗi mặt hàng trong giỏ chỉ được mua tối đa " + MAX_ITEM_QUANTITY + " sản phẩm");
        }

        if (branchId != null) {
            int available = inventoryService.getAvailableQuantity(branchId, productId);
            if (available < targetQuantity) {
                throw new IllegalStateException("Chi nhánh không đủ số lượng tồn (Hiện còn: " + available + ")");
            }
        }

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(targetQuantity);
            existingItem.get().setPrice(product.getPrice());
        } else {
            CartItemDto newItem = CartItemDto.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .sku(product.getSku())
                    .slug(product.getSlug())
                    .imageUrl(product.getPrimaryImageUrl())
                    .price(product.getPrice())
                    .quantity(quantity)
                    .build();
            cart.getItems().add(newItem);
        }

        // Persist to DB if logged in
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            final int finalQty = targetQuantity;
            userRepository.findById(currentUserId).ifPresent(user -> {
                CartItemEntity entity = cartItemRepository.findByUserIdAndProductId(currentUserId, productId)
                        .orElse(CartItemEntity.builder().user(user).product(product).quantity(0).build());
                entity.setQuantity(finalQty);
                cartItemRepository.save(entity);
            });
        }

        recalculateCoupon(cart);
        session.setAttribute(SESSION_CART_KEY, cart);
    }

    @Transactional
    public void updateQuantity(HttpSession session, Long productId, int quantity, Long branchId) {
        if (quantity <= 0) {
            removeFromCart(session, productId);
            return;
        }

        if (quantity > MAX_ITEM_QUANTITY) {
            throw new IllegalArgumentException("Số lượng không được vượt quá " + MAX_ITEM_QUANTITY);
        }

        if (branchId != null) {
            int available = inventoryService.getAvailableQuantity(branchId, productId);
            if (available < quantity) {
                throw new IllegalStateException("Chi nhánh không đủ tồn kho (Còn: " + available + ")");
            }
        }

        CartDto cart = getCart(session);
        cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .ifPresent(item -> {
                    productRepository.findById(productId).ifPresent(p -> item.setPrice(p.getPrice()));
                    item.setQuantity(quantity);
                });

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            cartItemRepository.findByUserIdAndProductId(currentUserId, productId)
                    .ifPresent(entity -> {
                        entity.setQuantity(quantity);
                        cartItemRepository.save(entity);
                    });
        }

        recalculateCoupon(cart);
        session.setAttribute(SESSION_CART_KEY, cart);
    }

    @Transactional
    public void removeFromCart(HttpSession session, Long productId) {
        CartDto cart = getCart(session);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            cartItemRepository.deleteByUserIdAndProductId(currentUserId, productId);
        }

        recalculateCoupon(cart);
        session.setAttribute(SESSION_CART_KEY, cart);
    }

    public void applyCoupon(HttpSession session, String couponCode) {
        CartDto cart = getCart(session);
        if (cart.isEmpty()) {
            throw new IllegalArgumentException("Giỏ hàng đang trống, không thể áp dụng mã");
        }

        Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Mã giảm giá '" + couponCode + "' không tồn tại"));

        if (!coupon.isValid(cart.getSubtotal())) {
            throw new IllegalArgumentException("Mã giảm giá không hợp lệ hoặc đơn hàng chưa đạt giá trị tối thiểu");
        }

        cart.setCouponCode(coupon.getCode());
        cart.setDiscountAmount(coupon.calculateDiscount(cart.getSubtotal()));
        session.setAttribute(SESSION_CART_KEY, cart);
    }

    public void removeCoupon(HttpSession session) {
        CartDto cart = getCart(session);
        cart.setCouponCode(null);
        cart.setDiscountAmount(BigDecimal.ZERO);
        session.setAttribute(SESSION_CART_KEY, cart);
    }

    @Transactional
    public void clearCart(HttpSession session) {
        session.removeAttribute(SESSION_CART_KEY);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId != null) {
            cartItemRepository.deleteByUserId(currentUserId);
        }
    }

    private void recalculateCoupon(CartDto cart) {
        if (cart.getCouponCode() != null) {
            couponRepository.findByCodeIgnoreCase(cart.getCouponCode())
                    .ifPresentOrElse(coupon -> {
                        if (coupon.isValid(cart.getSubtotal())) {
                            cart.setDiscountAmount(coupon.calculateDiscount(cart.getSubtotal()));
                        } else {
                            cart.setCouponCode(null);
                            cart.setDiscountAmount(BigDecimal.ZERO);
                        }
                    }, () -> {
                        cart.setCouponCode(null);
                        cart.setDiscountAmount(BigDecimal.ZERO);
                    });
        }
    }
}
