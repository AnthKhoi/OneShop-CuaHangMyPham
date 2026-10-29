package com.oneshop;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class OneShopPosWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    @WithMockUser(username = "sales.q1@oneshop.vn", roles = {"SALES_STAFF"})
    @DisplayName("Verify GET /admin/pos renders successfully for Sales Staff without Thymeleaf errors")
    void testPosTerminalRenders() throws Exception {
        mockMvc.perform(get("/admin/pos"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/pos/index"))
                .andExpect(model().attributeExists("posProducts", "categories"));
    }

    @Test
    @WithMockUser(username = "sales.q1@oneshop.vn", roles = {"SALES_STAFF"})
    @DisplayName("Verify POST /admin/pos/checkout successfully creates in-store order and redirects to receipt")
    void testPosCheckout() throws Exception {
        Branch branch = branchRepository.findAll().stream().findFirst().orElseThrow();
        Product product = productRepository.findAll().stream().findFirst().orElseThrow();

        mockMvc.perform(post("/admin/pos/checkout").with(csrf())
                        .param("branchId", branch.getId().toString())
                        .param("customerName", "Khách Mua Test POS")
                        .param("customerPhone", "0912345678")
                        .param("paymentMethod", "STORE_PAYMENT")
                        .param("amountGivenByCustomer", "1000000")
                        .param("items[0].productId", product.getId().toString())
                        .param("items[0].quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/pos/receipt/*"));
    }

    @Test
    @WithMockUser(username = "sales.q1@oneshop.vn", roles = {"SALES_STAFF"})
    @DisplayName("Verify customer lookup API returns JSON")
    void testCustomerLookup() throws Exception {
        mockMvc.perform(get("/admin/pos/customer-lookup").param("phone", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").exists());
    }
}
