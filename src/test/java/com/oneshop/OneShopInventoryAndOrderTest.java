package com.oneshop;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.cart.CartDto;
import com.oneshop.cart.CartItemDto;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import com.oneshop.inventory.BranchInventory;
import com.oneshop.inventory.BranchInventoryRepository;
import com.oneshop.inventory.InventoryService;
import com.oneshop.inventory.StockMovementRepository;
import com.oneshop.inventory.StockBatchRepository;
import com.oneshop.order.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit & Service Tests — Branch Inventory & Order Lifecycle")
class OneShopInventoryAndOrderTest {

    @Mock
    private BranchInventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository movementRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StockBatchRepository stockBatchRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    @DisplayName("TC-05: Khấu trừ tồn kho thành công và ghi nhận StockMovement khi đặt hàng")
    void testDeductStockSuccess() {
        Branch branch = Branch.builder().id(1L).name("OneShop Q1").build();
        Product product = Product.builder().id(10L).name("Kem Dưỡng").sku("KHL-001").price(BigDecimal.valueOf(500000)).build();

        BranchInventory inventory = BranchInventory.builder()
                .id(100L)
                .branch(branch)
                .product(product)
                .quantityOnHand(20)
                .quantityReserved(0)
                .reorderLevel(5)
                .build();

        when(inventoryRepository.findByBranchIdAndProductId(1L, 10L)).thenReturn(Optional.of(inventory));

        // Deduct 3 items
        inventoryService.deductStockForOrder(1L, 10L, 3, "OS-20261026-0001", "Khách hàng");

        // Quantity should decrease to 17
        assertEquals(17, inventory.getQuantityOnHand());
        verify(inventoryRepository, times(1)).save(inventory);
        verify(movementRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("TC-06: Chặn đặt hàng khi tồn kho chi nhánh không đủ (Ngăn ngừa tồn âm)")
    void testDeductStockInsufficientThrowsException() {
        Branch branch = Branch.builder().id(1L).name("OneShop Q1").build();
        Product product = Product.builder().id(10L).name("Kem Dưỡng").build();

        BranchInventory inventory = BranchInventory.builder()
                .id(100L)
                .branch(branch)
                .product(product)
                .quantityOnHand(2) // Only 2 in stock
                .quantityReserved(0)
                .build();

        when(inventoryRepository.findByBranchIdAndProductId(1L, 10L)).thenReturn(Optional.of(inventory));

        // Attempting to buy 5 items must fail
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            inventoryService.deductStockForOrder(1L, 10L, 5, "OS-20261026-0002", "Khách hàng");
        });

        assertTrue(exception.getMessage().contains("không đủ tồn kho"));
        // Quantity must remain unchanged at 2
        assertEquals(2, inventory.getQuantityOnHand());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-07: Điều chỉnh tồn kho bắt buộc phải có lý do (Reason mandatory)")
    void testAdjustStockMandatoryReason() {
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.adjustStock(1L, 10L, 15, "", "Admin");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.adjustStock(1L, 10L, -5, "Có lý do", "Admin");
        });
    }

    @Test
    @DisplayName("TC-08: Hủy đơn hàng hoàn trả đúng số lượng tồn kho và ghi nhật ký hoàn hàng")
    void testRestoreStockForCancelledOrder() {
        Branch branch = Branch.builder().id(1L).name("OneShop Q1").build();
        Product product = Product.builder().id(10L).name("Kem Dưỡng").build();

        BranchInventory inventory = BranchInventory.builder()
                .id(100L)
                .branch(branch)
                .product(product)
                .quantityOnHand(10)
                .build();

        when(inventoryRepository.findByBranchIdAndProductId(1L, 10L)).thenReturn(Optional.of(inventory));

        // Restore 2 items
        inventoryService.restoreStockForCancelledOrder(1L, 10L, 2, "OS-20261026-0001", "Admin");

        // Stock goes from 10 to 12
        assertEquals(12, inventory.getQuantityOnHand());
        verify(inventoryRepository, times(1)).save(inventory);
        verify(movementRepository, times(1)).save(any());
    }
}
