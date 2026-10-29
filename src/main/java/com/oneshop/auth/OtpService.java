package com.oneshop.auth;

import com.oneshop.common.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;

    @Transactional
    public void sendOtp(String email, OtpType type) {
        String cleanEmail = email.trim().toLowerCase();

        // 1. Check cooldown
        Optional<OtpVerification> latestOpt = otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(cleanEmail, type);
        if (latestOpt.isPresent()) {
            OtpVerification latest = latestOpt.get();
            if (latest.getCreatedAt() != null &&
                    latest.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                long waitSeconds = java.time.Duration.between(LocalDateTime.now(), latest.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS)).toSeconds();
                throw new IllegalStateException("Vui lòng đợi " + Math.max(1, waitSeconds) + " giây nữa để gửi lại mã OTP.");
            }
        }

        // 2. Generate 6-digit OTP
        int codeInt = secureRandom.nextInt(900000) + 100000;
        String otpCode = String.valueOf(codeInt);

        // 3. Save OTP record
        OtpVerification otp = OtpVerification.builder()
                .email(cleanEmail)
                .otpCode(otpCode)
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .verified(false)
                .attempts(0)
                .build();
        otpRepository.save(otp);

        // 4. Send email
        String purpose = type == OtpType.REGISTRATION ? "Đăng ký tài khoản" : "Đặt lại mật khẩu";
        emailService.sendOtpEmail(cleanEmail, otpCode, purpose);
        log.info("Generated OTP for {} ({})", cleanEmail, type);
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class})
    public boolean verifyOtp(String email, String inputCode, OtpType type) {
        String cleanEmail = email.trim().toLowerCase();
        OtpVerification otp = otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(cleanEmail, type)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy mã OTP cho email này. Vui lòng bấm gửi lại."));

        if (Boolean.TRUE.equals(otp.getVerified())) {
            throw new IllegalArgumentException("Mã OTP này đã được xác thực trước đó.");
        }

        if (otp.isExpired()) {
            throw new IllegalArgumentException("Mã OTP đã hết hạn (5 phút). Vui lòng yêu cầu mã mới.");
        }

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Bạn đã nhập sai quá " + MAX_ATTEMPTS + " lần. Vui lòng yêu cầu mã mới.");
        }

        otp.setAttempts(otp.getAttempts() + 1);

        if (!otp.getOtpCode().equals(inputCode.trim())) {
            otpRepository.save(otp);
            int remaining = MAX_ATTEMPTS - otp.getAttempts();
            throw new IllegalArgumentException("Mã OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        otp.setVerified(true);
        otpRepository.save(otp);
        return true;
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class})
    public String verifyPasswordResetOtp(String email, String inputCode) {
        verifyOtp(email, inputCode, OtpType.PASSWORD_RESET);
        String cleanEmail = email.trim().toLowerCase();
        OtpVerification otp = otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(cleanEmail, OtpType.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi xác thực OTP"));

        String resetToken = java.util.UUID.randomUUID().toString().replace("-", "");
        otp.setResetToken(resetToken);
        otpRepository.save(otp);
        return resetToken;
    }

    @Transactional
    public void validateAndConsumeResetToken(String email, String resetToken) {
        if (resetToken == null || resetToken.isBlank()) {
            throw new IllegalArgumentException("Mã token đặt lại mật khẩu không hợp lệ.");
        }
        String cleanEmail = email.trim().toLowerCase();
        OtpVerification otp = otpRepository.findByResetToken(resetToken.trim())
                .orElseThrow(() -> new IllegalArgumentException("Token đặt lại mật khẩu không tồn tại hoặc đã hết hạn."));

        if (!otp.getEmail().equalsIgnoreCase(cleanEmail)) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu không khớp với email yêu cầu.");
        }
        if (Boolean.TRUE.equals(otp.getConsumed())) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu này đã được sử dụng.");
        }
        if (otp.isExpired()) {
            throw new IllegalArgumentException("Token đặt lại mật khẩu đã hết hạn.");
        }
        if (!Boolean.TRUE.equals(otp.getVerified())) {
            throw new IllegalArgumentException("OTP chưa được xác thực.");
        }

        otp.setConsumed(true);
        otpRepository.save(otp);
    }

    @Transactional(readOnly = true)
    public boolean isOtpVerified(String email, OtpType type) {
        String cleanEmail = email.trim().toLowerCase();
        return otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(cleanEmail, type)
                .map(otp -> Boolean.TRUE.equals(otp.getVerified()) && !otp.isExpired() && !Boolean.TRUE.equals(otp.getConsumed()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<String> getLatestActiveOtp(String email, OtpType type) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String cleanEmail = email.trim().toLowerCase();
        return otpRepository.findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(cleanEmail, type)
                .filter(otp -> !Boolean.TRUE.equals(otp.getVerified()) && !otp.isExpired())
                .map(OtpVerification::getOtpCode);
    }
}
