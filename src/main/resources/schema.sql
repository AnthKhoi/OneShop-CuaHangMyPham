-- ===================================================================
-- DATABASE SCHEMA: Microsoft SQL Server (OneShop Cosmetics Chain)
-- Database: OneShopDB
-- ===================================================================

USE OneShopDB;
GO

-- 1. Bảng branches (Chi nhánh cửa hàng)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'branches')
CREATE TABLE branches (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code NVARCHAR(30) NOT NULL UNIQUE,
    name NVARCHAR(150) NOT NULL,
    address NVARCHAR(255) NOT NULL,
    phone NVARCHAR(20) NULL,
    opening_hours NVARCHAR(100) NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2 NULL,
    updated_at DATETIME2 NULL
);
GO

-- 2. Bảng roles & users & user_roles
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'roles')
CREATE TABLE roles (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(30) NOT NULL UNIQUE,
    description NVARCHAR(100) NULL
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'users')
CREATE TABLE users (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email NVARCHAR(100) NOT NULL UNIQUE,
    password_hash NVARCHAR(255) NOT NULL,
    full_name NVARCHAR(100) NOT NULL,
    phone NVARCHAR(20) NULL,
    address NVARCHAR(255) NULL,
    branch_id BIGINT NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2 NULL,
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branches(id)
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'user_roles')
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);
GO

-- 3. Bảng categories & brands & products & product_images
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'categories')
CREATE TABLE categories (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    slug NVARCHAR(120) NOT NULL UNIQUE,
    description NVARCHAR(MAX) NULL,
    image_url NVARCHAR(500) NULL,
    parent_id BIGINT NULL,
    active BIT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES categories(id)
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'brands')
CREATE TABLE brands (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    slug NVARCHAR(120) NOT NULL UNIQUE,
    logo_url NVARCHAR(500) NULL,
    description NVARCHAR(MAX) NULL,
    active BIT NOT NULL DEFAULT 1
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'products')
CREATE TABLE products (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sku NVARCHAR(50) NOT NULL UNIQUE,
    name NVARCHAR(200) NOT NULL,
    slug NVARCHAR(220) NOT NULL UNIQUE,
    short_description NVARCHAR(500) NULL,
    description NVARCHAR(MAX) NULL,
    ingredients NVARCHAR(MAX) NULL,
    how_to_use NVARCHAR(MAX) NULL,
    price DECIMAL(18,2) NOT NULL,
    compare_at_price DECIMAL(18,2) NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NOT NULL,
    is_featured BIT NOT NULL DEFAULT 0,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME2 NULL,
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_prod_cat FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_prod_brand FOREIGN KEY (brand_id) REFERENCES brands(id)
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'product_images')
CREATE TABLE product_images (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id BIGINT NOT NULL,
    secure_url NVARCHAR(500) NOT NULL,
    cloudinary_public_id NVARCHAR(150) NULL,
    alt_text NVARCHAR(200) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_primary BIT NOT NULL DEFAULT 0,
    CONSTRAINT fk_img_prod FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
GO

-- 4. Bảng branch_inventory & stock_movements (Chuỗi cửa hàng & tồn kho)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'branch_inventory')
CREATE TABLE branch_inventory (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity_on_hand INT NOT NULL DEFAULT 0,
    quantity_reserved INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 10,
    version BIGINT NULL,
    updated_at DATETIME2 NULL,
    CONSTRAINT uk_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT fk_inv_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_inv_prod FOREIGN KEY (product_id) REFERENCES products(id)
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'stock_movements')
CREATE TABLE stock_movements (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    movement_type NVARCHAR(30) NOT NULL,
    quantity_delta INT NOT NULL,
    quantity_before INT NULL,
    quantity_after INT NULL,
    reference_type NVARCHAR(50) NULL,
    reference_id NVARCHAR(100) NULL,
    reason NVARCHAR(500) NULL,
    performed_by NVARCHAR(100) NULL,
    created_at DATETIME2 NULL,
    CONSTRAINT fk_sm_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_sm_prod FOREIGN KEY (product_id) REFERENCES products(id)
);
GO

-- 5. Bảng coupons (Mã giảm giá)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'coupons')
CREATE TABLE coupons (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    code NVARCHAR(50) NOT NULL UNIQUE,
    description NVARCHAR(200) NULL,
    discount_type NVARCHAR(20) NOT NULL,
    discount_value DECIMAL(18,2) NOT NULL,
    min_order_amount DECIMAL(18,2) NULL,
    max_discount_amount DECIMAL(18,2) NULL,
    start_date DATETIME2 NULL,
    end_date DATETIME2 NULL,
    usage_limit INT NULL,
    used_count INT NOT NULL DEFAULT 0,
    active BIT NOT NULL DEFAULT 1
);
GO

-- 6. Bảng orders & order_items (Đơn hàng & snapshot lịch sử)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'orders')
CREATE TABLE orders (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_code NVARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    branch_id BIGINT NOT NULL,
    fulfillment_type NVARCHAR(30) NOT NULL,
    status NVARCHAR(30) NOT NULL,
    payment_status NVARCHAR(30) NOT NULL,
    payment_method NVARCHAR(30) NOT NULL,
    recipient_name NVARCHAR(100) NOT NULL,
    recipient_phone NVARCHAR(20) NOT NULL,
    recipient_email NVARCHAR(100) NULL,
    shipping_address NVARCHAR(255) NULL,
    note NVARCHAR(500) NULL,
    cancel_reason NVARCHAR(500) NULL,
    subtotal DECIMAL(18,2) NOT NULL,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    shipping_fee DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL,
    coupon_code NVARCHAR(50) NULL,
    created_at DATETIME2 NULL,
    updated_at DATETIME2 NULL,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_order_branch FOREIGN KEY (branch_id) REFERENCES branches(id)
);
GO

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'order_items')
CREATE TABLE order_items (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NULL,
    product_name NVARCHAR(200) NOT NULL,
    sku_snapshot NVARCHAR(50) NOT NULL,
    image_url NVARCHAR(500) NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    quantity INT NOT NULL,
    line_total DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_oi_prod FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL
);
GO

-- 7. Bảng media_assets (Quản lý media/ảnh)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'media_assets')
CREATE TABLE media_assets (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    file_name NVARCHAR(255) NULL,
    secure_url NVARCHAR(500) NOT NULL,
    cloudinary_public_id NVARCHAR(150) NULL,
    file_size BIGINT NULL,
    file_type NVARCHAR(50) NULL,
    folder NVARCHAR(100) NULL,
    uploaded_by NVARCHAR(100) NULL,
    created_at DATETIME2 NULL
);
GO

-- Index tối ưu truy vấn
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_brand ON products(brand_id);
CREATE INDEX idx_products_price ON products(price);
CREATE INDEX idx_inventory_branch_product ON branch_inventory(branch_id, product_id);
CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_branch ON orders(branch_id);
CREATE INDEX idx_orders_created ON orders(created_at);
GO

