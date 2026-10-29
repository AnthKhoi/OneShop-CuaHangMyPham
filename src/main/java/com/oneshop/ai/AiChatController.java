package com.oneshop.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody(required = false) Map<String, String> request) {
        String message = "";
        if (request != null && request.containsKey("message")) {
            message = request.get("message");
        }
        Map<String, Object> response = aiChatService.generateAdvice(message);
        return ResponseEntity.ok(response);
    }
}
