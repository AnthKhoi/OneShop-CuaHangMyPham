package com.oneshop;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartItemDto;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class CheckoutAndLogoutWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Verify GET /auth/logout behavior")
    void testGetLogout() throws Exception {
        mockMvc.perform(get("/auth/logout"))
                .andDo(print());
    }

    @Test
    @DisplayName("Verify POST /checkout behavior")
    void testPostCheckout() throws Exception {
        Branch branch = branchRepository.findAll().stream().findFirst().orElseThrow();
        Product product = productRepository.findAll().stream().findFirst().orElseThrow();

        MockHttpSession session = new MockHttpSession();
        CartDto cart = new CartDto();
        cart.getItems().add(CartItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .quantity(1)
                .build());
        session.setAttribute("ONESHOP_CART", cart);

        mockMvc.perform(post("/checkout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("customer@gmail.com").roles("CUSTOMER"))
                        .session(session)
                        .param("recipientName", "Khách Hàng Test")
                        .param("recipientPhone", "0912345678")
                        .param("recipientEmail", "test@gmail.com")
                        .param("branchId", branch.getId().toString())
                        .param("fulfillmentType", "DELIVERY")
                        .param("shippingAddress", "123 Đường Test, Quận 1")
                        .param("paymentMethod", "COD")
                        .param("note", "Ghi chú test"))
                .andDo(print());
    }

    @Test
    @DisplayName("Verify unauthenticated user cannot checkout and is redirected to login")
    void testUnauthenticatedCheckoutRedirect() throws Exception {
        mockMvc.perform(get("/checkout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?redirect=/checkout"));
    }

    @Test
    @DisplayName("Verify GET /admin for ADMIN and BRANCH_MANAGER")
    void testGetAdmin() throws Exception {
        mockMvc.perform(get("/admin")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@oneshop.vn").roles("ADMIN")))
                .andDo(print())
                .andExpect(status().isOk());

        mockMvc.perform(get("/decorators/admin-layout")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@oneshop.vn").roles("ADMIN")))
                .andDo(print())
                .andExpect(status().isOk());
    }
}

