package com.oneshop.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/decorators")
public class DecoratorController {

    @GetMapping("/store-layout")
    public String storeLayout() {
        return "decorators/store-layout";
    }

    @GetMapping("/admin-layout")
    public String adminLayout() {
        return "decorators/admin-layout";
    }
}
