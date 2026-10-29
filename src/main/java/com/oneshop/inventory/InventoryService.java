package com.oneshop.inventory;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.catalog.Product;
import com.oneshop.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final BranchInventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final StockTransferRepository transferRepository;
    private final StockBatchRepository stockBatchRepository;
    private final StockReceiptRepository stockReceiptRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final com.oneshop.catalog.ProductVariantRepository variantRepository;

    public BranchInventory getOrCreateInventory(Long branchId, Long productId) {
        return inventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .orElseGet(() -> {
                    Branch branch = branchRepository.findById(branchId)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh: " + branchId));
                    Product product = productRepository.findById(productId)
                            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm: " + productId));

                    BranchInventory inventory = BranchInventory.builder()
                            .branch(branch)
                            .product(product)
                            .quantityOnHand(0)
                            .quantityReserved(0)
                            .reorderLevel(10)
                            .build();
                    return inventoryRepository.save(inventory);
                });
    }

    public List<BranchInventory> getProductStockAcrossBranches(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    public List<BranchInventory> getInventoryList(Long branchId, String keyword) {
        List<BranchInventory> list;
        if (branchId != null) {
            list = inventoryRepository.findByBranchId(branchId);
        } else {
            list = inventoryRepository.findAll();
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            return list.stream()
                    .filter(bi -> bi.getProduct() != null && (
                            (bi.getProduct().getName() != null && bi.getProduct().getName().toLowerCase().contains(kw)) ||
                            (bi.getProduct().getSku() != null && bi.getProduct().getSku().toLowerCase().contains(kw))
                    ))
                    .toList();
        }
        return list;
    }

    public List<BranchInventory> getLowStockAlerts(Long branchId) {
        if (branchId != null) {
            return inventoryRepository.findLowStockByBranch(branchId);
        }
        return inventoryRepository.findAllLowStock();
    }

    public Page<StockMovement> getMovements(Long branchId, MovementType movementType, Pageable pageable) {
        return movementRepository.findMovements(branchId, movementType, pageable);
    }

    public int getAvailableQuantity(Long branchId, Long productId) {
        return inventoryRepository.findByBranchIdAndProductId(branchId, productId)
                .map(BranchInventory::getAvailableQuantity)
                .orElse(0);
    }

    @Transactional
    public BranchInventory adjustStock(Long branchId, Long productId, int newQuantityOnHand, String reason, String actor) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Lý do điều chỉnh tồn kho là bắt buộc");
        }
        if (newQuantityOnHand < 0) {
            throw new IllegalArgumentException("Số lượng tồn kho không được âm");
        }

        BranchInventory inventory = getOrCreateInventory(branchId, productId);
        int oldQuantity = inventory.getQuantityOnHand();
        int delta = newQuantityOnHand - oldQuantity;

        inventory.setQuantityOnHand(newQuantityOnHand);
        inventoryRepository.save(inventory);

        // Record stock movement audit
        StockMovement movement = StockMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .movementType(MovementType.ADJUSTMENT)
                .quantityDelta(delta)
                .quantityBefore(oldQuantity)
                .quantityAfter(newQuantityOnHand)
                .referenceType("MANUAL_ADJUSTMENT")
                .reason(reason)
                .performedBy(actor)
                .build();
        movementRepository.save(movement);

        log.info("Stock adjusted for Product ID {} at Branch ID {}: {} -> {} (reason: {}) by {}",
                productId, branchId, oldQuantity, newQuantityOnHand, reason, actor);

        return inventory;
    }

    @Transactional
    public BranchInventory importStock(Long branchId, Long productId, int quantity, String reason, String actor) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng nhập kho phải lớn hơn 0");
        }

        BranchInventory inventory = getOrCreateInventory(branchId, productId);
        int oldQuantity = inventory.getQuantityOnHand();
        int newQuantity = oldQuantity + quantity;

        inventory.setQuantityOnHand(newQuantity);
        inventoryRepository.save(inventory);

        StockMovement movement = StockMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .movementType(MovementType.IMPORT)
                .quantityDelta(quantity)
                .quantityBefore(oldQuantity)
                .quantityAfter(newQuantity)
                .referenceType("PURCHASE_RECEIPT")
                .reason(StringUtils.hasText(reason) ? reason : "Nhập hàng từ nhà cung cấp")
                .performedBy(actor)
                .build();
        movementRepository.save(movement);

        return inventory;
    }

    @Transactional
    public BranchInventory exportStock(Long branchId, Long productId, int quantity, String reason, String actor) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng xuất kho phải lớn hơn 0");
        }

        BranchInventory inventory = getOrCreateInventory(branchId, productId);
        int oldQuantity = inventory.getQuantityOnHand();
        if (oldQuantity < quantity) {
            throw new IllegalArgumentException("Không đủ tồn kho để xuất. Tồn hiện tại: " + oldQuantity + ", Yêu cầu: " + quantity);
        }

        int newQuantity = oldQuantity - quantity;
        inventory.setQuantityOnHand(newQuantity);
        inventoryRepository.save(inventory);

        StockMovement movement = StockMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .movementType(MovementType.EXPORT)
                .quantityDelta(-quantity)
                .quantityBefore(oldQuantity)
                .quantityAfter(newQuantity)
                .referenceType("INTERNAL_DISPOSAL")
                .reason(StringUtils.hasText(reason) ? reason : "Xuất hủy / hàng lỗi")
                .performedBy(actor)
                .build();
        movementRepository.save(movement);

        return inventory;
    }

    @Transactional
    public void deductStockForOrder(Long branchId, Long productId, int quantity, String orderCode, String actor) {
        if (quantity <= 0 || quantity > 10000) {
            throw new IllegalArgumentException("Số lượng xuất kho không hợp lệ (phải từ 1 đến 10,000)");
        }
        BranchInventory inventory = getOrCreateInventory(branchId, productId);
        int oldQuantity = inventory.getQuantityOnHand();

        if (inventory.getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Sản phẩm '" + inventory.getProduct().getName() +
                    "' tại chi nhánh " + inventory.getBranch().getName() + " không đủ tồn kho (Còn " +
                    inventory.getAvailableQuantity() + ", yêu cầu " + quantity + ")");
        }

        int newQuantity = oldQuantity - quantity;
        inventory.setQuantityOnHand(newQuantity);
        inventoryRepository.save(inventory);

        StockMovement movement = StockMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .movementType(MovementType.SALE)
                .quantityDelta(-quantity)
                .quantityBefore(oldQuantity)
                .quantityAfter(newQuantity)
                .referenceType("ORDER")
                .referenceId(orderCode)
                .reason("Bán lẻ theo đơn hàng #" + orderCode)
                .performedBy(actor)
                .build();
        movementRepository.save(movement);
    }

    @Transactional
    public void restoreStockForCancelledOrder(Long branchId, Long productId, int quantity, String orderCode, String actor) {
        if (quantity <= 0 || quantity > 10000) {
            throw new IllegalArgumentException("Số lượng hoàn kho không hợp lệ");
        }
        BranchInventory inventory = getOrCreateInventory(branchId, productId);
        int oldQuantity = inventory.getQuantityOnHand();
        int newQuantity = oldQuantity + quantity;

        inventory.setQuantityOnHand(newQuantity);
        inventoryRepository.save(inventory);

        // Also restore to StockBatch if batch tracking was deducted
        try {
            List<StockBatch> batches = stockBatchRepository.findByBranchIdAndProductIdOrderByExpiryDateAsc(branchId, productId);
            int toRestore = quantity;
            for (StockBatch b : batches) {
                if (toRestore <= 0) break;
                int deficit = b.getInitialQuantity() - b.getQuantityRemaining();
                if (deficit > 0) {
                    int add = Math.min(deficit, toRestore);
                    b.setQuantityRemaining(b.getQuantityRemaining() + add);
                    stockBatchRepository.save(b);
                    toRestore -= add;
                }
            }
        } catch (Exception ex) {
            log.warn("Could not restore stock batch for order {}: {}", orderCode, ex.getMessage());
        }

        StockMovement movement = StockMovement.builder()
                .branch(inventory.getBranch())
                .product(inventory.getProduct())
                .movementType(MovementType.RETURN)
                .quantityDelta(quantity)
                .quantityBefore(oldQuantity)
                .quantityAfter(newQuantity)
                .referenceType("ORDER_CANCEL")
                .referenceId(orderCode)
                .reason("Hoàn tồn do hủy đơn hàng #" + orderCode)
                .performedBy(actor)
                .build();
        movementRepository.save(movement);
    }

    public List<StockTransfer> getTransfers(Long branchId) {
        return transferRepository.findByBranch(branchId);
    }

    // ===================================================================
    // QUẢN LÝ LÔ HÀNG VÀ XUẤT KHO THEO NGUYÊN TẮC FEFO (FIRST-EXPIRED, FIRST-OUT)
    // ===================================================================

    @Transactional
    public StockReceipt createStockReceipt(Long branchId, String supplierName, String notes, List<StockReceiptItemDto> itemDtos, com.oneshop.user.User creator) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh: " + branchId));

        String code = "PN-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now()) + "-" + (int)(Math.random() * 9000 + 1000);

        java.math.BigDecimal totalCost = java.math.BigDecimal.ZERO;
        StockReceipt receipt = StockReceipt.builder()
                .receiptCode(code)
                .branch(branch)
                .supplierName(supplierName)
                .createdBy(creator)
                .totalCost(java.math.BigDecimal.ZERO)
                .notes(notes)
                .build();

        for (StockReceiptItemDto itemDto : itemDtos) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm: " + itemDto.getProductId()));

            com.oneshop.catalog.ProductVariant variant = null;
            if (itemDto.getVariantId() != null) {
                variant = variantRepository.findById(itemDto.getVariantId()).orElse(null);
            }

            java.math.BigDecimal lineTotal = itemDto.getCostPrice().multiply(java.math.BigDecimal.valueOf(itemDto.getQuantity()));
            totalCost = totalCost.add(lineTotal);

            StockReceiptItem item = StockReceiptItem.builder()
                    .product(product)
                    .variant(variant)
                    .batchCode(itemDto.getBatchCode())
                    .manufactureDate(itemDto.getManufactureDate())
                    .expiryDate(itemDto.getExpiryDate())
                    .quantity(itemDto.getQuantity())
                    .costPrice(itemDto.getCostPrice())
                    .lineTotal(lineTotal)
                    .build();
            receipt.addItem(item);

            // Create StockBatch for FEFO
            StockBatch batch = StockBatch.builder()
                    .batchCode(itemDto.getBatchCode())
                    .branch(branch)
                    .product(product)
                    .variant(variant)
                    .manufactureDate(itemDto.getManufactureDate())
                    .expiryDate(itemDto.getExpiryDate())
                    .initialQuantity(itemDto.getQuantity())
                    .quantityRemaining(itemDto.getQuantity())
                    .costPrice(itemDto.getCostPrice())
                    .build();
            stockBatchRepository.save(batch);

            // Update BranchInventory
            BranchInventory inventory = getOrCreateInventory(branchId, product.getId());
            inventory.setQuantityOnHand(inventory.getQuantityOnHand() + itemDto.getQuantity());
            inventoryRepository.save(inventory);

            // Record Movement
            StockMovement movement = StockMovement.builder()
                    .branch(branch)
                    .product(product)
                    .movementType(MovementType.IMPORT)
                    .quantityDelta(itemDto.getQuantity())
                    .quantityBefore(inventory.getQuantityOnHand() - itemDto.getQuantity())
                    .quantityAfter(inventory.getQuantityOnHand())
                    .referenceType("STOCK_RECEIPT")
                    .referenceId(code)
                    .reason("Nhập kho theo lô " + itemDto.getBatchCode() + " từ " + supplierName)
                    .performedBy(creator != null ? creator.getEmail() : "SYSTEM")
                    .build();
            movementRepository.save(movement);
        }

        receipt.setTotalCost(totalCost);
        return stockReceiptRepository.save(receipt);
    }

    @Transactional
    public List<StockBatch> allocateStockFEFO(Long branchId, Long productId, int quantityRequested) {
        List<StockBatch> availableBatches = stockBatchRepository.findAvailableBatchesFEFO(branchId, productId);
        if (availableBatches.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        int remainingToDeduct = quantityRequested;
        List<StockBatch> allocated = new java.util.ArrayList<>();

        for (StockBatch batch : availableBatches) {
            if (remainingToDeduct <= 0) break;
            int available = batch.getQuantityRemaining();
            if (available <= 0) continue;

            int deduct = Math.min(available, remainingToDeduct);
            batch.setQuantityRemaining(available - deduct);
            stockBatchRepository.save(batch);
            allocated.add(batch);

            remainingToDeduct -= deduct;
            log.info("FEFO Allocated: deducted {} from Batch {} (Expires: {})", deduct, batch.getBatchCode(), batch.getExpiryDate());
        }

        return allocated;
    }


    public List<StockBatch> getExpiringBatches(Long branchId, int daysThreshold) {
        java.time.LocalDate threshold = java.time.LocalDate.now().plusDays(daysThreshold);
        return stockBatchRepository.findExpiringBatches(branchId, threshold);
    }
}
