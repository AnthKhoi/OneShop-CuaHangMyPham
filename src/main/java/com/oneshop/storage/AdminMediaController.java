package com.oneshop.storage;

import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/admin/media")
@RequiredArgsConstructor
@Slf4j
@org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'BRANCH_MANAGER')")
public class AdminMediaController {

    private final CloudinaryService cloudinaryService;

    @GetMapping
    public String mediaLibrary(Model model) {
        try {
            model.addAttribute("mediaAssets", cloudinaryService.getAllMediaAssets());
            model.addAttribute("isConfigured", cloudinaryService.isConfigured());
        } catch (Exception ex) {
            log.error("Error loading media library: ", ex);
            model.addAttribute("mediaAssets", java.util.Collections.emptyList());
            model.addAttribute("isConfigured", false);
            model.addAttribute("errorMessage", "Không thể tải danh sách media: " + ex.getMessage());
        }
        return "admin/media/index";
    }

    @PostMapping("/upload")
    public String uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            RedirectAttributes redirectAttributes) {

        try {
            String actor = SecurityUtils.getCurrentUser().map(UserPrincipal::getFullName).orElse("Admin");
            CloudinaryService.UploadResult result = cloudinaryService.uploadImage(file, folder, actor);
            redirectAttributes.addFlashAttribute("successMessage", "Tải ảnh lên thành công: " + file.getOriginalFilename());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Tải ảnh thất bại: " + ex.getMessage());
        }

        return "redirect:/admin/media";
    }

    @PostMapping("/delete")
    public String deleteMedia(
            @RequestParam("publicId") String publicId,
            RedirectAttributes redirectAttributes) {

        try {
            cloudinaryService.deleteImage(publicId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa ảnh thành công!");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa ảnh: " + ex.getMessage());
        }

        return "redirect:/admin/media";
    }

    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<?> uploadAjax(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "products") String folder) {

        try {
            String actor = SecurityUtils.getCurrentUser().map(UserPrincipal::getFullName).orElse("Admin");
            CloudinaryService.UploadResult result = cloudinaryService.uploadImage(file, folder, actor);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", result.secureUrl(),
                    "publicId", result.publicId()
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", ex.getMessage()
            ));
        }
    }
}
