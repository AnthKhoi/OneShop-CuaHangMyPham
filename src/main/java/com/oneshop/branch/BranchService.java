package com.oneshop.branch;

import com.oneshop.inventory.BranchInventory;
import com.oneshop.inventory.BranchInventoryRepository;
import com.oneshop.user.User;
import com.oneshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchInventoryRepository branchInventoryRepository;

    public List<Branch> getAllBranches() {
        return branchRepository.findAll();
    }

    public List<Branch> getActiveBranches() {
        return branchRepository.findByActiveTrue();
    }

    public Branch getBranchById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh với ID: " + id));
    }

    public Branch getBranchByCode(String code) {
        return branchRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chi nhánh với mã: " + code));
    }

    @Transactional
    public Branch saveBranch(Branch branch) {
        if (branch.getId() == null && branchRepository.existsByCode(branch.getCode())) {
            throw new IllegalArgumentException("Mã chi nhánh " + branch.getCode() + " đã tồn tại");
        }
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch toggleBranchStatus(Long branchId) {
        Branch branch = getBranchById(branchId);
        branch.setActive(!Boolean.TRUE.equals(branch.getActive()));
        return branchRepository.save(branch);
    }

    public List<User> getBranchStaff(Long branchId) {
        return userRepository.findByBranchId(branchId);
    }

    public List<User> getUnassignedStaff() {
        return userRepository.findByBranchIsNull();
    }

    @Transactional
    public User assignStaffToBranch(Long userId, Long branchId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng ID: " + userId));
        Branch branch = getBranchById(branchId);
        user.setBranch(branch);
        return userRepository.save(user);
    }

    @Transactional
    public User removeStaffFromBranch(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng ID: " + userId));
        user.setBranch(null);
        return userRepository.save(user);
    }

    public List<BranchStockDto> checkProductStockAcrossBranches(Long productId) {
        List<Branch> activeBranches = branchRepository.findByActiveTrue();
        List<BranchInventory> inventories = branchInventoryRepository.findByProductId(productId);

        Map<Long, BranchInventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(bi -> bi.getBranch().getId(), bi -> bi, (a, b) -> a));

        List<BranchStockDto> results = new ArrayList<>();
        for (Branch b : activeBranches) {
            BranchInventory bi = inventoryMap.get(b.getId());
            int onHand = bi != null ? bi.getQuantityOnHand() : 0;
            int available = bi != null ? bi.getAvailableQuantity() : 0;
            results.add(BranchStockDto.builder()
                    .branchId(b.getId())
                    .branchCode(b.getCode())
                    .branchName(b.getName())
                    .branchAddress(b.getAddress())
                    .branchPhone(b.getPhone())
                    .quantityOnHand(onHand)
                    .availableQuantity(available)
                    .inStock(available > 0)
                    .build());
        }
        return results;
    }
}
