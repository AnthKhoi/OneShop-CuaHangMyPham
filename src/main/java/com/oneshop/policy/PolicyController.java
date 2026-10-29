package com.oneshop.policy;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/policy")
public class PolicyController {

    @GetMapping("/return")
    public String returnPolicy() {
        return "store/policy/return";
    }

    @GetMapping("/privacy")
    public String privacyPolicy() {
        return "store/policy/privacy";
    }

    @GetMapping("/terms")
    public String termsOfService() {
        return "store/policy/terms";
    }

    @GetMapping("/shipping")
    public String shippingPolicy() {
        return "store/policy/shipping";
    }

    @GetMapping("/payment")
    public String paymentPolicy() {
        return "store/policy/payment";
    }
}
