package com.oneshop.ai;

import com.oneshop.catalog.CatalogService;
import com.oneshop.catalog.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final CatalogService catalogService;

    public Map<String, Object> generateAdvice(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Map.of(
                    "reply", "Xin chào! Em là Chuyên viên tư vấn sắc đẹp của OneShop 🌸. Chị/anh cần tư vấn về loại da, sản phẩm nào hay cần thông tin chi nhánh cửa hàng ạ?",
                    "suggestions", List.of("📍 Địa chỉ cửa hàng", "🌿 Da dầu mụn", "☀️ Kem chống nắng", "🚚 Phí giao hàng")
            );
        }

        String raw = userMessage.trim();
        String lower = removeAccents(raw.toLowerCase());
        List<Product> allProducts = catalogService.getAllProducts().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .toList();

        StringBuilder reply = new StringBuilder();
        List<String> nextSuggestions = new ArrayList<>();

        // 1. INTENT: ĐỊA CHỈ, CỬA HÀNG, CHI NHÁNH, Ở ĐÂU, VỊ TRÍ, BẢN ĐỒ
        if (containsAny(lower, "dia chi", "chi nhanh", "o dau", "vi tri", "cua hang", "shop o", "co so", "quan 1", "thu duc", "store", "location", "ban do", "den dau", "tim shop")) {
            reply.append("📍 <strong>Hệ thống Chuỗi Cửa Hàng OneShop Beauty:</strong><br><br>")
                 .append("🏢 <strong>Chi nhánh 1 (Quận 1):</strong><br>")
                 .append("• Địa chỉ: <strong>120 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh</strong><br>")
                 .append("• Giờ mở cửa: 08:30 - 22:00 (mở cửa cả tuần & ngày lễ)<br>")
                 .append("• Hotline: <a href='tel:0349846114' class='text-danger fw-bold'>0349846114</a><br><br>")
                 .append("🏢 <strong>Chi nhánh 2 (TP. Thủ Đức):</strong><br>")
                 .append("• Địa chỉ: <strong>216 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh</strong><br>")
                 .append("• Giờ mở cửa: 08:30 - 22:00 (mở cửa cả tuần & ngày lễ)<br>")
                 .append("• Hotline: <a href='tel:0369815844' class='text-danger fw-bold'>0369815844</a><br><br>")
                 .append("✨ <em>Tại cả 2 chi nhánh đều có chỗ để xe máy/ô tô rộng rãi và chuyên viên hỗ trợ soi da, test sản phẩm miễn phí!</em>");
            nextSuggestions.addAll(List.of("🚚 Chính sách giao hàng", "🔥 Flash Deal đang giảm", "☀️ Kem chống nắng tốt"));
        }
        // 2. INTENT: HOTLINE, SỐ ĐIỆN THOẠI, LIÊN HỆ, TỔNG ĐÀI, CSKH, EMAIL
        else if (containsAny(lower, "hotline", "so dien thoai", "sdt", "so dt", "lien he", "email", "cskh", "tong dai", "goi dien", "cham soc khach hang")) {
            reply.append("📞 <strong>Kênh Liên Hệ & Hỗ Trợ Khách Hàng OneShop:</strong><br><br>")
                 .append("• <strong>Hotline Quận 1:</strong> <a href='tel:0349846114' class='text-danger fw-bold'>0349846114</a><br>")
                 .append("• <strong>Hotline Thủ Đức:</strong> <a href='tel:0369815844' class='text-danger fw-bold'>0369815844</a><br>")
                 .append("• <strong>Hộp thư hỗ trợ / CSKH:</strong> <a href='mailto:support@oneshop.vn' class='text-secondary'>support@oneshop.vn</a><br>")
                 .append("• <strong>Thời gian tiếp nhận:</strong> 08:30 - 22:00 tất cả các ngày trong tuần.<br><br>")
                 .append("Bạn cũng có thể nhắn trực tiếp tại đây để em hỗ trợ tư vấn tức thì nhé!");
            nextSuggestions.addAll(List.of("📍 Địa chỉ cửa hàng", "🛡️ Đổi trả hàng", "🚚 Chính sách vận chuyển"));
        }
        // 3. INTENT: GIAO HÀNG, PHÍ SHIP, VẬN CHUYỂN, FREESHIP, COD, HỎA TỐC
        else if (containsAny(lower, "giao hang", "phi ship", "ship", "van chuyen", "cod", "hoa toc", "freeship", "mien phi ship", "phi van chuyen", "bao lau", "nhan hang")) {
            reply.append("🚚 <strong>Chính Sách Vận Chuyển Cực Ưu Đãi Tại OneShop:</strong><br><br>")
                 .append("• <strong>MIỄN PHÍ VẬN CHUYỂN NỘI THÀNH:</strong> Áp dụng cho mọi đơn giao tại Quận 1 & TP. Thủ Đức.<br>")
                 .append("• <strong>FREESHIP TOÀN QUỐC:</strong> Cho mọi đơn hàng có giá trị từ <strong>500.000₫</strong> trở lên.<br>")
                 .append("• <strong>Thời gian nhận hàng:</strong><br>")
                 .append("&nbsp;&nbsp;+ Nội thành TP.HCM: Giao hỏa tốc 2 giờ hoặc trong ngày.<br>")
                 .append("&nbsp;&nbsp;+ Các tỉnh thành khác: Từ 1 - 3 ngày làm việc.<br>")
                 .append("• <strong>Hình thức thanh toán:</strong> Hỗ trợ nhận hàng thanh toán tận nơi (COD) và được kiểm tra hàng trước khi nhận.");
            nextSuggestions.addAll(List.of("🎁 Mã giảm giá", "📍 Xem chi nhánh", "🌿 Mỹ phẩm bán chạy"));
        }
        // 4. INTENT: CHÍNH HÃNG, HÀNG THẬT/GIẢ, FAKE, ĐỔI TRẢ, BẢO HÀNH, NGUỒN GỐC
        else if (containsAny(lower, "chinh hang", "that gia", "fake", "auth", "doi tra", "bao hanh", "hoan tien", "kich ung", "tem phu", "nguon goc", "xuat xu")) {
            reply.append("🛡️ <strong>Cam Kết 100% Chính Hãng & Đổi Trả Linh Hoạt:</strong><br><br>")
                 .append("• OneShop là đối tác phân phối chính ngạch từ các thương hiệu: <em>La Roche-Posay, Kiehl's, Paula's Choice, M·A·C, Innisfree, The Ordinary, Laneige, L'Oréal Paris...</em><br>")
                 .append("• Tất cả sản phẩm đều có <strong>tem phụ tiếng Việt</strong> và xuất hóa đơn VAT theo yêu cầu.<br>")
                 .append("• <strong>Chính sách đổi trả trong 7 ngày:</strong> Đổi mới hoặc hoàn tiền 100% nếu sản phẩm có lỗi từ nhà sản xuất hoặc da bị kích ứng có xác nhận y khoa.");
            nextSuggestions.addAll(List.of("📍 Địa chỉ showroom", "☀️ Kem chống nắng tốt", "💧 Serum phục hồi"));
        }
        // 5. INTENT: KHUYẾN MÃI, GIẢM GIÁ, VOUCHER, MÃ GIẢM GIÁ, COUPON, FLASH DEAL
        else if (containsAny(lower, "khuyen mai", "giam gia", "voucher", "ma giam", "coupon", "flash deal", "sale", "uu dai", "deal")) {
            reply.append("🎁 <strong>Chương Trình Ưu Đãi & Mã Giảm Giá Đang Diễn Ra:</strong><br><br>")
                 .append("• ⚡ <strong>Flash Deal 2 Giờ:</strong> Giảm sốc đến 40% thay đổi liên tục trên trang chủ.<br>")
                 .append("• 🎟️ Mã <strong><code>WELCOME50</code></strong>: Giảm ngay 50.000₫ cho đơn từ 300.000₫ (dành cho bạn mới).<br>")
                 .append("• 🎟️ Mã <strong><code>ONESHOP10</code></strong>: Giảm 10% (tối đa 100.000₫) cho đơn từ 500.000₫.<br>")
                 .append("• 🎟️ Mã <strong><code>BEAUTYVIP</code></strong>: Giảm 15% (tối đa 200.000₫) cho đơn từ 1.000.000₫.<br><br>")
                 .append("💡 Bạn chỉ cần nhập mã vào ô Voucher tại bước thanh toán để được giảm trừ trực tiếp nhé!");
            nextSuggestions.addAll(List.of("⚡ Xem Flash Deal", "🌿 Da dầu mụn", "💄 Son môi hot"));
        }
        // 6. INTENT: DA DẦU, DA MỤN, MỤN ẨN, MỤN ĐẦU ĐEN, LỖ CHÂN LÔNG TO, BHA
        else if (containsAny(lower, "da dau", "mun", "lo chan long", "kiem dau", "bha", "salicylic", "nhon", "dau mun", "mun an", "mun viem", "dau nhon")) {
            reply.append("🌿 <strong>Tư Vấn Routine Chăm Sóc Da Dầu Mụn & Lỗ Chân Lông To:</strong><br>")
                 .append("Với da dầu mụn, nguyên tắc vàng là: <em>Làm sạch sâu - Tẩy da chết hóa học - Cấp ẩm nhẹ dịu - Chống nắng kiềm dầu</em>:<br><br>");

            appendMatchingProducts(reply, allProducts, "effaclar", "bha", "anthelios", "simple", "oil control");

            reply.append("<br>💡 <strong>Gợi ý chu trình:</strong><br>")
                 .append("1. Gel rửa mặt tạo bọt dịu nhẹ kiềm dầu (sáng & tối)<br>")
                 .append("2. Dung dịch tẩy da chết BHA 2% Paula's Choice (2-3 lần/tuần ban đêm giúp thông thoáng lỗ chân lông)<br>")
                 .append("3. Kem dưỡng phục hồi mỏng nhẹ, không gây bí tắc<br>")
                 .append("4. Kem chống nắng phổ rộng ráo mịn Anthelios kiềm dầu ban ngày.");
            nextSuggestions.addAll(List.of("BHA Paula's Choice", "Kem chống nắng La Roche-Posay", "Sữa rửa mặt Simple"));
        }
        // 7. INTENT: KEM CHỐNG NẮNG, CHỐNG TIA UV, BẢO VỆ DA
        else if (containsAny(lower, "chong nang", "kem chong nang", "sunscreen", "spf", "uv", "tia uv")) {
            reply.append("☀️ <strong>Top Kem Chống Nắng Bán Chạy & Bảo Vệ Da Toàn Diện:</strong><br>")
                 .append("Chống nắng là bước quan trọng nhất để ngừa lão hóa, sạm nám và bảo vệ da:<br><br>");

            appendMatchingProducts(reply, allProducts, "anthelios", "chong nang", "sunscreen");

            reply.append("<br>💡 <strong>Lời khuyên chuyên viên:</strong> Nên thoa kem chống nắng đủ 2 đốt ngón tay trước khi ra ngoài 15-20 phút và dặm lại sau 3-4 tiếng nếu hoạt động ngoài trời.");
            nextSuggestions.addAll(List.of("Kem chống nắng kiềm dầu", "Nước tẩy trang sạch sâu", "Serum phục hồi da"));
        }
        // 8. INTENT: DA KHÔ, CẤP ẨM, BONG TRÓC, PHỤC HỒI, TREATMENT, B5, HOA CÚC
        else if (containsAny(lower, "da kho", "cap am", "bong troc", "phuc hoi", "treatment", "b5", "ha", "hyaluronic", "kho rap", "nhay cam", "hoa cuc", "water cream")) {
            reply.append("💧 <strong>Giải Pháp Cấp Ẩm Chuyên Sâu & Phục Hồi Hàng Rào Bảo Vệ Da:</strong><br>")
                 .append("Khi da khô căng, rát hoặc đang dùng treatment nặng đô (retinol/tre), bạn cần cấp ẩm đa tầng và bổ sung B5, Ceramide, chiết xuất hoa cúc:<br><br>");

            appendMatchingProducts(reply, allProducts, "b5", "hoa cuc", "water cream", "calendula", "kem duong");

            reply.append("<br>💡 Giúp làm dịu tức thì cảm giác châm chích, giữ nước căng bóng và tái tạo da khỏe mạnh.");
            nextSuggestions.addAll(List.of("Kem dưỡng Kiehl's hoa cúc", "Serum B5 La Roche-Posay", "Da khô dùng gì?"));
        }
        // 9. INTENT: SỮA RỬA MẶT, TẨY TRANG, LÀM SẠCH
        else if (containsAny(lower, "sua rua mat", "rua mat", "tay trang", "cleanser", "nuoc tay trang", "lam sach")) {
            reply.append("🫧 <strong>Các Dòng Làm Sạch Dịu Nhẹ Được Yêu Thích Nhất:</strong><br>")
                 .append("Bước làm sạch giúp da hấp thụ tốt mọi dưỡng chất tiếp theo mà không gây khô căng:<br><br>");

            appendMatchingProducts(reply, allProducts, "sua rua mat", "facial wash", "effaclar gel", "tay trang");

            reply.append("<br>💡 Hãy rửa mặt với nước ấm vừa phải và massage nhẹ nhàng từ 30-60 giây nhé!");
            nextSuggestions.addAll(List.of("Sữa rửa mặt Simple", "Gel rửa mặt La Roche-Posay", "BHA 2%"));
        }
        // 10. INTENT: SON MÔI, TRANG ĐIỂM, PHẤN NƯỚC, MAKEUP, CUSHION
        else if (containsAny(lower, "son", "son moi", "lipstick", "phan nuoc", "cushion", "kem nen", "trang diem", "makeup", "mac", "phan phu", "ma hong")) {
            reply.append("💄 <strong>Bộ Sưu Tập Son Môi & Trang Điểm Hot Trend:</strong><br>")
                 .append("OneShop có sẵn các dòng son lì tôn da và cushion che phủ tự nhiên:<br><br>");

            appendMatchingProducts(reply, allProducts, "chili", "son", "cushion", "phan", "mac", "innisfree");

            reply.append("<br>💡 Chất son mịn môi, màu son thời thượng chuẩn nét giúp bạn luôn tự tin tỏa sáng!");
            nextSuggestions.addAll(List.of("Son MAC Chili", "Phấn nước Innisfree", "Cách chọn màu son"));
        }
        // 11. INTENT: THƯƠNG HIỆU CỤ THỂ (BRAND)
        else if (containsAny(lower, "la roche", "kiehl", "paula", "mac", "innisfree", "loreal", "laneige", "the ordinary")) {
            String brandKey = "";
            if (lower.contains("la roche")) brandKey = "la roche";
            else if (lower.contains("kiehl")) brandKey = "kiehl";
            else if (lower.contains("paula")) brandKey = "paula";
            else if (lower.contains("mac")) brandKey = "mac";
            else if (lower.contains("innisfree")) brandKey = "innisfree";
            else if (lower.contains("loreal")) brandKey = "l'oreal";
            else if (lower.contains("laneige")) brandKey = "laneige";
            else if (lower.contains("the ordinary")) brandKey = "the ordinary";

            reply.append("✨ <strong>Sản Phẩm Chính Hãng Thuộc Thương Hiệu Bạn Quan Tâm:</strong><br><br>");
            appendMatchingProducts(reply, allProducts, brandKey);
            reply.append("<br>Tất cả sản phẩm đều được bảo đảm chính hãng 100% với tem nhãn đầy đủ.");
            nextSuggestions.addAll(List.of("📍 Địa chỉ cửa hàng", "☀️ Kem chống nắng", "🌿 Xem khuyến mãi"));
        }
        // 12. INTENT: LỜI CHÀO HỎI THUẦN TÚY (CHỈ KHI KHÔNG CÓ Ý ĐỊNH CỤ THỂ NÀO KHÁC)
        else if (isPureGreeting(lower)) {
            reply.append("Dạ chào bạn! Rất vui được đồng hành cùng bạn tại <strong>OneShop Beauty</strong> 🌸✨.<br><br>")
                 .append("Em có thể hỗ trợ bạn ngay về:<br>")
                 .append("• <strong>Địa chỉ & hotline 2 chi nhánh</strong> (Quận 1 & Thủ Đức)<br>")
                 .append("• <strong>Tư vấn chu trình dưỡng da</strong> (da dầu mụn, da khô, nhạy cảm)<br>")
                 .append("• <strong>Gợi ý mỹ phẩm chính hãng</strong> (chống nắng, phục hồi, son môi, BHA...)<br>")
                 .append("• <strong>Chính sách giao hàng miễn phí & mã giảm giá</strong><br><br>")
                 .append("Bạn đang quan tâm đến mục nào để em hỗ trợ nhanh nhất nhé?");
            nextSuggestions.addAll(List.of("📍 Địa chỉ chi nhánh", "🌿 Tư vấn da dầu mụn", "☀️ Kem chống nắng", "🎁 Mã giảm giá"));
        }
        // 13. DYNAMIC SEARCH ACROSS CATALOG
        else {
            List<Product> matched = allProducts.stream()
                    .filter(p -> removeAccents(p.getName().toLowerCase()).contains(lower)
                              || (p.getCategory() != null && removeAccents(p.getCategory().getName().toLowerCase()).contains(lower))
                              || (p.getBrand() != null && removeAccents(p.getBrand().getName().toLowerCase()).contains(lower)))
                    .limit(4)
                    .toList();

            if (!matched.isEmpty()) {
                reply.append("Dạ, OneShop có các sản phẩm phù hợp với nhu cầu của bạn nè:<br><br>");
                for (Product p : matched) {
                    reply.append("• <a href='/products/").append(p.getSlug())
                         .append("' class='text-danger fw-bold text-decoration-none'>")
                         .append(p.getName()).append("</a> — <span class='text-danger fw-bold'>")
                         .append(String.format("%,d ₫", p.getPrice().longValue()))
                         .append("</span>");
                    if (p.hasDiscount()) {
                        reply.append(" <span class='text-muted text-decoration-line-through small'>")
                             .append(String.format("%,d ₫", p.getCompareAtPrice().longValue())).append("</span>");
                    }
                    reply.append("<br>");
                }
                reply.append("<br>Bạn có thể nhấp vào tên sản phẩm để xem chi tiết công dụng hoặc ghé trực tiếp 2 chi nhánh OneShop để trải nghiệm nhé!");
                nextSuggestions.addAll(List.of("📍 Địa chỉ cửa hàng", "🚚 Chính sách freeship", "🎁 Mã giảm giá"));
            } else {
                reply.append("Dạ cảm ơn bạn đã gửi câu hỏi cho OneShop! 🌸<br><br>")
                     .append("Để em tư vấn đúng và chuẩn xác nhất cho bạn, bạn hãy cho em biết cụ thể hơn về <strong>nhu cầu cần tìm</strong> (ví dụ: <em>tìm kem chống nắng, tư vấn da dầu mụn, địa chỉ chi nhánh, chính sách đổi trả...</em>) nhé!<br><br>")
                     .append("Hoặc bạn có thể bấm nhanh vào các nút gợi ý bên dưới nè 👇");
                nextSuggestions.addAll(List.of("📍 Địa chỉ chi nhánh", "🌿 Da dầu mụn", "☀️ Kem chống nắng", "📞 Hotline hỗ trợ"));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reply", reply.toString());
        result.put("suggestions", nextSuggestions);
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) return false;
        for (String kw : keywords) {
            if (text.contains(removeAccents(kw.toLowerCase()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPureGreeting(String lower) {
        // Only consider it pure greeting if it's very short or strictly greeting words
        String trimmed = lower.replaceAll("[^a-zA-Z0-9 ]", "").trim();
        List<String> words = Arrays.asList(trimmed.split("\\s+"));
        if (words.size() <= 4 && (containsAny(lower, "chao", "hi", "hello", "alo", "ad oi", "shop oi", "hi shop", "chao shop"))) {
            return true;
        }
        return false;
    }

    private void appendMatchingProducts(StringBuilder sb, List<Product> allProducts, String... keywords) {
        int count = 0;
        for (Product p : allProducts) {
            String pName = removeAccents(p.getName().toLowerCase());
            String bName = p.getBrand() != null ? removeAccents(p.getBrand().getName().toLowerCase()) : "";
            boolean match = false;
            for (String kw : keywords) {
                String normalizedKw = removeAccents(kw.toLowerCase());
                if (pName.contains(normalizedKw) || bName.contains(normalizedKw)) {
                    match = true;
                    break;
                }
            }
            if (match) {
                String safeName = org.springframework.web.util.HtmlUtils.htmlEscape(p.getName());
                String safeSlug = org.springframework.web.util.HtmlUtils.htmlEscape(p.getSlug());
                sb.append("• <a href='/products/").append(safeSlug)
                  .append("' class='text-danger fw-bold text-decoration-none'>")
                  .append(safeName).append("</a>")
                  .append(" — <span class='text-danger fw-bold'>")
                  .append(String.format("%,d ₫", p.getPrice().longValue())).append("</span>");
                if (p.hasDiscount()) {
                    sb.append(" <span class='text-muted text-decoration-line-through small'>")
                      .append(String.format("%,d ₫", p.getCompareAtPrice().longValue())).append("</span>");
                }
                sb.append("<br>");
                count++;
                if (count >= 3) break;
            }
        }
    }

    private static String removeAccents(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }
}
