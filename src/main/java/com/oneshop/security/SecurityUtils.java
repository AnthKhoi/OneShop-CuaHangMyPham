package com.oneshop.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return Optional.of((UserPrincipal) authentication.getPrincipal());
        }
        return Optional.empty();
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().map(UserPrincipal::getId).orElse(null);
    }

    public static Optional<String> getCurrentUsername() {
        return getCurrentUser().map(UserPrincipal::getUsername);
    }

    public static Long getCurrentUserBranchId() {
        return getCurrentUser().map(UserPrincipal::getBranchId).orElse(null);
    }

    public static boolean isCurrentUserAdmin() {
        return getCurrentUser().map(UserPrincipal::isAdmin).orElse(false);
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String && "anonymousUser".equals(authentication.getPrincipal()));
    }

    public static Long enforceBranchAccess(Long requestedBranchId) {
        UserPrincipal principal = getCurrentUser().orElse(null);
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("Yêu cầu đăng nhập để truy cập tài nguyên chi nhánh");
        }
        if (principal.isAdmin() || principal.hasRole("ROLE_OWNER")) {
            return requestedBranchId;
        }
        if (principal.getBranchId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Tài khoản chưa được gán chi nhánh hoạt động. Vui lòng liên hệ Quản trị viên.");
        }
        if (requestedBranchId != null && !requestedBranchId.equals(principal.getBranchId())) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền thao tác trên chi nhánh khác.");
        }
        return principal.getBranchId();
    }
}
