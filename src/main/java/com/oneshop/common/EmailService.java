package com.oneshop.common;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Async
    public void sendOtpEmail(String recipientEmail, String otpCode, String purposeText) {
        if (senderEmail == null || senderEmail.isBlank()) {
            log.info("Email service not configured. Skipped sending email to {}. OTP Code: {}", recipientEmail, otpCode);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "OneShop Cosmetics");
            helper.setTo(recipientEmail);
            helper.setSubject("[OneShop] Mã xác thực OTP: " + otpCode);

            String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f8f9fa; margin: 0; padding: 20px; color: #22252a; }
                        .container { max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.06); }
                        .header { background: linear-gradient(135deg, #e11d48, #be123c); padding: 32px 24px; text-align: center; color: #ffffff; }
                        .header h1 { margin: 0; font-size: 26px; font-weight: 800; letter-spacing: -0.5px; }
                        .header p { margin: 6px 0 0; opacity: 0.9; font-size: 14px; }
                        .content { padding: 36px 32px; text-align: center; }
                        .otp-box { background: #fff1f2; border: 2px dashed #fda4af; border-radius: 12px; padding: 18px 24px; margin: 24px 0; display: inline-block; }
                        .otp-code { font-size: 36px; font-weight: 900; letter-spacing: 8px; color: #be123c; font-family: monospace; }
                        .notice { font-size: 13px; color: #64748b; line-height: 1.6; margin-top: 20px; }
                        .footer { background: #f1f5f9; padding: 16px; text-align: center; font-size: 12px; color: #94a3b8; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>✨ OneShop Cosmetics</h1>
                            <p>Hệ thống chuỗi mỹ phẩm chính hãng</p>
                        </div>
                        <div class="content">
                            <h3 style="margin-top: 0; color: #0f172a;">Xác thực mã bảo mật</h3>
                            <p style="color: #475569; font-size: 15px;">
                                Bạn vừa yêu cầu mã xác thực cho thao tác <strong>%s</strong> trên hệ thống OneShop.
                            </p>
                            <div class="otp-box">
                                <div class="otp-code">%s</div>
                            </div>
                            <p class="notice">
                                ⏳ Mã OTP có hiệu lực trong vòng <strong>5 phút</strong>.<br>
                                🔒 Tuyệt đối không chia sẻ mã này cho bất kỳ ai, kể cả nhân viên OneShop.
                            </p>
                        </div>
                        <div class="footer">
                            Email này được gửi tự động từ hệ thống OneShop. Vui lòng không trả lời.
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(purposeText, otpCode);

            helper.setText(html, true);
            mailSender.send(message);
            log.info("OTP email successfully sent to {}", recipientEmail);
        } catch (Exception ex) {
            log.error("Failed to send OTP email to {}: {}", recipientEmail, ex.getMessage());
        }
    }
}
