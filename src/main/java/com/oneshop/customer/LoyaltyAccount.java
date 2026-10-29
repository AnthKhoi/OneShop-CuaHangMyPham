package com.oneshop.customer;

import com.oneshop.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(nullable = false)
    private Integer points = 0;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String tier = "BRONZE"; // BRONZE, SILVER, GOLD, DIAMOND

    @Builder.Default
    @Column(name = "total_spent", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addPoints(int earned, BigDecimal amountSpent) {
        this.points += earned;
        if (amountSpent != null) {
            this.totalSpent = this.totalSpent.add(amountSpent);
        }
        recalculateTier();
        this.updatedAt = LocalDateTime.now();
    }

    public void usePoints(int used) {
        if (used > this.points) {
            throw new IllegalArgumentException("Số điểm tích lũy không đủ để sử dụng");
        }
        this.points -= used;
        this.updatedAt = LocalDateTime.now();
    }

    private void recalculateTier() {
        if (totalSpent.compareTo(BigDecimal.valueOf(15000000)) >= 0) {
            this.tier = "DIAMOND";
        } else if (totalSpent.compareTo(BigDecimal.valueOf(8000000)) >= 0) {
            this.tier = "GOLD";
        } else if (totalSpent.compareTo(BigDecimal.valueOf(3000000)) >= 0) {
            this.tier = "SILVER";
        } else {
            this.tier = "BRONZE";
        }
    }
}
