package com.oneshop;

import com.oneshop.branch.Branch;
import com.oneshop.security.JwtTokenProvider;
import com.oneshop.security.UserPrincipal;
import com.oneshop.user.Role;
import com.oneshop.user.RoleName;
import com.oneshop.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Tests — Security, Roles & JWT Authentication")
class OneShopSecurityAndAccessTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        String testSecret = "c2VjdXJlLW9uZXNob3AtY29zbWV0aWNzLXByZW1pdW0tc2VjcmV0LWtleS0yMDI2LXN0cm9uZw==";
        jwtTokenProvider = new JwtTokenProvider(testSecret, 3600000, "ONESHOP_JWT");
    }

    @Test
    @DisplayName("TC-09: Khởi tạo và kiểm tra quyền của UserPrincipal (Admin vs Branch Manager)")
    void testUserPrincipalRoles() {
        Role adminRole = Role.builder().name(RoleName.ROLE_ADMIN).build();
        User adminUser = User.builder()
                .id(1L)
                .email("admin@oneshop.vn")
                .fullName("Quản Trị")
                .roles(Set.of(adminRole))
                .active(true)
                .build();

        UserPrincipal adminPrincipal = UserPrincipal.create(adminUser);
        assertTrue(adminPrincipal.isAdmin());
        assertTrue(adminPrincipal.hasRole("ROLE_ADMIN"));
        assertNull(adminPrincipal.getBranchId());

        // Branch Manager assigned to Branch Q1
        Branch branchQ1 = Branch.builder().id(5L).name("OneShop Q1").build();
        Role managerRole = Role.builder().name(RoleName.ROLE_BRANCH_MANAGER).build();
        User managerUser = User.builder()
                .id(2L)
                .email("manager@oneshop.vn")
                .fullName("Cửa Hàng Trưởng")
                .branch(branchQ1)
                .roles(Set.of(managerRole))
                .active(true)
                .build();

        UserPrincipal managerPrincipal = UserPrincipal.create(managerUser);
        assertFalse(managerPrincipal.isAdmin());
        assertTrue(managerPrincipal.isBranchManager());
        assertEquals(5L, managerPrincipal.getBranchId());
        assertEquals("OneShop Q1", managerPrincipal.getBranchName());
    }

    @Test
    @DisplayName("TC-10: Sinh token JWT hợp lệ và giải mã chính xác username / userId")
    void testJwtTokenGenerationAndValidation() {
        String email = "customer@gmail.com";
        Long userId = 99L;

        String token = jwtTokenProvider.generateTokenFromUsername(email, userId);
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Validate token
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(email, jwtTokenProvider.getUsernameFromJwt(token));

        // Invalid token returns false
        assertFalse(jwtTokenProvider.validateToken("invalid.jwt.token"));
    }
}
