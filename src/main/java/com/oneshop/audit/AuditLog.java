package com.oneshop.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Column(nullable = false, length = 50)
    private String action; // CREATE, UPDATE, DELETE, STOCK_IN, STOCK_TRANSFER, ORDER_CONFIRM...

    @Column(name = "entity_name", nullable = false, length = 80)
    private String entityName; // Product, StockReceipt, StockTransfer, Order...

    @Column(name = "entity_id", length = 50)
    private String entityId;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
