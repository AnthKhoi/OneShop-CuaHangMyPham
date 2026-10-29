package com.oneshop.common;

import com.oneshop.branch.Branch;
import com.oneshop.branch.BranchRepository;
import com.oneshop.catalog.*;
import com.oneshop.inventory.*;
import com.oneshop.order.*;
import com.oneshop.promotion.Coupon;
import com.oneshop.promotion.CouponDiscountType;
import com.oneshop.promotion.CouponRepository;
import com.oneshop.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final BranchInventoryRepository inventoryRepository;
    private final StockMovementRepository movementRepository;
    private final CouponRepository couponRepository;
    private final OrderRepository orderRepository;
    private final com.oneshop.customer.ProductReviewRepository productReviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Checking and seeding OneShop initial data...");

        // 0. Ensure Unicode NVARCHAR columns in SQL Server
        try {
            jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN shipping_address NVARCHAR(500) NULL");
            jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN recipient_name NVARCHAR(200) NOT NULL");
            jdbcTemplate.execute("ALTER TABLE orders ALTER COLUMN note NVARCHAR(500) NULL");
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN address NVARCHAR(500) NULL");
            jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN full_name NVARCHAR(200) NOT NULL");
            // Clean up obsolete inventory staff role and accounts if previously existed
            jdbcTemplate.execute("DELETE FROM user_roles WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_INVENTORY_STAFF')");
            jdbcTemplate.execute("DELETE FROM roles WHERE name = 'ROLE_INVENTORY_STAFF'");
            jdbcTemplate.execute("DELETE FROM users WHERE email IN ('inventory.q1@oneshop.vn', 'inventory.thuduc@oneshop.vn')");

            // Ensure all seeded brands have valid logo URLs
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?w=300&q=80' WHERE slug = 'kiehls' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=300&q=80' WHERE slug = 'paulas-choice' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=300&q=80' WHERE slug = 'mac-cosmetics' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?w=300&q=80' WHERE slug = 'innisfree' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=300&q=80' WHERE slug = 'loreal-paris' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1571781926291-c477ebfd024b?w=300&q=80' WHERE slug = 'laneige' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1608248597359-0a66d0ef0528?w=300&q=80' WHERE slug = 'the-ordinary' AND (logo_url IS NULL OR logo_url = '')");
            jdbcTemplate.execute("UPDATE brands SET logo_url = 'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=300&q=80' WHERE slug = 'la-roche-posay' AND (logo_url IS NULL OR logo_url = '' OR logo_url LIKE '%wikimedia%')");
        } catch (Exception ex) {
            log.debug("DB init / cleanup notice: {}", ex.getMessage());
        }

        // 0.1 Ensure media_assets table exists
        try {
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'media_assets') " +
                    "CREATE TABLE media_assets (" +
                    "id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                    "file_name NVARCHAR(255) NULL, " +
                    "secure_url NVARCHAR(500) NOT NULL, " +
                    "cloudinary_public_id NVARCHAR(150) NULL, " +
                    "file_size BIGINT NULL, " +
                    "file_type NVARCHAR(50) NULL, " +
                    "folder NVARCHAR(100) NULL, " +
                    "uploaded_by NVARCHAR(100) NULL, " +
                    "created_at DATETIME2 NULL);");

            // Ensure columns for OTP and Orders
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.tables WHERE name = 'otp_verifications') AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('otp_verifications') AND name = 'consumed') " +
                    "ALTER TABLE otp_verifications ADD consumed BIT NULL DEFAULT 0;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.tables WHERE name = 'otp_verifications') AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('otp_verifications') AND name = 'reset_token') " +
                    "ALTER TABLE otp_verifications ADD reset_token NVARCHAR(64) NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.tables WHERE name = 'orders') AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('orders') AND name = 'access_token') " +
                    "ALTER TABLE orders ADD access_token NVARCHAR(64) NULL;");
            jdbcTemplate.execute("IF EXISTS (SELECT * FROM sys.tables WHERE name = 'orders') AND NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('orders') AND name = 'shipper_id') " +
                    "ALTER TABLE orders ADD shipper_id BIGINT NULL;");

            // Ensure wishlist and cart tables exist
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'wishlist_items') " +
                    "CREATE TABLE wishlist_items (" +
                    "id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                    "user_id BIGINT NOT NULL, " +
                    "product_id BIGINT NOT NULL, " +
                    "created_at DATETIME2 NULL, " +
                    "CONSTRAINT uk_user_product_wishlist UNIQUE (user_id, product_id));");

            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'cart_items') " +
                    "CREATE TABLE cart_items (" +
                    "id BIGINT IDENTITY(1,1) PRIMARY KEY, " +
                    "user_id BIGINT NOT NULL, " +
                    "product_id BIGINT NOT NULL, " +
                    "quantity INT NOT NULL, " +
                    "updated_at DATETIME2 NULL);");
        } catch (Exception ex) {
            log.warn("Could not check/create tables or columns via jdbcTemplate: {}", ex.getMessage());
        }

        // 1. Roles
        Role roleAdmin = initRole(RoleName.ROLE_ADMIN, "Quản trị viên toàn hệ thống OneShop");
        Role roleOwner = initRole(RoleName.ROLE_OWNER, "Chủ sở hữu chuỗi cửa hàng");
        Role roleBranchManager = initRole(RoleName.ROLE_BRANCH_MANAGER, "Quản lý chi nhánh cửa hàng");
        Role roleStaff = initRole(RoleName.ROLE_STAFF, "Nhân viên nghiệp vụ chi nhánh");
        Role roleSalesStaff = initRole(RoleName.ROLE_SALES_STAFF, "Nhân viên bán hàng / tư vấn mỹ phẩm");
        Role roleShipper = initRole(RoleName.ROLE_SHIPPER, "Nhân viên giao hàng / Shipper");
        Role roleCustomer = initRole(RoleName.ROLE_CUSTOMER, "Khách hàng mua sắm");

        // 2. Branches (Rút gọn chỉ 2 chi nhánh: Quận 1 và Thủ Đức)
        Branch b1 = initBranch("CN-HCM-Q1", "OneShop Quận 1 - TP. Hồ Chí Minh", "120 Hai Bà Trưng, Phường Bến Nghé, Quận 1, TP. HCM", "028 3822 9999", "08:30 - 22:00");
        Branch b2 = initBranch("CN-HCM-TD", "OneShop Thủ Đức - TP. Hồ Chí Minh", "216 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. HCM", "028 3896 8888", "08:30 - 22:00");

        // 3. Demo Users & Staff (Chỉ cho Admin và 2 chi nhánh Q1, Thủ Đức)
        // Quản trị viên hệ thống
        initUser("admin@oneshop.vn", "Admin@123", "Admin", "0901111222", "TP. Hồ Chí Minh", null, Set.of(roleAdmin));

        // Chi nhánh 1: Quận 1 - TP. Hồ Chí Minh
        initUser("manager.q1@oneshop.vn", "Manager@123", "Nguyễn Hoàng Nam", "0902222333", "120 Hai Bà Trưng, Q.1, TP. HCM", b1, Set.of(roleBranchManager));
        initUser("sales.q1@oneshop.vn", "Sales@123", "Trần Thị Mai Anh", "0903333444", "Bình Thạnh, TP. HCM", b1, Set.of(roleSalesStaff));

        // Chi nhánh 2: Thủ Đức - TP. Hồ Chí Minh
        initUser("manager.thuduc@oneshop.vn", "Manager@123", "Phạm Đức Tuấn", "0905111222", "216 Võ Văn Ngân, TP. Thủ Đức, TP. HCM", b2, Set.of(roleBranchManager));
        initUser("sales.thuduc@oneshop.vn", "Sales@123", "Đỗ Thu Thảo", "0905333444", "Linh Trung, TP. Thủ Đức, TP. HCM", b2, Set.of(roleSalesStaff));

        // Nhân viên giao hàng (Shipper)
        initUser("shipper@oneshop.vn", "Shipper@123", "Nguyễn Văn Giao (Shipper)", "0908889999", "TP. Hồ Chí Minh", null, Set.of(roleShipper));

        User customerDemo = initUser("customer@gmail.com", "Customer@123", "Nguyễn Thảo My", "0918888999", "45 Lê Lợi, Bến Nghé, Quận 1, TP. HCM", null, Set.of(roleCustomer));
        User userKhoi = initUser("lehuynhanhkhoi2006@gmail.com", "Khoi@123", "Lê Huỳnh Anh Khôi", "0349846114", "TP. Hồ Chí Minh", null, Set.of(roleCustomer));

        // 4. Coupons
        initCoupon("WELCOME50", "Giảm 50.000đ cho đơn từ 100.000đ", CouponDiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50000), BigDecimal.valueOf(100000), null);
        initCoupon("ONESHOP", "Ưu đãi thành viên giảm 30.000đ mọi đơn hàng", CouponDiscountType.FIXED_AMOUNT, BigDecimal.valueOf(30000), BigDecimal.ZERO, null);
        initCoupon("ONESHOP10", "Giảm 10% tối đa 100.000đ cho đơn từ 200.000đ", CouponDiscountType.PERCENTAGE, BigDecimal.valueOf(10), BigDecimal.valueOf(200000), BigDecimal.valueOf(100000));
        initCoupon("BEAUTYVIP", "Ưu đãi khách hàng VIP giảm 15% tối đa 200.000đ", CouponDiscountType.PERCENTAGE, BigDecimal.valueOf(15), BigDecimal.valueOf(500000), BigDecimal.valueOf(200000));

        // 5. Categories
        Category catSkincare = initCategory("Chăm sóc da mặt", "cham-soc-da-mat", "Các sản phẩm toner, serum, kem dưỡng, chống nắng chuyên sâu.", "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=500&q=80", 1);
        Category catMakeup = initCategory("Trang điểm", "trang-diem", "Phấn nền, cushion, má hồng, mascara làm đẹp tự nhiên.", "https://images.unsplash.com/photo-1512496015851-a90fb38ba796?w=500&q=80", 2);
        Category catLipstick = initCategory("Son môi", "son-moi", "Son kem lì, son thỏi dưỡng ẩm từ các thương hiệu hàng đầu thế giới.", "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=500&q=80", 3);
        Category catBodycare = initCategory("Chăm sóc cơ thể", "cham-soc-co-the", "Sữa dưỡng thể, tẩy tế bào chết, sữa tắm cấp ẩm chuyên sâu.", "https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?w=500&q=80", 4);
        Category catPerfume = initCategory("Nước hoa", "nuoc-hoa", "Nước hoa niche, eau de parfum quyến rũ, lưu hương dài lâu.", "https://images.unsplash.com/photo-1592945403244-b3fbafd7f539?w=500&q=80", 5);
        Category catHaircare = initCategory("Chăm sóc tóc", "cham-soc-toc", "Dầu gội, dầu xả, tinh dầu dưỡng tóc phục hồi hư tổn.", "https://images.unsplash.com/photo-1527799820374-dcf8d9d4a388?w=500&q=80", 6);

        // 6. Brands
        Brand bLRP = initBrand("La Roche-Posay", "la-roche-posay", "Dược mỹ phẩm hàng đầu nước Pháp cho da nhạy cảm.", "/uploads/products/lrp-anthelios-50ml.png");
        Brand bKiehls = initBrand("Kiehl's", "kiehls", "Thương hiệu mỹ phẩm cao cấp từ New York từ năm 1851.", "/uploads/products/kiehls-calendula-toner-250ml.jpg");
        Brand bPC = initBrand("Paula's Choice", "paulas-choice", "Khoa học về làn da với thành phần BHA, AHA nồng độ chuẩn.", "/uploads/products/pc-bha-2-percent-118ml.png");
        Brand bMAC = initBrand("M·A·C Cosmetics", "mac-cosmetics", "Thương hiệu trang điểm chuyên nghiệp đỉnh cao toàn cầu.", "/uploads/products/mac-matte-lipstick-chili.png");
        Brand bInnisfree = initBrand("Innisfree", "innisfree", "Thương hiệu thiên nhiên thanh khiết từ đảo ngọc Jeju Hàn Quốc.", "/uploads/products/innisfree-cushion-no-sebum.jpg");
        Brand bLoreal = initBrand("L'Oréal Paris", "loreal-paris", "Thương hiệu làm đẹp số 1 thế giới đến từ Paris Pháp.", "/uploads/products/loreal-infallible-32h-30ml.png");
        Brand bLaneige = initBrand("Laneige", "laneige", "Chuyên gia dưỡng ẩm và công nghệ Water Bank nổi tiếng Hàn Quốc.", "/uploads/products/laneige-lip-sleeping-mask-berry.jpg");
        Brand bTheOrdinary = initBrand("The Ordinary", "the-ordinary", "Mỹ phẩm tối giản, nồng độ hoạt chất rõ ràng, hiệu quả cao.", "/uploads/products/the-ordinary-niacinamide-30ml.png");
        Brand bBioderma = initBrand("Bioderma", "bioderma", "Tiên phong công nghệ sinh học Micellar Water dịu lành từ Pháp.", "/uploads/products/bioderma-sensibio-500ml.jpg");
        Brand bCeraVe = initBrand("CeraVe", "cerave", "Chứa 3 Ceramides thiết yếu phục hồi hàng rào ẩm tự nhiên của da.", "/uploads/products/cerave-foaming-cleanser-473ml.jpg");
        Brand bMaybelline = initBrand("Maybelline New York", "maybelline", "Thương hiệu trang điểm hàng đầu thế giới từ New York năng động.", "/uploads/products/maybelline-sky-high-mascara.jpg");
        Brand b3CE = initBrand("3CE Stylenanda", "3ce-stylenanda", "Thương hiệu mỹ phẩm phong cách thời thượng trẻ trung Hàn Quốc.", "/uploads/products/3ce-velvet-lip-tint-daffodil.png");
        Brand bYSL = initBrand("Yves Saint Laurent", "ysl", "Thời trang và nước hoa Haute Couture cao cấp hàng đầu nước Pháp.", "/uploads/products/ysl-libre-edp-50ml.png");
        Brand bChanel = initBrand("Chanel", "chanel", "Biểu tượng thời trang và nước hoa xa xỉ trường tồn cùng thời gian.", "/uploads/products/chanel-coco-mademoiselle-edp-50ml.jpg");
        Brand bDior = initBrand("Dior", "dior", "Đẳng cấp nước hoa và mỹ phẩm xa xỉ bậc nhất từ thủ đô Paris.", "/uploads/products/dior-sauvage-edp-60ml.jpg");
        Brand bJoMalone = initBrand("Jo Malone London", "jo-malone-london", "Nhà hương phong cách quý tộc Anh Quốc thanh tao và trang nhã.", "/uploads/products/jo-malone-english-pear-freesia.jpg");
        Brand bMoroccanoil = initBrand("Moroccanoil", "moroccanoil", "Dầu dưỡng Argan trứ danh phục hồi mái tóc hư tổn chuẩn salon.", "/uploads/products/moroccanoil-treatment-100ml.jpg");
        Brand bTsubaki = initBrand("Tsubaki", "tsubaki", "Dưỡng tóc bóng mượt chuẩn salon Nhật Bản từ tinh dầu hoa trà đỏ.", "/uploads/products/tsubaki-premium-repair-shampoo.png");

        // 7. Initialize/Upsert 40 authentic cosmetics products across all categories with 100% exact local packshots
        log.info("Checking and seeding/upserting 40 accurate cosmetics products with 100% real product images, realistic prices, and branch inventories...");
        List<Branch> branches = List.of(b1, b2);
        List<Product> seededProducts = new ArrayList<>();
        seededProducts.add(upsertProduct(
                "LRP-ANT-001",
                "Kem Chống Nắng La Roche-Posay Anthelios UVMune 400 Oil Control 50ml",
                "kem-chong-nang-lrp-anthelios-50ml",
                "Kiểm soát bóng nhờn suốt 12 giờ, màng lọc độc quyền Mexoryl 400 bảo vệ da trước tia UVA siêu dài.",
                "Kem chống nắng La Roche-Posay Anthelios UVMune 400 Oil Control Gel-Cream là bước tiến đột phá trong công nghệ chống nắng với màng lọc độc quyền Mexoryl 400 bảo vệ tối ưu trước tia UVA siêu dài (380-400nm). Kết cấu dạng gel-cream khô ráo tức thì, không gây vệt trắng, kháng nước mồ hôi và kiềm dầu hiệu quả suốt cả ngày.",
                "Aqua/Water, Diisopropyl Sebacate, Silica, Alcohol Denat, Ethylhexyl Salicylate, Bis-Ethylhexyloxyphenol Methoxyphenyl Triazine, Ethylhexyl Triazone, Butyl Methoxydibenzoylmethane, Glycerin, Propanediol, C12-22 Alkyl Acrylate/Hydroxyethylacrylate Copolymer, Drometrizole Trisiloxane, Perlite, Tocopherol, Triethanolamine.",
                "Thoa đều khắp mặt và cổ trước khi ra ngoài 20 phút. Thoa lại sau mỗi 2-3 giờ khi tiếp xúc trực tiếp dưới ánh nắng mặt trời hoặc sau khi bơi lội, đổ mồ hôi nhiều.",
                BigDecimal.valueOf(420000),
                BigDecimal.valueOf(495000),
                catSkincare,
                bLRP,
                true,
                "/uploads/products/lrp-anthelios-50ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "PC-BHA-002",
                "Dung Dịch Tẩy Tế Bào Chết Paula's Choice Skin Perfecting 2% BHA Liquid 118ml",
                "dung-dich-tay-da-chet-paulas-choice-bha-2-percent-118ml",
                "Làm sạch sâu bã nhờn trong lỗ chân lông, loại bỏ mụn đầu đen và cải thiện bề mặt da mịn màng.",
                "Paula's Choice Skin Perfecting 2% BHA Liquid Exfoliant là sản phẩm tẩy tế bào chết hóa học toàn cầu với công thức 2% Salicylic Acid tan trong dầu. Thẩm thấu sâu vào từng nang lông để cuốn trôi dầu thừa tích tụ, giải quyết triệt để mụn cám, mụn đầu đen và thu nhỏ kích thước lỗ chân lông rõ rệt.",
                "Water (Aqua), Methylpropanediol, Butylene Glycol, Salicylic Acid (2%), Polysorbate 20, Camellia Oleifera (Green Tea) Leaf Extract, Sodium Hydroxide, Tetrasodium EDTA.",
                "Sử dụng 1-2 lần/ngày sau bước rửa mặt và cân bằng toner. Đổ lượng vừa đủ ra bông cotton hoặc lòng bàn tay thoa đều khắp mặt, không rửa lại. Ban ngày dùng kèm kem chống nắng SPF 30+ trở lên.",
                BigDecimal.valueOf(810000),
                BigDecimal.valueOf(949000),
                catSkincare,
                bPC,
                true,
                "/uploads/products/pc-bha-2-percent-118ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "KHL-CAL-004",
                "Kem Dưỡng Ngậm Nước Chiết Xuất Hoa Cúc Kiehl's Calendula Water Cream 50ml",
                "kem-duong-hoa-cuc-kiehls-calendula-50ml",
                "Ngậm nước mọng sương, phục hồi da mệt mỏi và làm dịu vùng da ửng đỏ tức thì.",
                "Kem dưỡng ngậm nước Kiehl's Calendula Serum-Infused Water Cream chứa tinh chất hoa cúc cô đặc và hàng trăm cánh hoa cúc nghiền mịn. Kết cấu mỏng nhẹ tan thành các giọt nước siêu nhỏ khi chạm vào da, thẩm thấu tức thì mang lại làn da căng mọng, rạng rỡ và dịu mát suốt 24 giờ.",
                "Aqua/Water, Dimethicone, Glycerin, Propylene Glycol, Isononyl Isononanoate, Calendula Officinalis Flower Extract, Calendula Officinalis Flower, Bisabolol, Sodium Chloride, Tocopherol.",
                "Lấy một lượng kem cỡ hạt đậu thoa đều lên da mặt và cổ đã làm sạch, vỗ nhẹ nhàng theo chuyển động tròn để kem tan thành hạt nước ngấm sâu vào biểu bì.",
                BigDecimal.valueOf(1350000),
                BigDecimal.valueOf(1500000),
                catSkincare,
                bKiehls,
                true,
                "/uploads/products/kiehls-calendula-water-cream-50ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "ORD-NIA-006",
                "Tinh Chất Giảm Mụn Mờ Thâm The Ordinary Niacinamide 10% + Zinc 1% 30ml",
                "tinh-chat-the-ordinary-niacinamide-10-zinc-1-30ml",
                "Kiềm dầu nhờn, làm dịu đốm mụn đỏ và hỗ trợ làm sáng đều màu da sau mụn.",
                "The Ordinary Niacinamide 10% + Zinc 1% kết hợp vitamin B3 nồng độ cao và muối kẽm của pyrrolidone carboxylic acid giúp cân bằng hoạt động điều tiết bã nhờn, làm thông thoáng lỗ chân lông và làm mờ các vết thâm sau mụn nhanh chóng mà không gây châm chích.",
                "Aqua (Water), Niacinamide (10%), Pentylene Glycol, Zinc PCA (1%), Dimethyl Isosorbide, Tamarindus Indica Seed Gum, Xanthan Gum, Isoceteth-20, Ethoxydiglycol, Phenoxyethanol, Chlorphenesin.",
                "Nhỏ 2-3 giọt serum lên toàn bộ mặt vào buổi sáng và buổi tối trước khi thoa kem dưỡng ẩm. Tránh sử dụng cùng lúc với các sản phẩm chứa Vitamin C nguyên chất (Ascorbic Acid).",
                BigDecimal.valueOf(210000),
                BigDecimal.valueOf(270000),
                catSkincare,
                bTheOrdinary,
                false,
                "/uploads/products/the-ordinary-niacinamide-30ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LRP-EFC-007",
                "Gel Rửa Mặt Tạo Bọt Cho Da Dầu Mụn La Roche-Posay Effaclar Purifying Foaming Gel 200ml",
                "gel-rua-mat-la-roche-posay-effaclar-200ml",
                "Làm sạch sâu bã nhờn mà không làm khô da, duy trì độ cân bằng pH 5.5 sinh lý.",
                "Sữa rửa mặt dạng gel tạo bọt nhẹ dịu La Roche-Posay Effaclar Gel giúp loại bỏ tạp chất và lượng bã nhờn dư thừa mà không phá vỡ lớp màng lipid bảo vệ da. Thành phần giàu nước khoáng La Roche-Posay Thermal Spring Water giúp làm dịu và kháng viêm hiệu quả.",
                "Aqua/Water, Sodium Laureth Sulfate, PEG-8, Coco-Betaine, Hexylene Glycol, Sodium Chloride, PEG-120 Methyl Glucose Dioleate, Zinc PCA, Sodium Hydroxide, Citric Acid, Sodium Benzoate, Phenoxyethanol, Caprylyl Glycol, Parfum/Fragrance.",
                "Lấy lượng gel cỡ hạt ngô, tạo bọt kỹ với nước ấm rồi thoa đều lên mặt. Mát-xa nhẹ nhàng theo chuyển động tròn trong khoảng 1 phút rồi rửa sạch với nước mát.",
                BigDecimal.valueOf(395000),
                BigDecimal.valueOf(450000),
                catSkincare,
                bLRP,
                false,
                "/uploads/products/lrp-effaclar-gel-200ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "KHL-TNR-013",
                "Nước Cân Bằng Hoa Cúc Cổ Điển Kiehl's Calendula Herbal-Extract Toner 250ml",
                "nuoc-can-bang-hoa-cuc-kiehls-calendula-250ml",
                "Công thức không cồn chứa những cánh hoa cúc tự nhiên làm dịu và cân bằng lượng dầu thừa.",
                "Kiehl's Calendula Herbal-Extract Alcohol-Free Toner là dòng nước cân bằng huyền thoại ra đời từ thập niên 1960. Những cánh hoa cúc tươi được chưng cất tự nhiên cùng chiết xuất ngưu bàng giúp cân bằng độ pH, làm dịu tức thì các nốt mụn viêm sưng và dưỡng ẩm êm ái.",
                "Aqua/Water, Pentylene Glycol, Methylpropanediol, Allantoin, Calendula Officinalis Flower Extract, Arctium Lappa Root Extract, Hedera Helix (Ivy) Extract, Althaea Officinalis Root Extract, Gentiana Lutea Root Extract.",
                "Sau bước rửa mặt, thấm đẫm toner ra bông tẩy trang rồi lau nhẹ theo chiều cấu trúc da. Có thể vỗ trực tiếp bằng tay hoặc đắp lotion mask làm dịu da trong 5 phút.",
                BigDecimal.valueOf(1150000),
                BigDecimal.valueOf(1300000),
                catSkincare,
                bKiehls,
                true,
                "/uploads/products/kiehls-calendula-toner-250ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LRP-B5-014",
                "Kem Dưỡng Phục Hồi Làm Dịu Da La Roche-Posay Cicaplast Baume B5+ 40ml",
                "kem-duong-phuc-hoi-lrp-cicaplast-baume-b5-40ml",
                "Tổ hợp Tribioma độc quyền, Panthenol 5% và Madecassoside phục hồi màng ẩm bảo vệ da sau 1 giờ.",
                "Cicaplast Baume B5+ phiên bản cải tiến bổ sung phức hợp men vi sinh Tribioma kết hợp cùng Panthenol 5% (Vitamin B5) và Madecassoside chiết xuất từ rau má. Giúp tăng tốc độ tái tạo biểu bì, giảm cảm giác ngứa rát, khô căng và làm lành thương tổn sau nặn mụn, laser, peel da.",
                "Aqua/Water, Hydrogenated Polyisobutene, Dimethicone, Glycerin, Butyrospermum Parkii Butter (Shea Butter), Panthenol, Zea Mays Starch, Propanediol, Butylene Glycol, Madecassoside, Zinc Gluconate, Madecassic Acid, Asiatic Acid.",
                "Thoa 2 lần mỗi ngày lên vùng da mặt hoặc cơ thể cần phục hồi sau khi làm sạch da. Thích hợp cho cả người lớn, trẻ em và trẻ sơ sinh.",
                BigDecimal.valueOf(365000),
                BigDecimal.valueOf(420000),
                catSkincare,
                bLRP,
                true,
                "/uploads/products/lrp-cicaplast-b5-plus-40ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LNG-CSK-015",
                "Nước Cân Bằng Dưỡng Ẩm Chuyên Sâu Laneige Cream Skin Cerapeptide Refiner 170ml",
                "nuoc-can-bang-duong-am-laneige-cream-skin-170ml",
                "Hòa tan nguyên hũ kem dưỡng vào chai toner, cấp ẩm tầng sâu và tăng sinh collagen 120 giờ.",
                "Laneige Cream Skin Cerapeptide Refiner ứng dụng công nghệ Micro Blending Technology độc quyền hòa tan trọn vẹn hũ kem dưỡng ẩm vào nước cân bằng. Phức hợp Cerapeptide bổ sung Ceramide củng cố hàng rào ẩm và Peptide tăng độ đàn hồi, ngăn ngừa khô ráp tức thì.",
                "Water/Aqua, Butylene Glycol, Glycerin, Limnanthes Alba (Meadowfoam) Seed Oil, Ceramide NP, Acetyl Tetrapeptide-11, Camellia Sinensis Leaf Extract, 1,2-Hexanediol, Ethylhexylglycerin, Disodium EDTA.",
                "Sử dụng vào buổi sáng và tối. Đổ lượng thích hợp ra lòng bàn tay và vỗ nhẹ lên khắp mặt cho đến khi tinh chất thẩm thấu sâu. Với da đặc biệt khô rát, có thể vỗ 2-3 lớp liên tiếp.",
                BigDecimal.valueOf(620000),
                BigDecimal.valueOf(720000),
                catSkincare,
                bLaneige,
                false,
                "/uploads/products/laneige-cream-skin-170ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "BIO-MIC-016",
                "Nước Tẩy Trang Làm Dịu Da Nhạy Cảm Bioderma Sensibio H2O Micellar Water 500ml",
                "nuoc-tay-trang-bioderma-sensibio-h2o-500ml",
                "Công nghệ Micellar số 1 thế giới làm sạch 99% bụi mịn và lớp trang điểm mà không cần rửa lại.",
                "Bioderma Sensibio H2O là dung dịch tẩy trang công nghệ hạt micelle đầu tiên và biểu tượng trên toàn cầu. Hạt micelle mô phỏng cấu trúc phospholipid màng tế bào tự nhiên, hút sạch cặn trang điểm và bụi bẩn PM 2.5 mà không gây tổn hại hàng rào bảo vệ da, đồng thời làm dịu vùng da nhạy cảm đỏ rát.",
                "Aqua/Water, PEG-6 Caprylic/Capric Glycerides, Fructooligosaccharides, Mannitol, Xylitol, Rhamnose, Cucumis Sativus (Cucumber) Fruit Extract, Propylene Glycol, Cetrimonium Bromide, Disodium EDTA.",
                "Thấm ướt bông cotton bằng nước tẩy trang Bioderma Sensibio H2O. Nhẹ nhàng lau khắp mặt và vùng mắt môi cho đến khi bông sạch hoàn toàn. Không bắt buộc phải rửa lại với nước.",
                BigDecimal.valueOf(425000),
                BigDecimal.valueOf(495000),
                catSkincare,
                bBioderma,
                true,
                "/uploads/products/bioderma-sensibio-500ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "CRV-CLN-017",
                "Sữa Rửa Mặt Cho Da Dầu Mụn CeraVe Foaming Cleanser 473ml",
                "sua-rua-mat-cho-da-dau-mun-cerave-foaming-cleanser-473ml",
                "Bổ sung 3 Ceramides thiết yếu, Niacinamide và Axit Hyaluronic làm sạch êm dịu không nhờn dính.",
                "CeraVe Foaming Cleanser được phát triển bởi các bác sĩ da liễu Hoa Kỳ dành riêng cho da thường đến da dầu. Công thức tạo bọt êm ái chứa 3 Ceramide thiết yếu (1, 3, 6-II), Niacinamide và Hyaluronic Acid giúp loại bỏ bã nhờn sâu mà không làm mất đi độ ẩm tự nhiên của lớp biểu bì.",
                "Aqua/Water, Cocamidopropyl Hydroxysultaine, Glycerin, Sodium Lauroyl Sarcosinate, PEG-150 Pentaerythrityl Tetrastearate, Niacinamide, Ceramide NP, Ceramide AP, Ceramide EOP, Carbomer, Sodium Hyaluronate, Cholesterol, Phenoxyethanol, Disodium EDTA.",
                "Làm ướt da bằng nước ấm. Bơm sữa rửa mặt ra tay và xoa tạo bọt nhẹ, mát-xa đều trên khuôn mặt theo chuyển động vòng tròn khoảng 30-60 giây rồi rửa sạch bằng nước ấm.",
                BigDecimal.valueOf(390000),
                BigDecimal.valueOf(450000),
                catSkincare,
                bCeraVe,
                false,
                "/uploads/products/cerave-foaming-cleanser-473ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "KHL-DRK-018",
                "Tinh Chất Làm Sáng Da Mờ Thâm Kiehl's Clearly Corrective Dark Spot Solution 30ml",
                "tinh-chat-mo-tham-sang-da-kiehls-dark-spot-30ml",
                "Vitamin C thế hệ mới kết hợp chiết xuất bạch dương trắng mờ thâm mụn và dưỡng sáng trong suốt.",
                "Kiehl's Clearly Corrective Dark Spot Solution là tinh chất làm sáng da bán chạy số 1 của Kiehl's. Hoạt chất Activated C (dẫn xuất Vitamin C ổn định cao) thẩm thấu nhanh cùng tinh chất Bạch Dương Trắng và Hoa Mẫu Đơn giúp giảm thiểu đốm nâu, vết thâm mụn sạm màu và nâng tông da rạng ngời chỉ sau 2 tuần.",
                "Aqua/Water, Propylene Glycol, Glycerin, Alcohol Denat, PEG/PPG/Polybutylene Glycol-8/5/3 Glycerin, Hydroxypropyl Tetrahydropyrantriol, 3-O-Ethyl Ascorbic Acid, PPG-6-Decyltetradeceth-30, Salicylic Acid, Betula Alba Bark Extract, Paeonia Suffruticosa Root Extract.",
                "Nhỏ vài giọt ra lòng bàn tay thoa đều khắp mặt hoặc chấm trực tiếp lên các nốt thâm mụn, nám sạm. Dùng đều đặn mỗi sáng và tối trước khi thoa kem dưỡng ẩm.",
                BigDecimal.valueOf(1750000),
                BigDecimal.valueOf(1950000),
                catSkincare,
                bKiehls,
                true,
                "/uploads/products/kiehls-dark-spot-solution-30ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "PC-RES-019",
                "Tinh Chất Thu Nhỏ Lỗ Chân Lông Paula's Choice Clinical Niacinamide 20% Treatment 20ml",
                "tinh-chat-thu-nho-lo-chan-long-paulas-choice-niacinamide-20-percent",
                "Nồng độ Niacinamide 20% đậm đặc đặc trị lỗ chân lông thô to, da sần vỏ cam và sạm màu.",
                "Paula's Choice Clinical Niacinamide 20% Treatment là sản phẩm đột phá chứa nồng độ Vitamin B3 tinh khiết tối đa giúp cải thiện cấu trúc da lão hóa sần sùi, phục hồi độ đàn hồi quanh thành lỗ chân lông và làm phẳng bề mặt da bị tổn thương do ánh nắng mặt trời.",
                "Water (Aqua), Niacinamide (20%), Pentylene Glycol, Butylene Glycol, Glycerin, Acetyl Glucosamine, Ascorbyl Glucoside, Glycyrrhiza Glabra (Licorice) Root Extract, Spiraea Ulmaria Extract, Camellia Japonica Flower Extract, Sodium Hyaluronate.",
                "Sử dụng 1 lần/ngày vào buổi tối sau bước làm sạch và tẩy tế bào chết BHA. Nhỏ 2-3 giọt xoa đều khắp mặt và tiếp tục các bước dưỡng ẩm tiếp theo.",
                BigDecimal.valueOf(1450000),
                BigDecimal.valueOf(1690000),
                catSkincare,
                bPC,
                false,
                "/uploads/products/pc-niacinamide-20-percent-20ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "INN-CSH-008",
                "Phấn Nước Kiềm Dầu Mịn Màng Innisfree No-Sebum Powder Cushion SPF35 PA++ 14g",
                "phan-nuoc-kiem-dau-innisfree-no-sebum-cushion",
                "Che phủ khuyết điểm hoàn hảo, mỏng nhẹ thoáng da suốt 48 giờ với khoáng chất đảo Jeju.",
                "Innisfree No-Sebum Powder Cushion thừa hưởng công nghệ kiềm dầu huyền thoại từ dòng No-Sebum. Đem đến hiệu ứng lớp nền lì mờ tự nhiên, che phủ 95% lỗ chân lông to và đốm đỏ, duy trì lớp nền tươi tắn suốt cả ngày dài năng động mà không gây bí tắc mụn.",
                "Water/Aqua, Titanium Dioxide (CI 77891), Cyclopentasiloxane, Ethylhexyl Methoxycinnamate, Diphenylsiloxy Phenyl Trimethicone, Silica, Zinc Oxide, Iron Oxides (CI 77492), Butylene Glycol, Dimethicone, Camellia Sinensis Leaf Extract.",
                "Dùng bông phấn đính kèm ấn nhẹ vào lõi đệm để lấy lượng phấn vừa đủ, sau đó dặm đều từ trung tâm khuôn mặt ra ngoài viền khuôn mặt.",
                BigDecimal.valueOf(450000),
                BigDecimal.valueOf(520000),
                catMakeup,
                bInnisfree,
                false,
                "/uploads/products/innisfree-cushion-no-sebum.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LOR-INF-020",
                "Kem Nền Lâu Trôi 32H L'Oréal Paris Infallible 32H Fresh Wear Foundation 30ml",
                "kem-nen-lau-troi-loreal-infallible-32h-fresh-wear-30ml",
                "Bền màu 32 giờ, kháng nước mồ hôi với công nghệ co giãn thoáng khí hạt phấn siêu mịn.",
                "L'Oréal Paris Infallible 32H Fresh Wear Foundation là dòng kem nền liquid bán chạy toàn cầu với công nghệ che phủ cao nhưng cực kỳ thoáng khí nhờ màng vi tinh thể Oxygen. Chống trôi, chống lem khẩu trang, kiểm soát dầu thừa và bổ sung Vitamin C giúp làm sáng da sau mỗi lần trang điểm.",
                "Aqua/Water, Dimethicone, Isododecane, Alcohol Denat, Ethylhexyl Methoxycinnamate, Trimethylsiloxysilicate, Butylene Glycol, PEG-10 Dimethicone, Perlite, Ascorbyl Glucoside, Magnesium Sulfate, Silica Silylate, Iron Oxides.",
                "Lắc đều lọ trước khi dùng. Nhấn 1 lần vòi xịt ra mu bàn tay, dùng cọ nền hoặc mút trang điểm ẩm tán đều từ giữa mặt ra các phía để đạt độ che phủ như ý.",
                BigDecimal.valueOf(299000),
                BigDecimal.valueOf(359000),
                catMakeup,
                bLoreal,
                false,
                "/uploads/products/loreal-infallible-32h-30ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LOR-TRM-021",
                "Kem Nền Mịn Nhẹ Tiệp Màu Da L'Oréal Paris True Match Foundation 30ml",
                "kem-nen-tiep-mau-da-loreal-true-match-liquid-30ml",
                "Hòa quyện hoàn hảo với tông da Á Đông, bổ sung Hyaluronic Acid cấp ẩm mướt mịn cả ngày.",
                "Kem nền thế hệ mới L'Oréal True Match sở hữu công nghệ vi phân tử hạt màu siêu mịn tiệp chính xác với tông da và phân tông dưới da của người phụ nữ Châu Á. Tinh chất Axit Hyaluronic 1% giúp cấp ẩm căng mọng, không vỡ nền hay lộ vân phấn sau nhiều giờ hoạt động.",
                "Aqua/Water, Dimethicone, Isododecane, Cyclohexasiloxane, Glycerin, PEG-10 Dimethicone, Methyl Methacrylate Crosspolymer, Sodium Hyaluronate, Panthenol, Tocopherol, Iron Oxides.",
                "Lấy một lượng kem nền vừa phải chấm đều lên 5 điểm trên khuôn mặt (trán, mũi, hai má, cằm), dùng bông mút tán đều nhẹ nhàng theo chiều hướng lên trên.",
                BigDecimal.valueOf(279000),
                BigDecimal.valueOf(329000),
                catMakeup,
                bLoreal,
                false,
                "/uploads/products/loreal-true-match-30ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "INN-PWD-022",
                "Phấn Phủ Bột Kiềm Dầu Innisfree No-Sebum Mineral Powder 5g",
                "phan-phu-bot-kiem-dau-innisfree-no-sebum-mineral-powder-5g",
                "Hút sạch bã nhờn dư thừa, giữ lớp trang điểm thông thoáng mờ lì với khoáng chất tự nhiên.",
                "Innisfree No-Sebum Mineral Powder là hũ phấn phủ quốc dân được phái đẹp yêu thích suốt nhiều năm qua. Chứa các hạt bột khoáng chất từ đảo núi lửa Jeju cùng chiết xuất bạc hà tươi mát giúp hút sạch mồ hôi và bã nhờn, tạo lớp khóa nền trong suốt tự nhiên không màu.",
                "Silica, Aluminum Starch Octenylsuccinate, Dimethicone/Vinyl Dimethicone Crosspolymer, Caprylic/Capric Triglyceride, Mica, Methicone, Mineral Salts, Mentha Arvensis Leaf Extract, 1,2-Hexanediol.",
                "Dùng bông phấn đính kèm vỗ nhẹ lớp phấn lên vùng chữ T, cánh mũi và trán sau khi hoàn thành lớp nền trang điểm. Có thể dùng dặm lại trong ngày khi da tiết dầu.",
                BigDecimal.valueOf(135000),
                BigDecimal.valueOf(160000),
                catMakeup,
                bInnisfree,
                false,
                "/uploads/products/innisfree-no-sebum-powder-5g.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LNG-NEO-023",
                "Phấn Nước Siêu Mịn Lì Chống Trôi Laneige Neo Cushion Matte SPF42 PA++ 15g",
                "phan-nuoc-laneige-neo-cushion-matte-spf42-15g",
                "Che phủ khuyết điểm siêu mịn 24 giờ, chống trôi lem vào khẩu trang và kháng ánh sáng xanh.",
                "Laneige Neo Cushion Matte sở hữu thiết kế hình hộp bo tròn thời thượng lấy cảm hứng từ tai nghe công nghệ. Hạt phấn siêu nhỏ che phủ hoàn hảo các nốt mụn đỏ, tàn nhang và vết thâm mà nhẹ như không khí, kiểm soát bã nhờn và bảo vệ làn da trước tác hại của ánh sáng xanh từ màn hình máy tính.",
                "Water/Aqua, Titanium Dioxide, Methyl Trimethicone, Homosalate, Caprylyl Methicone, PEG-10 Dimethicone, Polymethyl Methacrylate, Niacinamide, Panthenol, Camellia Sinensis Leaf Extract, Tocopherol.",
                "Dùng bông mút có khe thoát khí chuyên dụng chấm một lượng cushion vừa đủ và dặm đều khắp khuôn mặt, chú ý dặm kỹ hơn tại vùng da có nhiều khuyết điểm.",
                BigDecimal.valueOf(680000),
                BigDecimal.valueOf(790000),
                catMakeup,
                bLaneige,
                true,
                "/uploads/products/laneige-neo-cushion-matte.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MAC-SFX-024",
                "Phấn Nền Đa Năng M·A·C Studio Fix Powder Plus Foundation 15g",
                "phan-nen-da-nang-mac-studio-fix-powder-plus-foundation",
                "Kết hợp kem nền và phấn phủ trong 1 bước, kiềm dầu che phủ hoàn mỹ chuẩn studio chuyên nghiệp.",
                "M·A·C Studio Fix Powder Plus Foundation là sản phẩm trang điểm mang tính biểu tượng của giới nghệ sĩ makeup chuyên nghiệp thế giới. Kết cấu bột nén siêu mịn tích hợp độ che phủ trung bình đến toàn diện, làm mờ lỗ chân lông và kiềm dầu lên đến 12 giờ mà không gây mốc cakey.",
                "Talc, Silica, Isostearyl Neopentanoate, Dimethicone, Zinc Stearate, Polyethylene, Octyldodecyl Stearoyl Stearate, Phenoxyethanol, Iron Oxides (CI 77491, CI 77492, CI 77499).",
                "Dùng bông phấn đính kèm để đạt độ che phủ cao hoặc dùng cọ phấn phủ lông tơi vỗ nhẹ khắp mặt để tạo hiệu ứng tự nhiên, nhẹ nhàng hàng ngày.",
                BigDecimal.valueOf(1050000),
                BigDecimal.valueOf(1200000),
                catMakeup,
                bMAC,
                true,
                "/uploads/products/mac-studio-fix-powder-plus.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MAC-FIX-025",
                "Xịt Khoá Nền Cố Định Lớp Trang Điểm M·A·C Prep + Prime Fix+ 100ml",
                "xit-khoa-nen-co-dinh-trang-diem-mac-prep-prime-fix-plus-100ml",
                "Cấp ẩm dồi dào, cố định lớp makeup bền đẹp 12 giờ và loại bỏ tình trạng nền bị mốc bở.",
                "M·A·C Prep + Prime Fix+ là xịt khoáng dưỡng ẩm làm mềm và khóa chặt lớp trang điểm trứ danh. Chứa các chiết xuất từ trà xanh, hoa cúc, dưa leo cùng Vitamin E và khoáng chất giúp hồi sinh làn da khô mệt mỏi, làm hòa quyện các tầng phấn tạo hiệu ứng làn da thủy tinh mướt rượt.",
                "Water/Aqua, Glycerin, Butylene Glycol, Cucumis Sativus (Cucumber) Fruit Extract, Chamomilla Recutita (Matricaria) Extract, Camellia Sinensis Leaf Extract, Tocopheryl Acetate, Caffeine, Panthenol, Phenoxyethanol.",
                "Giữ chai xịt cách mặt 20-30cm, nhắm mắt và xịt đều theo hình chữ X và chữ T sau khi trang điểm xong. Có thể xịt lên cọ trước khi lấy phấn mắt để màu lên đậm rõ nét hơn.",
                BigDecimal.valueOf(850000),
                BigDecimal.valueOf(990000),
                catMakeup,
                bMAC,
                false,
                "/uploads/products/mac-prep-prime-fix-plus.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MAY-SKY-026",
                "Chuốt Mi Dài Mi Cong Vút Chống Nước Maybelline Lash Sensational Sky High Mascara 6ml",
                "chuot-mi-maybelline-lash-sensational-sky-high-mascara-6ml",
                "Đầu chổi Flex Tower linh hoạt chạm đến từng sợi mi, mi dài cong vút bất chấp mưa rơi hay nước mắt.",
                "Maybelline New York Lash Sensational Sky High Mascara tạo nên cơn sốt toàn cầu với khả năng kéo dài từng sợi mi từ gốc đến ngọn. Công thức bổ sung chiết xuất tre và các vi sợi tơ giúp nuôi dưỡng mi khỏe mạnh, giữ nếp cong vút cả ngày mà hoàn toàn không vón cục hay nhòe đen.",
                "Isododecane, Cera Alba/Beeswax, Copernicia Cerifera Cera/Carnauba Wax, Disteardimonium Hectorite, Aqua/Water, Allyl Stearate/VA Copolymer, Oryza Sativa Cera/Rice Bran Wax, Paraffin, Bambusa Vulgaris Extract, Iron Oxides.",
                "Chải mascara từ chân mi theo chuyển động zíc-zắc lên tới ngọn. Chải thêm 1-2 lớp trước khi mascara khô hẳn để tăng độ dài và độ dày mong muốn.",
                BigDecimal.valueOf(195000),
                BigDecimal.valueOf(245000),
                catMakeup,
                bMaybelline,
                false,
                "/uploads/products/maybelline-sky-high-mascara.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MAC-LIP-003",
                "Son Thỏi M·A·C Matte Lipstick - Màu 602 Chili Đỏ Tương Ớt 3g",
                "son-thoi-mac-matte-chili-602",
                "Tone đỏ gạch pha cam nâu trầm quyến rũ, chất son matte mịn mượt chuẩn màu tôn da.",
                "M·A·C Matte Lipstick Chili 602 là thỏi son có doanh số kỷ lục trong lịch sử thương hiệu M·A·C. Sắc son đỏ gạch pha chút cam cháy và nâu đất tôn sáng mọi màu da phụ nữ Việt, chất son lì mịn như nhung bám màu 8-10 giờ mà không làm thâm khô viền môi.",
                "Dimethicone, Isoeicosane, Kaolin, Ceresin, Euphorbia Cerifera (Candelilla) Wax, Tocopherol, Ascorbyl Palmitate, Red 7 Lake (CI 15850), Iron Oxides (CI 77491, CI 77492, CI 77499).",
                "Thoa trực tiếp lên môi từ lòng môi ra khóe môi hoặc dùng cọ kẻ viền môi để đường kẻ sắc nét. Có thể thoa một lớp mỏng son dưỡng trước khi tô son lì.",
                BigDecimal.valueOf(590000),
                BigDecimal.valueOf(690000),
                catLipstick,
                bMAC,
                true,
                "/uploads/products/mac-matte-lipstick-chili.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LNG-LIP-005",
                "Mặt Nạ Ngủ Môi Hương Quả Mọng Laneige Lip Sleeping Mask Berry 20g",
                "mat-na-ngu-moi-laneige-berry-20g",
                "Hồi sinh đôi môi bong tróc khô ráp, cấp ẩm dồi dào cho bờ môi hồng hào căng mọng sau một đêm.",
                "Laneige Lip Sleeping Mask Berry giàu Vitamin C và chất chống oxy hóa từ tổ hợp quả mọng Berry Mix Complex (dâu tây, mâm xôi, việt quất). Công nghệ Moisture Wrap khóa ẩm độc quyền nhẹ nhàng làm tan tế bào da chết trên môi suốt đêm dài, mang lại bờ môi mềm mượt mịn màng vào sớm hôm sau.",
                "Diisostearyl Malate, Hydrogenated Polyisobutene, Phytosteryl/Isostearyl/Cetyl/Stearyl/Behenyl Dimer Dilinoleate, Polybutene, Butyrospermum Parkii (Shea) Butter, Synthetic Wax, Fragaria Chiloensis (Strawberry) Fruit Extract, Rubus Idaeus (Raspberry) Fruit Extract, Lycium Chinense Fruit Extract, Ascorbic Acid.",
                "Trước khi đi ngủ, dùng thìa đính kèm thoa một lớp dày lên toàn bộ môi. Sáng thức dậy lau nhẹ bằng khăn giấy mềm hoặc bông tẩy trang ẩm.",
                BigDecimal.valueOf(380000),
                BigDecimal.valueOf(450000),
                catLipstick,
                bLaneige,
                true,
                "/uploads/products/laneige-lip-sleeping-mask-berry.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MAC-PKL-011",
                "Son Kem Lì M·A·C Powder Kiss Liquid Lipcolour - Màu 991 Devoted To Chili 5ml",
                "son-kem-mac-powder-kiss-991-devoted-to-chili",
                "Hiệu ứng mịn màng như nhung sương mờ, sắc đỏ gạch thời thượng không làm lộ vân môi.",
                "M·A·C Powder Kiss Liquid Lipcolour tái định nghĩa khái niệm son kem lì với chất son mềm xốp bông mịn tựa đám mây. Công thức chứa các hạt vi mô tán đều tạo hiệu ứng mờ lì ảo diệu, độ dưỡng ẩm cao giúp môi luôn ẩm mượt và bền màu kéo dài đến 10 giờ.",
                "Dimethicone, Dimethicone Crosspolymer, Isononyl Isononanoate, Polyglyceryl-2 Triisostearate, Diisostearyl Malate, Red 7 Lake (CI 15850), Iron Oxides (CI 77491), Tocopheryl Acetate.",
                "Dùng đầu cọ son hình giọt nước gạt bớt son thừa, thoa trực tiếp lên lòng môi rồi dùng đầu ngón tay hoặc bặm môi nhẹ nhàng tán đều ra viền môi.",
                BigDecimal.valueOf(650000),
                BigDecimal.valueOf(750000),
                catLipstick,
                bMAC,
                false,
                "/uploads/products/mac-devoted-to-chili-liquid.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MAC-RBY-027",
                "Son Thỏi Lì Cổ Điển M·A·C Retro Matte Lipstick - Màu 707 Ruby Woo Đỏ Thuần 3g",
                "son-thoi-mac-retro-matte-ruby-woo-707",
                "Sắc đỏ thuần cổ điển kiêu kỳ với chất son siêu lì đỉnh cao chuẩn mực của mọi thời đại.",
                "M·A·C Retro Matte Ruby Woo 707 là thỏi son đỏ quyền lực nhất của làng mỹ phẩm thế giới. Sắc đỏ thuần base xanh huyền thoại giúp làm bừng sáng khuôn mặt và hàm răng trắng tinh tế, tạo nên vẻ đẹp sang trọng, đẳng cấp và quyến rũ không thể nhầm lẫn.",
                "Dimethicone, Isoeicosane, Kaolin, Ceresin, Euphorbia Cerifera (Candelilla) Wax, Silica, Red 7 Lake (CI 15850), Red 6 (CI 15850), Yellow 5 Lake (CI 19140), Tocopherol.",
                "Dưỡng môi thật kỹ trước khi thoa. Đánh lòng môi tạo vẻ tươi tắn ngọt ngào hoặc đánh full môi viền sắc sảo để tham gia các buổi tiệc dạ hội trang trọng.",
                BigDecimal.valueOf(590000),
                BigDecimal.valueOf(690000),
                catLipstick,
                bMAC,
                true,
                "/uploads/products/mac-retro-matte-ruby-woo.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LNG-GLW-028",
                "Son Dưỡng Bóng Căng Mọng Môi Laneige Lip Glowy Balm Berry 10g",
                "son-duong-bong-laneige-lip-glowy-balm-berry-10g",
                "Dưỡng ẩm tức thì dạng tuýp tiện lợi, tạo hiệu ứng bờ môi căng mọng óng ánh ánh quả mọng.",
                "Laneige Lip Glowy Balm là người bạn đồng hành hoàn hảo ban ngày cho dòng mặt nạ ngủ môi. Chiết xuất bơ Murumuru và bơ hạt mỡ khóa ẩm sâu, tạo lớp bóng trong trẻo tự nhiên mà không gây dính nhớp, đem lại đôi môi tươi hồng tự nhiên đầy sức sống.",
                "Hydrogenated Polyisobutene, Phytosteryl/Isostearyl/Cetyl/Stearyl/Behenyl Dimer Dilinoleate, Astrocaryum Murumuru Seed Butter, Butyrospermum Parkii (Shea) Butter, Fragrance/Parfum, Tocopherol.",
                "Bóp nhẹ tuýp son thoa trực tiếp lên môi khi cảm thấy môi bị khô căng, hoặc thoa đè lên lớp son màu lì để tạo hiệu ứng môi mọng nước quyến rũ.",
                BigDecimal.valueOf(220000),
                BigDecimal.valueOf(260000),
                catLipstick,
                bLaneige,
                false,
                "/uploads/products/laneige-lip-glowy-balm-berry.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "3CE-VLV-029",
                "Son Kem Lì 3CE Velvet Lip Tint - Màu Daffodil Đỏ Đất Thời Thượng 4g",
                "son-kem-li-3ce-velvet-lip-tint-daffodil",
                "Sắc đỏ đất hài hòa nữ tính, chất son kem nhung mịn xốp che phủ hoàn hảo khuyết điểm môi.",
                "3CE Velvet Lip Tint màu Daffodil là sự kết hợp tinh tế giữa sắc đỏ quyến rũ và tông đất trầm ấm nhẹ nhàng. Chất son kem xốp mịn trải đều lấp đầy các rãnh môi mà không làm khô căng, mang đến vẻ đẹp đằm thắm, thanh lịch và vô cùng cuốn hút.",
                "Dimethicone, Cyclopentasiloxane, Dimethicone Crosspolymer, Dimethicone/Vinyl Dimethicone Crosspolymer, Polyglyceryl-2 Triisostearate, Isododecane, Iron Oxides (CI 77491), Red 7 Lake (CI 15850).",
                "Gạt nhẹ đầu cọ lấy một lượng son vừa phải, thoa vào giữa lòng môi rồi dùng cọ tán đều ra ngoài rìa môi để tạo hiệu ứng ombre thanh thoát.",
                BigDecimal.valueOf(285000),
                BigDecimal.valueOf(340000),
                catLipstick,
                b3CE,
                true,
                "/uploads/products/3ce-velvet-lip-tint-daffodil.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "3CE-CLD-030",
                "Son Kem Mịn Lì Như Mây 3CE Cloud Lip Tint - Màu Macaron Red Đỏ Tươi 4g",
                "son-kem-3ce-cloud-lip-tint-macaron-red",
                "Kết cấu mỏng nhẹ bồng bềnh như mây, màu đỏ tươi rực rỡ bám màu bền bỉ suốt 8 tiếng.",
                "3CE Cloud Lip Tint Macaron Red là màu son đỏ thuần cổ điển rạng ngời giúp tôn da và làm trắng răng tức thì. Công thức giọt nước mềm mịn lướt êm trên bờ môi, khô lại thành lớp màng nhung lì không gây khô tróc, hạn chế bám dính vào thành cốc khi uống nước.",
                "Dimethicone, Aqua/Water, Polyamide-8, Polyglyceryl-2 Triisostearate, Glycerin, Silica, Red 28 Lake (CI 45410), Red 7 Lake (CI 15850), Iron Oxides, Tocopherol.",
                "Thoa một lượng nhỏ vào giữa môi rồi bặm môi nhẹ. Có thể dặm thêm lớp thứ 2 để sắc son đỏ lên đậm màu và sắc sảo nhất.",
                BigDecimal.valueOf(310000),
                BigDecimal.valueOf(360000),
                catLipstick,
                b3CE,
                false,
                "/uploads/products/3ce-cloud-lip-tint-macaron-red.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "3CE-SFT-031",
                "Son Thỏi Lì Mịn Môi 3CE Soft Matte Lipstick - Màu Over It Cam San Hô 3.5g",
                "son-thoi-li-3ce-soft-matte-lipstick-over-it",
                "Tông cam san hô rạng rỡ trẻ trung, vỏ son trong suốt tinh tế cùng chất son lì siêu nhẹ.",
                "3CE Soft Matte Lipstick Over It mang sắc cam san hô pha chút ấm áp của nắng hè, mang đến diện mạo tươi mới và tràn đầy năng lượng cho các bạn gái trẻ. Thiết kế thân son trong suốt nhìn xuyên thấu độc đáo kết hợp cùng chất son mịn như tơ tạo cảm giác dễ chịu cả ngày.",
                "Dimethicone, Isoamyl Laurate, Polyglyceryl-2 Triisostearate, Diphenyl Dimethicone/Vinyl Diphenyl Dimethicone/Silsesquioxane Crosspolymer, Iron Oxides (CI 77491), Yellow 6 Lake (CI 15985).",
                "Thoa nhẹ nhàng từ lòng môi ra viền ngoài. Có thể kết hợp cùng phấn má hồng cùng tông để hoàn thiện phong cách trang điểm Hàn Quốc tự nhiên.",
                BigDecimal.valueOf(330000),
                BigDecimal.valueOf(390000),
                catLipstick,
                b3CE,
                false,
                "/uploads/products/3ce-soft-matte-lipstick-over-it.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "INN-BDY-012",
                "Sữa Dưỡng Thể Chiết Xuất Trà Xanh Innisfree Green Tea Pure Body Lotion 300ml",
                "sua-duong-the-innisfree-green-tea-300ml",
                "Cấp nước sâu cho da cơ thể khô ráp với 16 loại axit amin từ lá trà xanh đảo Jeju.",
                "Innisfree Green Tea Pure Body Lotion chứa nguồn dưỡng chất dồi dào từ lá trà xanh tươi hữu cơ được trồng tại đảo Jeju. Kết cấu sữa dưỡng dạng lotion mỏng nhẹ, thấm nhanh trong 10 giây mà không hề gây nhờn rít, lưu lại hương thơm trà xanh thanh khiết, thư giãn.",
                "Water/Aqua, Glycerin, Butylene Glycol, Camellia Sinensis Leaf Extract (Trà xanh Jeju), Cetyl Ethylhexanoate, Butyrospermum Parkii (Shea) Butter, Glyceryl Stearate, Dimethicone, Tocopherol.",
                "Sau khi tắm sạch và lau khô cơ thể bằng khăn mềm, lấy một lượng sữa dưỡng thoa đều lên toàn thân và mát-xa nhẹ nhàng theo hình xoắn ốc để dưỡng chất thấm sâu.",
                BigDecimal.valueOf(290000),
                BigDecimal.valueOf(350000),
                catBodycare,
                bInnisfree,
                false,
                "/uploads/products/innisfree-green-tea-body-lotion.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "PC-BDY-032",
                "Kem Dưỡng Thể Giảm Viêm Chân Lông Paula's Choice Weightless Body Treatment 2% BHA 210ml",
                "kem-duong-the-giam-viem-nang-long-paulas-choice-2-percent-bha",
                "Tẩy tế bào chết cơ thể 2% Salicylic Acid điều trị mụn lưng, viêm nang lông và da sần sùi.",
                "Paula's Choice Weightless Body Treatment 2% BHA là giải pháp chăm sóc da cơ thể đặc trị số 1 hiện nay. Với 2% BHA dịu nhẹ, sản phẩm làm mềm và loại bỏ lớp sừng dày cộm ở đầu gối, khuỷu tay, làm thông thoáng lỗ chân lông trên lưng và ngực, điều trị dứt điểm tình trạng dày sừng nang lông.",
                "Water (Aqua), Butylene Glycol, Cetyl Alcohol, PPG-14 Butyl Ether, Salicylic Acid (2%), Tocopheryl Acetate, Chamomilla Recutita (Matricaria) Flower Extract, Epilobium Angustifolium Extract, Bisabolol, Sodium Hydroxide.",
                "Thoa một lượng nhỏ lên vùng da cơ thể bị mụn lưng, viêm nang lông hoặc sần sùi sau khi tắm sạch. Có thể sử dụng hàng ngày hoặc 2-3 lần/tuần tùy nhu cầu da.",
                BigDecimal.valueOf(850000),
                BigDecimal.valueOf(990000),
                catBodycare,
                bPC,
                true,
                "/uploads/products/pc-bha-body-treatment-210ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "CRV-BDY-033",
                "Kem Dưỡng Ẩm Toàn Thân Cho Da Khô Đến Rất Khô CeraVe Moisturizing Cream 454g",
                "kem-duong-am-toan-than-cerave-moisturizing-cream-454g",
                "Công nghệ MVE nhả chậm dưỡng chất 24 giờ cùng 3 Ceramides phục hồi da nứt nẻ bong tróc.",
                "CeraVe Moisturizing Cream hũ 454g là sản phẩm dưỡng ẩm thể dịu lành được các hiệp hội da liễu chứng nhận. Ứng dụng công nghệ MVE phân phối đa tầng giúp giải phóng các phân tử Ceramide và Hyaluronic Acid thẩm thấu liên tục suốt 24 giờ, làm mềm dịu những vùng da khô nứt nẻ nghiêm trọng nhất.",
                "Aqua/Water, Glycerin, Cetearyl Alcohol, Caprylic/Capric Triglyceride, Cetyl Alcohol, Ceteareth-20, Petrolatum, Potassium Phosphate, Ceramide NP, Ceramide AP, Ceramide EOP, Carbomer, Sodium Hyaluronate, Cholesterol, Phenoxyethanol.",
                "Thoa đều kem lên toàn bộ vùng da khô trên cơ thể hoặc khuôn mặt bất cứ khi nào cảm thấy khô căng hoặc sau khi tắm.",
                BigDecimal.valueOf(420000),
                BigDecimal.valueOf(490000),
                catBodycare,
                bCeraVe,
                false,
                "/uploads/products/cerave-moisturizing-cream-454g.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "INN-WSH-034",
                "Sữa Tắm Làm Sạch Sâu Dịu Nhẹ Hương Trà Xanh Innisfree Green Tea Body Cleanser 310ml",
                "sua-tam-tra-xanh-innisfree-green-tea-body-cleanser-310ml",
                "Tạo bọt dày mịn xốp làm sạch sâu bụi bẩn, ngát hương trà xanh thư giãn tinh thần.",
                "Innisfree Green Tea Body Cleanser là sữa tắm thiên nhiên dịu lành với nguồn nước trà xanh nguyên chất ép tươi từ đảo Jeju. Khả năng tạo lớp bọt xốp dày đặc giúp cuốn trôi bụi bẩn và mồ hôi sau một ngày dài làm việc, giữ cho làn da luôn ẩm mượt, thông thoáng và tỏa ngát hương trà xanh sảng khoái.",
                "Water/Aqua, Sodium Laureth Sulfate, Glycerin, Cocamidopropyl Betaine, Camellia Sinensis Leaf Extract (Trà xanh), Sodium Chloride, Disodium EDTA, Citric Acid, Fragrance/Parfum.",
                "Lấy một lượng sữa tắm vừa đủ ra bông tắm hoặc lòng bàn tay, xoa tạo bọt kỹ rồi mát-xa nhẹ nhàng khắp cơ thể, sau đó tắm lại sạch với nước mát.",
                BigDecimal.valueOf(260000),
                BigDecimal.valueOf(310000),
                catBodycare,
                bInnisfree,
                false,
                "/uploads/products/innisfree-green-tea-body-cleanser.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "PRF-LIB-009",
                "Nước Hoa Nữ Cao Cấp Yves Saint Laurent YSL Libre Eau De Parfum 50ml",
                "nuoc-hoa-nu-ysl-libre-edp-50ml",
                "Hương hoa oải hương Pháp kết hợp hoa cam Ma-rốc kiêu kỳ, biểu tượng của sự quyến rũ và tự do.",
                "Yves Saint Laurent Libre Eau De Parfum là kiệt tác mùi hương dành cho người phụ nữ hiện đại, mạnh mẽ và độc lập. Mở đầu bằng sự tươi mát của tinh dầu lá cam và quýt hồng, bùng nổ ngọt ngào cùng hoa oải hương và hoa cam Ma-rốc, kết thúc lắng đọng bằng hương vani Madagascar và xạ hương gợi cảm.",
                "Alcohol, Parfum/Fragrance, Aqua/Water, Limonene, Linalool, Benzyl Salicylate, Hydroxycitronellal, Coumarin, Geraniol, Citronellol, Isoeugenol.",
                "Xịt trực tiếp vào các điểm mạch trên cơ thể như cổ tay, sau tai, ngực và phía trong khuỷu tay để hương thơm khuếch tán và lưu giữ dài lâu từ 8-12 tiếng.",
                BigDecimal.valueOf(3100000),
                BigDecimal.valueOf(3500000),
                catPerfume,
                bYSL,
                true,
                "/uploads/products/ysl-libre-edp-50ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "CHN-MDO-035",
                "Nước Hoa Nữ Quý Phái Chanel Coco Mademoiselle Eau De Parfum 50ml",
                "nuoc-hoa-chanel-coco-mademoiselle-eau-de-parfum-50ml",
                "Hương thơm phương Đông ấm áp, tươi mát của cam bergamot, hoa hồng Thổ Nhĩ Kỳ và hoắc hương.",
                "Chanel Coco Mademoiselle là biểu tượng nước hoa kinh điển trường tồn của phái đẹp toàn cầu. Sự kết hợp hoàn hảo giữa hương thơm thanh khiết của hoa hồng Bungari, hoa nhài Grasse và chiều sâu sang trọng của hoắc hương Indonesia cùng cỏ vetiver tạo nên sức quyến rũ khó cưỡng và phong thái đài các.",
                "Alcohol, Parfum/Fragrance, Aqua/Water, Linalool, Limonene, Benzyl Salicylate, Citronellol, Geraniol, Hexyl Cinnamal, Coumarin, Citral, Benzyl Alcohol.",
                "Xịt một làn sương hương thơm vào không khí rồi bước qua, hoặc chấm nhẹ tại các điểm đập nhịp đập của cơ thể như cổ tay và gáy cổ.",
                BigDecimal.valueOf(3450000),
                BigDecimal.valueOf(3850000),
                catPerfume,
                bChanel,
                true,
                "/uploads/products/chanel-coco-mademoiselle-edp-50ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "DOR-SVG-036",
                "Nước Hoa Nam Lịch Lãm Đầy Sức Hút Dior Sauvage Eau De Parfum 60ml",
                "nuoc-hoa-nam-dior-sauvage-eau-de-parfum-60ml",
                "Hương cam Bergamot vùng Calabria nồng nàn cùng hạt tiêu hồng và vani Papua New Guinea nam tính.",
                "Dior Sauvage Eau De Parfum lấy cảm hứng từ sa mạc rộng lớn lúc hoàng hôn buông xuống. Hương cam Bergamot mọng nước kích thích mọi giác quan kết hợp cùng nốt khói huyền bí của tinh chất vani tự nhiên và hương gỗ hổ phách tạo nên dấu ấn nam tính phong trần, lịch lãm và đầy mê hoặc.",
                "Alcohol, Parfum/Fragrance, Aqua/Water, Limonene, Linalool, Ethylhexyl Methoxycinnamate, Citronellol, Coumarin, Citral, Geraniol, Tocopherol.",
                "Xịt 2-3 nhát lên cổ áo, hai bên cổ và cổ tay trước các buổi gặp gỡ quan trọng hoặc buổi hẹn hò buổi tối để tỏa sáng phong thái phái mạnh.",
                BigDecimal.valueOf(2950000),
                BigDecimal.valueOf(3350000),
                catPerfume,
                bDior,
                true,
                "/uploads/products/dior-sauvage-edp-60ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "JML-ENG-037",
                "Nước Hoa Niche Tinh Tế Jo Malone London English Pear & Freesia Cologne 30ml",
                "nuoc-hoa-jo-malone-english-pear-and-freesia-30ml",
                "Hương quả lê chín mọng mùa thu hòa quyện hoa lan nam phi trắng thanh tao của vùng quê nước Anh.",
                "Jo Malone London English Pear & Freesia Cologne mang trọn vẻ đẹp của mùa thu nước Anh thanh bình. Mở đầu bằng hương lê King William ngọt mát mọng nước, ôm ấp hương hoa lan Nam Phi trắng tinh khôi và kết thúc bằng hổ phách, hoắc hương ấm áp dịu dàng tạo cảm giác thư thái quý phái.",
                "Alcohol Denat, Water/Aqua, Fragrance/Parfum, Farnesol, Limonene, Geraniol, Linalool, Hexyl Cinnamal, Citronellol, Citral.",
                "Xịt tự do lên cơ thể và quần áo. Có thể kết hợp xịt lớp (layering) với các mùi hương hoa khác của Jo Malone để tạo nên dấu ấn hương thơm độc bản của riêng bạn.",
                BigDecimal.valueOf(1950000),
                BigDecimal.valueOf(2250000),
                catPerfume,
                bJoMalone,
                false,
                "/uploads/products/jo-malone-english-pear-freesia.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LOR-OIL-010",
                "Dầu Dưỡng Tóc Chuyên Sâu L'Oréal Professionnel Mythic Oil Huile Originale 100ml",
                "dau-duong-toc-loreal-mythic-oil-100ml",
                "Chiết xuất tinh dầu argan nuôi dưỡng tóc bóng mượt, bảo vệ tóc trước nhiệt độ 230°C.",
                "L'Oréal Professionnel Mythic Oil Huile Originale là dầu dưỡng tóc chuyên nghiệp chuẩn salon hàng đầu thế giới. Bổ sung dầu Argan tự nhiên giàu Vitamin E nuôi dưỡng sâu sợi tóc hư tổn do uốn nhuộm, kiểm soát độ xơ rối và bảo vệ nhiệt hiệu quả lên đến 230 độ C mà không gây bết dính.",
                "Cyclopentasiloxane, Dimethiconol, C12-15 Alkyl Benzoate, Argania Spinosa Kernel Oil, Limonene, Hexyl Cinnamal, Coumarin, Linalool, Amyl Cinnamal, Geraniol.",
                "Bơm 1-2 giọt ra lòng bàn tay xoa đều rồi vuốt đều từ thân đến ngọn tóc khi tóc còn ẩm sau khi gội hoặc trước khi sấy tạo kiểu tóc.",
                BigDecimal.valueOf(480000),
                BigDecimal.valueOf(550000),
                catHaircare,
                bLoreal,
                false,
                "/uploads/products/loreal-mythic-oil-100ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "LOR-ELS-038",
                "Dầu Dưỡng Tóc Tinh Dầu Hoa Tự Nhiên L'Oréal Paris Elseve Extraordinary Oil 100ml",
                "dau-duong-toc-loreal-paris-elseve-extraordinary-oil-100ml",
                "Chiết xuất 6 loại hoa thiên nhiên quý giá nuôi dưỡng tóc óng ả mềm mại tức thì.",
                "L'Oréal Paris Elseve Extraordinary Oil mang đến giải pháp dưỡng tóc toàn diện từ 6 loài hoa quý (hoa sen, hoa cúc, hoa hồng, hoa tiare, hoa lanh, hoa hướng dương). Thấm sâu vào biểu bì tóc khô xơ mà không gây bết dính, mang lại mái tóc óng ả bồng bềnh và ngát hương thơm quyến rũ.",
                "Cyclopentasiloxane, Dimethiconol, Chamomilla Recutita Extract, Cocos Nucifera Oil, Nelumbium Speciosum Flower Extract, Linum Usitatissimum Flower Extract, Glycine Soja Oil.",
                "Dùng 2-3 giọt xoa đều lên tóc trước khi gội đầu để gỡ rối, hoặc sau khi sấy tóc để tóc vào nếp bóng mượt suốt cả ngày dài.",
                BigDecimal.valueOf(239000),
                BigDecimal.valueOf(279000),
                catHaircare,
                bLoreal,
                false,
                "/uploads/products/loreal-elseve-extraordinary-oil-100ml.png",
                branches
        ));

        seededProducts.add(upsertProduct(
                "MRC-TRT-039",
                "Tinh Dầu Dưỡng Tóc Phục Hồi Moroccanoil Treatment Original 100ml",
                "tinh-dau-duong-toc-moroccanoil-treatment-original-100ml",
                "Dầu Argan nguyên chất trứ danh phục hồi tóc hư tổn, tăng độ bóng mượt và rút ngắn thời gian sấy.",
                "Moroccanoil Treatment Original là sản phẩm tiên phong khai sinh danh mục chăm sóc tóc bằng tinh dầu trên toàn thế giới. Giàu chất chống oxy hóa, protein và vitamin từ dầu argan nguyên chất, thẩm thấu ngay tức thì để hàn gắn các liên kết tóc đứt gãy, chống chẻ ngọn và đem lại độ bóng rực rỡ kỳ diệu.",
                "Cyclomethicone, Dimethicone, Argania Spinosa (Argan) Kernel Oil, Fragrance/Parfum, Linum Usitatissimum (Linseed) Seed Extract, CI 26100 (Red 17), CI 47000 (Yellow 11).",
                "Thoa 1-2 lần bơm tinh dầu Moroccanoil lên tóc sạch đã lau khô bằng khăn, từ giữa thân tóc đến ngọn tóc. Sấy khô hoặc để tóc khô tự nhiên.",
                BigDecimal.valueOf(980000),
                BigDecimal.valueOf(1150000),
                catHaircare,
                bMoroccanoil,
                true,
                "/uploads/products/moroccanoil-treatment-100ml.jpg",
                branches
        ));

        seededProducts.add(upsertProduct(
                "TSU-SHP-040",
                "Dầu Gội Phục Hồi Tóc Hư Tổn Chuẩn Salon Nhật Bản Tsubaki Premium Repair Shampoo 490ml",
                "dau-goi-tsubaki-premium-repair-shampoo-490ml",
                "Công nghệ thẩm thấu CMC độc quyền đưa dưỡng chất hoa trà đỏ phục hồi lõi tóc hư tổn sâu.",
                "Dầu gội phục hồi Tsubaki Premium Repair ứng dụng công nghệ thẩm thấu định vị CMC độc quyền từ tập đoàn Shiseido Nhật Bản. Mở rộng biểu bì tóc tức thì để dẫn truyền tinh dầu hoa trà đỏ, protein ngọc trai và sữa ong chúa vào sâu tận lõi tóc, giúp tóc mềm mượt, bồng bềnh chuẩn chất lượng salon cao cấp ngay tại nhà.",
                "Water/Aqua, Sodium Laureth Sulfate, Cocamidopropyl Betaine, Guar Hydroxypropyltrimonium Chloride, Camellia Japonica Seed Oil (Hoa trà đỏ), Royal Jelly Extract (Sữa ong chúa), Glycine Soja Seed Extract, Sodium Benzoate, Citric Acid.",
                "Làm ướt tóc và da đầu. Lấy lượng dầu gội vừa đủ mát-xa nhẹ nhàng tạo bọt trên da đầu 2-3 phút để lưu thông tuần hoàn máu, sau đó xả sạch kỹ với nước.",
                BigDecimal.valueOf(199000),
                BigDecimal.valueOf(239000),
                catHaircare,
                bTsubaki,
                false,
                "/uploads/products/tsubaki-premium-repair-shampoo.png",
                branches
        ));

        // 8. Seed sample demo orders if none exist
        if (orderRepository.count() == 0 && seededProducts.size() >= 15) {
            log.info("Seeding realistic sample orders for dashboard analytics...");
            createDemoOrder("OS-20261020-1011", customerDemo, b1, OrderStatus.COMPLETED, PaymentStatus.PAID,
                    seededProducts.get(0), 2, seededProducts.get(2), 1, "Nguyễn Thảo My", "0918888999", "45 Lê Lợi, Q.1, TP. HCM",
                    "Giao hàng giờ hành chính", null);

            createDemoOrder("OS-20261022-2045", customerDemo, b1, OrderStatus.COMPLETED, PaymentStatus.PAID,
                    seededProducts.get(1), 1, seededProducts.get(3), 1, "Nguyễn Thảo My", "0918888999", "45 Lê Lợi, Q.1, TP. HCM",
                    null, null);

            createDemoOrder("OS-20261024-3092", customerDemo, b2, OrderStatus.PROCESSING, PaymentStatus.PAID,
                    seededProducts.get(8), 1, seededProducts.get(4), 2, "Hoàng Lan Anh", "0934567890", "12 Hoàng Quốc Việt, Cầu Giấy, Hà Nội",
                    "Giao cẩn thận đồ dễ vỡ", null);

            createDemoOrder("OS-20261025-4128", customerDemo, b1, OrderStatus.PENDING, PaymentStatus.UNPAID,
                    seededProducts.get(6), 1, seededProducts.get(13), 2, "Vũ Minh Quân", "0987654321", "88 Nguyễn Thị Minh Khai, Q.3, TP. HCM",
                    "Gọi trước khi giao 15 phút", null);

            createDemoOrder("OS-20261025-5201", customerDemo, b1, OrderStatus.CANCELLED, PaymentStatus.REFUNDED,
                    seededProducts.get(7), 1, null, 0, "Đỗ Hoàng Long", "0912345678", "210 Điện Biên Phủ, Q.3, TP. HCM",
                    "Khách đổi ý muốn mua sản phẩm khác", "Khách hàng yêu cầu hủy đơn");

            // Seed completed order for userKhoi (Lê Huỳnh Anh Khôi) so he can test review feature
            createDemoOrder("OS-20261026-9901", userKhoi, b1, OrderStatus.COMPLETED, PaymentStatus.PAID,
                    seededProducts.get(0), 1, seededProducts.get(1), 1, "Lê Huỳnh Anh Khôi", "0349846114", "120 Hai Bà Trưng, Q.1, TP. HCM",
                    "Đã nhận hàng thành công", null);
        }

        // 9. Seed sample verified product reviews if none exist
        if (productReviewRepository.count() == 0 && seededProducts.size() >= 3) {
            log.info("Seeding verified product reviews...");
            productReviewRepository.save(com.oneshop.customer.ProductReview.builder()
                    .product(seededProducts.get(0))
                    .user(customerDemo)
                    .rating(5)
                    .comment("Chất kem mềm mịn, phục hồi da kích ứng rất nhanh. Mua chính hãng OneShop an tâm tuyệt đối!")
                    .active(true)
                    .createdAt(LocalDateTime.now().minusDays(5))
                    .build());

            productReviewRepository.save(com.oneshop.customer.ProductReview.builder()
                    .product(seededProducts.get(1))
                    .user(customerDemo)
                    .rating(5)
                    .comment("Dung dịch tẩy da chết BHA 2% Paula's Choice gom cồi mụn cực đỉnh, lỗ chân lông sạch thoáng hẳn!")
                    .active(true)
                    .createdAt(LocalDateTime.now().minusDays(3))
                    .build());

            productReviewRepository.save(com.oneshop.customer.ProductReview.builder()
                    .product(seededProducts.get(2))
                    .user(customerDemo)
                    .rating(5)
                    .comment("Kem dưỡng hoa cúc Kiehl's cấp nước mướt rượt, mùi thảo mộc hoa cúc thanh nhẹ rất thư giãn!")
                    .active(true)
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build());
        }

        log.info("OneShop 40 cosmetics products catalog seed/sync completed successfully with 100% exact images!");
    }

    private Role initRole(RoleName name, String description) {
        return roleRepository.findByName(name).orElseGet(() ->
                roleRepository.save(Role.builder().name(name).description(description).build())
        );
    }

    private Branch initBranch(String code, String name, String address, String phone, String hours) {
        return branchRepository.findByCode(code).orElseGet(() ->
                branchRepository.save(Branch.builder()
                        .code(code)
                        .name(name)
                        .address(address)
                        .phone(phone)
                        .openingHours(hours)
                        .active(true)
                        .build())
        );
    }

    private User initUser(String email, String rawPassword, String fullName, String phone, String address, Branch branch, Set<Role> roles) {
        return userRepository.findByEmail(email).map(user -> {
            user.setFullName(fullName);
            if (phone != null) user.setPhone(phone);
            if (address != null) user.setAddress(address);
            if (branch != null) user.setBranch(branch);
            if (roles != null && !roles.isEmpty()) {
                if (user.getRoles() == null) {
                    user.setRoles(new HashSet<>(roles));
                } else {
                    user.getRoles().clear();
                    user.getRoles().addAll(roles);
                }
            }
            return userRepository.save(user);
        }).orElseGet(() ->
                userRepository.save(User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(rawPassword))
                        .fullName(fullName)
                        .phone(phone)
                        .address(address)
                        .branch(branch)
                        .roles(roles != null ? new HashSet<>(roles) : new HashSet<>())
                        .active(true)
                        .build())
        );
    }

    private void initCoupon(String code, String desc, CouponDiscountType type, BigDecimal value, BigDecimal minOrder, BigDecimal maxDiscount) {
        couponRepository.findByCodeIgnoreCase(code).ifPresentOrElse(
                c -> {
                    c.setDescription(desc);
                    c.setDiscountType(type);
                    c.setDiscountValue(value);
                    c.setMinOrderAmount(minOrder);
                    c.setMaxDiscountAmount(maxDiscount);
                    c.setActive(true);
                    c.setStartDate(LocalDateTime.now().minusDays(30));
                    c.setEndDate(LocalDateTime.now().plusMonths(12));
                    couponRepository.save(c);
                },
                () -> couponRepository.save(Coupon.builder()
                        .code(code)
                        .description(desc)
                        .discountType(type)
                        .discountValue(value)
                        .minOrderAmount(minOrder)
                        .maxDiscountAmount(maxDiscount)
                        .startDate(LocalDateTime.now().minusDays(30))
                        .endDate(LocalDateTime.now().plusMonths(12))
                        .usageLimit(1000)
                        .usedCount(5)
                        .active(true)
                        .build())
        );
    }

    private Category initCategory(String name, String slug, String description, String imageUrl, int sortOrder) {
        return categoryRepository.findBySlug(slug).orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .name(name)
                        .slug(slug)
                        .description(description)
                        .imageUrl(imageUrl)
                        .sortOrder(sortOrder)
                        .active(true)
                        .build())
        );
    }

    private Brand initBrand(String name, String slug, String description, String logoUrl) {
        return brandRepository.findBySlug(slug).map(b -> {
            if (logoUrl != null && !logoUrl.isBlank() && (b.getLogoUrl() == null || b.getLogoUrl().isBlank())) {
                b.setLogoUrl(logoUrl);
                return brandRepository.save(b);
            }
            return b;
        }).orElseGet(() ->
                brandRepository.save(Brand.builder()
                        .name(name)
                        .slug(slug)
                        .description(description)
                        .logoUrl(logoUrl)
                        .active(true)
                        .build())
        );
    }

    private Product upsertProduct(
            String sku, String name, String slug, String shortDesc, String desc,
            String ingredients, String howToUse, BigDecimal price, BigDecimal compareAtPrice,
            Category category, Brand brand, boolean isFeatured, String imageUrl,
            List<Branch> branches) {

        Product product = productRepository.findBySku(sku).orElse(null);
        if (product == null) {
            product = productRepository.findBySlug(slug).orElse(null);
        }

        if (product == null) {
            product = Product.builder()
                    .sku(sku)
                    .name(name)
                    .slug(slug)
                    .shortDescription(shortDesc)
                    .description(desc)
                    .ingredients(ingredients)
                    .howToUse(howToUse)
                    .price(price)
                    .compareAtPrice(compareAtPrice)
                    .category(category)
                    .brand(brand)
                    .isFeatured(isFeatured)
                    .active(true)
                    .build();
            product = productRepository.save(product);
        } else {
            product.setName(name);
            product.setSlug(slug);
            product.setShortDescription(shortDesc);
            product.setDescription(desc);
            product.setIngredients(ingredients);
            product.setHowToUse(howToUse);
            product.setPrice(price);
            product.setCompareAtPrice(compareAtPrice);
            product.setCategory(category);
            product.setBrand(brand);
            product.setIsFeatured(isFeatured);
            product.setActive(true);
            product = productRepository.save(product);
        }

        // Upsert primary image - replace any old inaccurate images completely
        List<ProductImage> existingImages = imageRepository.findByProduct(product);
        if (existingImages == null || existingImages.isEmpty()) {
            ProductImage image = ProductImage.builder()
                    .product(product)
                    .secureUrl(imageUrl)
                    .altText(name)
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            imageRepository.save(image);
        } else {
            for (ProductImage img : existingImages) {
                img.setSecureUrl(imageUrl);
                img.setAltText(name);
                imageRepository.save(img);
            }
        }

        // Ensure inventory exists for all branches
        for (Branch b : branches) {
            var invOpt = inventoryRepository.findByBranchIdAndProductId(b.getId(), product.getId());
            if (invOpt.isEmpty()) {
                int baseStock = 25 + (int) ((product.getId() != null ? product.getId() : 1) * 7 + b.getId() * 11) % 40;
                BranchInventory inv = BranchInventory.builder()
                        .branch(b)
                        .product(product)
                        .quantityOnHand(baseStock)
                        .quantityReserved(0)
                        .reorderLevel(10)
                        .build();
                inventoryRepository.save(inv);

                StockMovement movement = StockMovement.builder()
                        .branch(b)
                        .product(product)
                        .movementType(MovementType.IMPORT)
                        .quantityDelta(baseStock)
                        .quantityBefore(0)
                        .quantityAfter(baseStock)
                        .referenceType("INITIAL_STOCK")
                        .reason("Nhập kho tồn đầu kỳ hệ thống OneShop")
                        .performedBy("Hệ thống khởi tạo")
                        .build();
                movementRepository.save(movement);
            }
        }

        return product;
    }

    private void createDemoOrder(
            String code, User user, Branch branch, OrderStatus status, PaymentStatus paymentStatus,
            Product p1, int q1, Product p2, int q2, String name, String phone, String addr, String note, String cancelReason) {

        BigDecimal subtotal = p1.getPrice().multiply(BigDecimal.valueOf(q1));
        if (p2 != null && q2 > 0) {
            subtotal = subtotal.add(p2.getPrice().multiply(BigDecimal.valueOf(q2)));
        }

        BigDecimal shipping = subtotal.compareTo(BigDecimal.valueOf(500000)) >= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(30000);
        BigDecimal total = subtotal.add(shipping);

        Order order = Order.builder()
                .orderCode(code)
                .user(user)
                .branch(branch)
                .fulfillmentType(FulfillmentType.DELIVERY)
                .status(status)
                .paymentStatus(paymentStatus)
                .paymentMethod(PaymentMethod.COD)
                .recipientName(name)
                .recipientPhone(phone)
                .recipientEmail(user != null ? user.getEmail() : "customer@demo.vn")
                .shippingAddress(addr)
                .note(note)
                .cancelReason(cancelReason)
                .subtotal(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .shippingFee(shipping)
                .totalAmount(total)
                .build();

        OrderItem item1 = OrderItem.builder()
                .product(p1)
                .productName(p1.getName())
                .skuSnapshot(p1.getSku())
                .imageUrl(p1.getPrimaryImageUrl())
                .unitPrice(p1.getPrice())
                .quantity(q1)
                .lineTotal(p1.getPrice().multiply(BigDecimal.valueOf(q1)))
                .build();
        order.addItem(item1);

        if (p2 != null && q2 > 0) {
            OrderItem item2 = OrderItem.builder()
                    .product(p2)
                    .productName(p2.getName())
                    .skuSnapshot(p2.getSku())
                    .imageUrl(p2.getPrimaryImageUrl())
                    .unitPrice(p2.getPrice())
                    .quantity(q2)
                    .lineTotal(p2.getPrice().multiply(BigDecimal.valueOf(q2)))
                    .build();
            order.addItem(item2);
        }

        orderRepository.save(order);
    }
}
