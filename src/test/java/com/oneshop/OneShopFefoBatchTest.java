package com.oneshop;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.catalog.Brand;
import com.oneshop.catalog.BrandRepository;
import com.oneshop.catalog.Category;
import com.oneshop.catalog.CategoryRepository;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.inventory.InventoryService;
import com.oneshop.inventory.StockBatch;
import com.oneshop.inventory.StockBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DisplayName("Unit Tests — Quản lý kho theo nguyên tắc FEFO (First-Expired, First-Out)")
class OneShopFefoBatchTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockBatchRepository batchRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Test
    @Transactional
    @DisplayName("TC-12: Xuất kho ưu tiên lô có hạn sử dụng gần nhất trước (FEFO)")
    void testFefoAllocationOrder() {
        Branch branch = branchRepository.findAll().get(0);
        Category category = categoryRepository.findAll().get(0);
        Brand brand = brandRepository.findAll().get(0);

        Product testProduct = productRepository.save(Product.builder()
                .sku("FEFO-TEST-001")
                .name("Sản Phẩm Kiểm Thử FEFO")
                .slug("san-pham-kiem-thu-fefo")
                .price(BigDecimal.valueOf(100000))
                .category(category)
                .brand(brand)
                .active(true)
                .build());

        // Lô 1: Hết hạn sau 30 ngày (gần hơn) -> Số lượng 10
        StockBatch batch1 = batchRepository.save(StockBatch.builder()
                .batchCode("LOT-EXP-30D")
                .branch(branch)
                .product(testProduct)
                .manufactureDate(LocalDate.now().minusMonths(2))
                .expiryDate(LocalDate.now().plusDays(30))
                .initialQuantity(10)
                .quantityRemaining(10)
                .costPrice(BigDecimal.valueOf(60000))
                .build());

        // Lô 2: Hết hạn sau 90 ngày (xa hơn) -> Số lượng 20
        StockBatch batch2 = batchRepository.save(StockBatch.builder()
                .batchCode("LOT-EXP-90D")
                .branch(branch)
                .product(testProduct)
                .manufactureDate(LocalDate.now().minusMonths(1))
                .expiryDate(LocalDate.now().plusDays(90))
                .initialQuantity(20)
                .quantityRemaining(20)
                .costPrice(BigDecimal.valueOf(60000))
                .build());

        // Yêu cầu xuất kho 15 sản phẩm
        List<StockBatch> allocated = inventoryService.allocateStockFEFO(branch.getId(), testProduct.getId(), 15);

        assertEquals(2, allocated.size(), "Phải phân bổ qua cả 2 lô");

        // Lô 1 (cận date hơn) phải được xuất hết trước
        StockBatch updatedBatch1 = batchRepository.findById(batch1.getId()).orElseThrow();
        assertEquals(0, updatedBatch1.getQuantityRemaining(), "Lô 1 cận date hơn phải xuất hết (còn 0)");

        // Lô 2 phải xuất phần còn lại (15 - 10 = 5) -> còn lại 20 - 5 = 15
        StockBatch updatedBatch2 = batchRepository.findById(batch2.getId()).orElseThrow();
        assertEquals(15, updatedBatch2.getQuantityRemaining(), "Lô 2 phải trừ 5, còn lại 15");
    }
}
