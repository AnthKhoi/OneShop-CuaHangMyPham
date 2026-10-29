package com.oneshop.auth;

import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        // Clear JWT Cookie
        Cookie cookie = new Cookie("ONESHOP_JWT", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        // Invalidate session if present
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        // Clear Spring Security Context
        SecurityContextHolder.clearContext();

        redirectAttributes.addFlashAttribute("successMessage", "Bạn đã đăng xuất an toàn.");
        return "redirect:/auth/login?logout=true";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "redirect", required = false) String redirect,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "resetSuccess", required = false) String resetSuccess,
            Model model) {

        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/";
        }

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setRedirect(redirect);
        model.addAttribute("loginRequest", loginRequest);

        if (logout != null) {
            model.addAttribute("successMessage", "Đăng xuất tài khoản thành công!");
        }
        if (resetSuccess != null) {
            model.addAttribute("successMessage", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập với mật khẩu mới.");
        }

        return "store/auth/login";
    }

    @PostMapping("/login")
    public String handleLogin(
            @Valid @ModelAttribute("loginRequest") LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            return "store/auth/login";
        }

        try {
            UserPrincipal principal = authService.login(loginRequest.getEmail(), loginRequest.getPassword(), response);
            redirectAttributes.addFlashAttribute("successMessage", "Chào mừng " + principal.getFullName() + " đã đăng nhập thành công!");

            if (isSafeRedirect(loginRequest.getRedirect())) {
                return "redirect:" + loginRequest.getRedirect().trim();
            }

            // Redirect employee to admin panel
            if (principal.hasRole("ROLE_SALES_STAFF")) {
                return "redirect:/admin/orders";
            }
            if (principal.isAdmin() || principal.isBranchManager()) {
                return "redirect:/admin";
            }

            return "redirect:/";
        } catch (DisabledException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("unverifiedEmail", loginRequest.getEmail());
            return "store/auth/login";
        } catch (BadCredentialsException ex) {
            model.addAttribute("errorMessage", "Email hoặc mật khẩu không chính xác");
            return "store/auth/login";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Đăng nhập thất bại: " + ex.getMessage());
            return "store/auth/login";
        }
    }

    private boolean isSafeRedirect(String redirect) {
        if (!StringUtils.hasText(redirect)) return false;
        String trimmed = redirect.trim();
        return trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.contains("\\") && !trimmed.contains("/auth/");
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (SecurityUtils.isAuthenticated()) {
            return "redirect:/";
        }
        model.addAttribute("registerRequest", new RegisterRequest());
        return "store/auth/register";
    }

    @PostMapping("/register")
    public String handleRegister(
            @Valid @ModelAttribute("registerRequest") RegisterRequest registerRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            return "store/auth/register";
        }

        try {
            authService.initiateRegister(registerRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Mã xác thực OTP đã được gửi đến email " + registerRequest.getEmail() + ". Vui lòng kiểm tra hộp thư!");
            return "redirect:/auth/verify-otp?email=" + registerRequest.getEmail().trim();
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "store/auth/register";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Đăng ký không thành công: " + ex.getMessage());
            return "store/auth/register";
        }
    }

    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam("email") String email, Model model) {
        model.addAttribute("email", email);
        model.addAttribute("type", OtpType.REGISTRATION);
        return "store/auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String handleVerifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otpCode") String otpCode,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            UserPrincipal principal = authService.completeRegister(email, otpCode, response);
            redirectAttributes.addFlashAttribute("successMessage", "Xác thực tài khoản thành công! Chào mừng " + principal.getFullName() + " đến với OneShop.");
            return "redirect:/";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("type", OtpType.REGISTRATION);
            return "store/auth/verify-otp";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Xác thực thất bại: " + ex.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("type", OtpType.REGISTRATION);
            return "store/auth/verify-otp";
        }
    }

    @PostMapping("/resend-otp")
    public String handleResendOtp(
            @RequestParam("email") String email,
            @RequestParam("type") OtpType type,
            RedirectAttributes redirectAttributes) {
        try {
            authService.resendOtp(email, type);
            redirectAttributes.addFlashAttribute("successMessage", "Mã OTP mới đã được gửi tới " + email);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        if (type == OtpType.REGISTRATION) {
            return "redirect:/auth/verify-otp?email=" + email;
        } else {
            return "redirect:/auth/verify-reset-otp?email=" + email;
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "store/auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(
            @RequestParam("email") String email,
            RedirectAttributes redirectAttributes) {
        if (StringUtils.hasText(email)) {
            try {
                authService.initiateForgotPassword(email.trim());
            } catch (Exception ex) {
                log.error("Error in forgot-password for {}: {}", email, ex.getMessage());
            }
        }
        // Neutral response to prevent account enumeration
        redirectAttributes.addFlashAttribute("successMessage",
                "Nếu email tồn tại trong hệ thống, mã xác thực OTP đã được gửi đến hộp thư của bạn.");
        return "redirect:/auth/verify-reset-otp?email=" + (email != null ? email.trim() : "");
    }

    @GetMapping("/verify-reset-otp")
    public String verifyResetOtpPage(@RequestParam("email") String email, Model model) {
        model.addAttribute("email", email);
        model.addAttribute("type", OtpType.PASSWORD_RESET);
        return "store/auth/verify-reset-otp";
    }

    @PostMapping("/verify-reset-otp")
    public String handleVerifyResetOtp(
            @RequestParam("email") String email,
            @RequestParam("otpCode") String otpCode,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            String resetToken = authService.verifyResetOtp(email, otpCode);
            redirectAttributes.addFlashAttribute("successMessage", "Mã xác thực hợp lệ. Vui lòng nhập mật khẩu mới.");
            return "redirect:/auth/reset-password?email=" + email.trim() + "&token=" + resetToken;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("type", OtpType.PASSWORD_RESET);
            return "store/auth/verify-reset-otp";
        }
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(
            @RequestParam("email") String email,
            @RequestParam(value = "token", required = false) String token,
            Model model) {
        if (!StringUtils.hasText(token)) {
            model.addAttribute("errorMessage", "Phiên đặt lại mật khẩu không hợp lệ hoặc thiếu mã token xác thực.");
            return "store/auth/login";
        }
        model.addAttribute("email", email);
        model.addAttribute("token", token);
        return "store/auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String handleResetPassword(
            @RequestParam("email") String email,
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model) {
        try {
            authService.resetPassword(email, token, password, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập.");
            return "redirect:/auth/login?resetSuccess=true";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("email", email);
            model.addAttribute("token", token);
            return "store/auth/reset-password";
        }
    }

    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public String logout(HttpServletResponse response, RedirectAttributes redirectAttributes) {
        authService.logout(response);
        redirectAttributes.addFlashAttribute("successMessage", "Bạn đã đăng xuất an toàn.");
        return "redirect:/auth/login?logout=true";
    }
}
