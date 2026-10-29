package com.oneshop.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final boolean isConfigured;
    private final MediaAssetRepository mediaAssetRepository;

    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> ALLOWED_FOLDERS = Set.of("products", "banners", "brands", "general");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Autowired
    public CloudinaryService(
            @Value("${oneshop.cloudinary.cloud-name:}") String cloudName,
            @Value("${oneshop.cloudinary.api-key:}") String apiKey,
            @Value("${oneshop.cloudinary.api-secret:}") String apiSecret,
            MediaAssetRepository mediaAssetRepository) {

        this.mediaAssetRepository = mediaAssetRepository;

        if (StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret)) {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret,
                    "secure", true
            ));
            this.isConfigured = true;
            log.info("Cloudinary service initialized successfully with cloud name: {}", cloudName);
        } else {
            this.cloudinary = null;
            this.isConfigured = false;
            log.warn("Cloudinary credentials are not configured. Running in mock/fallback mode.");
        }
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    public UploadResult uploadImage(MultipartFile file, String folder) throws IOException {
        return uploadImage(file, folder, "Admin");
    }

    @Transactional
    public UploadResult uploadImage(MultipartFile file, String folder, String uploadedBy) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dung lượng tệp vượt quá giới hạn tối đa 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Định dạng tệp không hợp lệ. Chỉ chấp nhận JPG, PNG, WEBP, GIF");
        }

        // Validate extension
        String rawExt = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (rawExt == null || !ALLOWED_EXTENSIONS.contains(rawExt.toLowerCase())) {
            throw new IllegalArgumentException("Đuôi mở rộng của tệp không hợp lệ. Chỉ chấp nhận jpg, jpeg, png, webp, gif");
        }
        String ext = rawExt.toLowerCase();

        // Validate Magic Bytes
        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.read(header);
            if (bytesRead < 4 || !isValidImageHeader(header)) {
                throw new IllegalArgumentException("Nội dung tệp không phải là hình ảnh hợp lệ (magic bytes mismatch)");
            }
        }

        // Sanitize and whitelist target folder
        String sanitizedFolder = "general";
        if (folder != null) {
            String clean = folder.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
            if (ALLOWED_FOLDERS.contains(clean)) {
                sanitizedFolder = clean;
            }
        }

        String secureUrl;
        String publicId;

        if (isConfigured && cloudinary != null) {
            Map<?, ?> uploadParams = ObjectUtils.asMap(
                    "folder", "oneshop/" + sanitizedFolder,
                    "resource_type", "image"
            );
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            secureUrl = (String) result.get("secure_url");
            publicId = (String) result.get("public_id");
        } else {
            // Graceful fallback: save uploaded file to local storage with path traversal protection
            try {
                Path uploadRoot = Paths.get("uploads").toAbsolutePath().normalize();
                Path targetDir = uploadRoot.resolve(sanitizedFolder).normalize();
                if (!targetDir.startsWith(uploadRoot)) {
                    throw new SecurityException("Phát hiện đường dẫn không hợp lệ");
                }
                Files.createDirectories(targetDir);

                String savedFileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
                Path destination = targetDir.resolve(savedFileName).normalize();
                if (!destination.startsWith(uploadRoot)) {
                    throw new SecurityException("Đường dẫn lưu file nằm ngoài thư mục uploads cho phép");
                }

                Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

                publicId = "local_" + savedFileName;
                secureUrl = "/uploads/" + sanitizedFolder + "/" + savedFileName;
                log.info("Saved local file: {} -> URL: {}", destination, secureUrl);
            } catch (Exception ex) {
                log.error("Failed to save local file, using fallback URL", ex);
                publicId = "mock_" + UUID.randomUUID().toString().substring(0, 8);
                secureUrl = "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=800&q=80";
            }
        }

        // Save Media Asset tracking record safely
        try {
            MediaAsset asset = MediaAsset.builder()
                    .fileName(file.getOriginalFilename())
                    .secureUrl(secureUrl)
                    .cloudinaryPublicId(publicId)
                    .fileSize(file.getSize())
                    .fileType(contentType)
                    .folder(folder != null ? folder : "products")
                    .uploadedBy(uploadedBy != null ? uploadedBy : "Admin")
                    .build();
            mediaAssetRepository.save(asset);
        } catch (Exception ex) {
            log.warn("Could not save media asset audit record: {}", ex.getMessage());
        }

        return new UploadResult(secureUrl, publicId);
    }

    @Transactional
    public void deleteImage(String publicId) {
        if (isConfigured && cloudinary != null && StringUtils.hasText(publicId) && !publicId.startsWith("mock_")) {
            try {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            } catch (IOException e) {
                log.error("Failed to delete image with publicId: {}", publicId, e);
            }
        }
        try {
            mediaAssetRepository.findByCloudinaryPublicId(publicId).ifPresent(mediaAssetRepository::delete);
        } catch (Exception ex) {
            log.warn("Could not delete media asset audit record: {}", ex.getMessage());
        }
    }

    public List<MediaAsset> getAllMediaAssets() {
        try {
            return mediaAssetRepository.findAllByOrderByCreatedAtDesc();
        } catch (Exception ex) {
            log.warn("Could not load media assets from DB: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private boolean isValidImageHeader(byte[] header) {
        if (header == null || header.length < 4) return false;
        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // GIF: GIF8
        if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
            return true;
        }
        // WEBP: RIFF....WEBP
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return true;
        }
        return false;
    }

    public record UploadResult(String secureUrl, String publicId) {}
}
