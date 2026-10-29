package com.oneshop.audit;

import com.oneshop.security.SecurityUtils;
import com.oneshop.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void logAction(String action, String entityName, String entityId, String details, HttpServletRequest request) {
        try {
            String username = SecurityUtils.getCurrentUsername().orElse("SYSTEM");
            String role = SecurityUtils.getCurrentUser()
                    .map(UserPrincipal::getRoleSummary)
                    .orElse("ANONYMOUS");

            String ip = request != null ? request.getRemoteAddr() : "127.0.0.1";

            AuditLog logEntry = AuditLog.builder()
                    .username(username)
                    .userRole(role)
                    .action(action)
                    .entityName(entityName)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(ip)
                    .build();

            auditLogRepository.save(logEntry);
            log.info("AUDIT: [{}] {} on {} id={} by {}", action, details, entityName, entityId, username);
        } catch (Exception ex) {
            log.error("Failed to persist audit log: {}", ex.getMessage());
        }
    }
}
