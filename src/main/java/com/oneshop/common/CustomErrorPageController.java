package com.oneshop.common;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/error")
public class CustomErrorPageController {

    @GetMapping("/403")
    public String accessDenied(Model model) {
        model.addAttribute("errorCode", "403");
        model.addAttribute("errorMessage", "Bạn không có quyền truy cập vào chức năng hoặc trang này.");
        return "error/403";
    }

    @GetMapping("/404")
    public String notFound(Model model) {
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorMessage", "Trang bạn tìm kiếm không tồn tại hoặc đã được chuyển đi.");
        return "error/404";
    }

    @GetMapping("/500")
    public String serverError(Model model) {
        model.addAttribute("errorCode", "500");
        model.addAttribute("errorMessage", "Hệ thống đang gặp sự cố tạm thời. Vui lòng thử lại sau ít phút.");
        return "error/500";
    }
}
