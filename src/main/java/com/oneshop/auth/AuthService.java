package com.oneshop.auth;

import com.oneshop.security.JwtTokenProvider;
import com.oneshop.security.UserPrincipal;
import com.oneshop.user.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final OtpService otpService;

    public UserPrincipal login(String email, String password, HttpServletResponse response) {
        String cleanEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không chính xác"));

        if (Boolean.FALSE.equals(user.getActive())) {
            throw new DisabledException("Tài khoản chưa được kích hoạt qua mã OTP. Vui lòng xác thực email.");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(cleanEmail, password)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateToken(authentication);
        setJwtCookie(response, jwt, (int) (tokenProvider.getExpirationMs() / 1000));

        return (UserPrincipal) authentication.getPrincipal();
    }

    @Transactional
    public void initiateRegister(RegisterRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String cleanPhone = request.getPhone() != null ? request.getPhone().trim() : "";

        if (!cleanPhone.isBlank()) {
            Optional<User> userWithPhone = userRepository.findFirstByPhone(cleanPhone);
            if (userWithPhone.isPresent() && !userWithPhone.get().getEmail().equalsIgnoreCase(cleanEmail) && Boolean.TRUE.equals(userWithPhone.get().getActive())) {
                throw new IllegalArgumentException("Số điện thoại này đã được sử dụng cho một tài khoản khác.");
            }
        }

        Optional<User> existingUserOpt = userRepository.findByEmail(cleanEmail);

        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            if (Boolean.TRUE.equals(existing.getActive())) {
                throw new IllegalArgumentException("Email này đã được sử dụng bởi một tài khoản khác.");
            }
            // If user registered before but hasn't activated OTP, update info
            existing.setFullName(request.getFullName().trim());
            existing.setPhone(cleanPhone);
            existing.setAddress(request.getAddress());
            existing.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(existing);
        } else {
            Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                    .orElseThrow(() -> new IllegalStateException("Quyền ROLE_CUSTOMER chưa được cấu hình"));

            Set<Role> roles = new HashSet<>();
            roles.add(customerRole);

            User newUser = User.builder()
                    .email(cleanEmail)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .fullName(request.getFullName().trim())
                    .phone(request.getPhone())
                    .address(request.getAddress())
                    .roles(roles)
                    .active(false) // Inactive until OTP verified
                    .build();

            userRepository.save(newUser);
        }

        // Send registration OTP
        otpService.sendOtp(cleanEmail, OtpType.REGISTRATION);
    }

    @Transactional
    public UserPrincipal completeRegister(String email, String otpCode, HttpServletResponse response) {
        String cleanEmail = email.trim().toLowerCase();
        otpService.verifyOtp(cleanEmail, otpCode, OtpType.REGISTRATION);

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông tin tài khoản"));

        user.setActive(true);
        userRepository.save(user);

        // Auto login customer
        UserPrincipal principal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateToken(authentication);
        setJwtCookie(response, jwt, (int) (tokenProvider.getExpirationMs() / 1000));

        return principal;
    }

    @org.springframework.beans.factory.annotation.Value("${oneshop.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    public void resendOtp(String email, OtpType type) {
        otpService.sendOtp(email.trim().toLowerCase(), type);
    }

    public void initiateForgotPassword(String email) {
        if (!org.springframework.util.StringUtils.hasText(email)) {
            return;
        }
        String cleanEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
        if (userOpt.isEmpty() || Boolean.FALSE.equals(userOpt.get().getActive())) {
            log.info("Password reset requested for non-existent or inactive email: {}", cleanEmail);
            return; // Neutral response to prevent email enumeration
        }

        otpService.sendOtp(cleanEmail, OtpType.PASSWORD_RESET);
    }

    public String verifyResetOtp(String email, String otpCode) {
        return otpService.verifyPasswordResetOtp(email.trim().toLowerCase(), otpCode);
    }

    @Transactional
    public void resetPassword(String email, String resetToken, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("Mật khẩu phải có tối thiểu 6 ký tự.");
        }

        String cleanEmail = email.trim().toLowerCase();
        otpService.validateAndConsumeResetToken(cleanEmail, resetToken);

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản để đổi mật khẩu."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void logout(HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        setJwtCookie(response, "", 0);
    }

    private void setJwtCookie(HttpServletResponse response, String token, int maxAge) {
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie
                .from(tokenProvider.getCookieName(), token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
