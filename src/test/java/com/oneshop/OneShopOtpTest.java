package com.oneshop;

import com.oneshop.auth.OtpService;
import com.oneshop.auth.OtpType;
import com.oneshop.auth.OtpVerification;
import com.oneshop.auth.OtpRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DisplayName("Unit Tests — Hệ thống mã xác thực OTP qua Email")
class OneShopOtpTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpRepository otpRepository;

    @Test
    @Transactional
    @DisplayName("TC-11: Sinh mã OTP và xác thực thành công")
    void testGenerateAndVerifyOtp() {
        String email = "test.customer@gmail.com";

        // 1. Send OTP
        otpService.sendOtp(email, OtpType.REGISTRATION);

        // 2. Query OTP record
        Optional<OtpVerification> otpOpt = otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(email, OtpType.REGISTRATION);
        assertTrue(otpOpt.isPresent(), "Mã OTP phải được lưu vào database");

        OtpVerification otp = otpOpt.get();
        assertNotNull(otp.getOtpCode());
        assertEquals(6, otp.getOtpCode().length(), "Mã OTP phải gồm 6 chữ số");
        assertFalse(otp.getVerified(), "Ban đầu verified phải là false");

        // 3. Verify with wrong code
        assertThrows(IllegalArgumentException.class, () ->
                otpService.verifyOtp(email, "000000", OtpType.REGISTRATION)
        );

        // 4. Verify with correct code
        boolean result = otpService.verifyOtp(email, otp.getOtpCode(), OtpType.REGISTRATION);
        assertTrue(result, "Xác thực với OTP đúng phải trả về true");
        assertTrue(otpService.isOtpVerified(email, OtpType.REGISTRATION));
    }

    @Test
    @DisplayName("TC-12: Chống Brute-Force OTP - Ghi nhận số lần thử sai")
    void testOtpBruteForceLockout() {
        String email = "bruteforce.test@oneshop.vn";
        otpService.sendOtp(email, OtpType.REGISTRATION);

        for (int i = 1; i <= 5; i++) {
            final String wrongCode = "99999" + i;
            assertThrows(IllegalArgumentException.class, () ->
                    otpService.verifyOtp(email, wrongCode, OtpType.REGISTRATION)
            );
        }

        // Lần thử thứ 6 phải bị khóa do vượt quá MAX_ATTEMPTS
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                otpService.verifyOtp(email, "123456", OtpType.REGISTRATION)
        );
        assertTrue(ex.getMessage().contains("nhập sai quá 5 lần"));
    }

    @Test
    @DisplayName("TC-13: Reset token dùng 1 lần (Single-use token)")
    void testPasswordResetTokenSingleUse() {
        String email = "reset.token.test@oneshop.vn";
        otpService.sendOtp(email, OtpType.PASSWORD_RESET);

        OtpVerification otp = otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(email, OtpType.PASSWORD_RESET).orElseThrow();
        String resetToken = otpService.verifyPasswordResetOtp(email, otp.getOtpCode());
        assertNotNull(resetToken);

        // Dùng token lần đầu: thành công
        assertDoesNotThrow(() -> otpService.validateAndConsumeResetToken(email, resetToken));

        // Dùng lại token lần 2: phải bị từ chối ngay lập tức
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                otpService.validateAndConsumeResetToken(email, resetToken)
        );
        assertTrue(ex.getMessage().contains("đã được sử dụng"));
    }
}
