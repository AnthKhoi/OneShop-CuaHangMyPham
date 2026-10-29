package com.oneshop.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public String handleResourceNotFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, Model model) {
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorMessage", "Không tìm thấy tài nguyên yêu cầu.");
        return "error/404";
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNotFound(NoHandlerFoundException ex, Model model) {
        log.warn("Page not found: {}", ex.getRequestURL());
        model.addAttribute("errorCode", "404");
        model.addAttribute("errorMessage", "Trang bạn tìm kiếm không tồn tại hoặc đã được chuyển đi.");
        return "error/404";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, Model model) {
        log.warn("Invalid argument: {}", ex.getMessage());
        model.addAttribute("errorCode", "400");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalState(IllegalStateException ex, Model model) {
        log.warn("Illegal state: {}", ex.getMessage());
        model.addAttribute("errorCode", "400");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Unhandled error: ", ex);
        model.addAttribute("errorCode", "500");
        model.addAttribute("errorMessage", "Hệ thống đang gặp sự cố tạm thời. Vui lòng thử lại sau ít phút.");
        return "error/500";
    }
}
