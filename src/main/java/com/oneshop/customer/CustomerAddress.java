package com.oneshop.customer;

import com.oneshop.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "recipient_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String city;

    @Column(nullable = false, columnDefinition = "NVARCHAR(100)")
    private String district;

    @Column(columnDefinition = "NVARCHAR(100)")
    private String ward;

    @Column(name = "street_address", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String streetAddress;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(streetAddress);
        if (ward != null && !ward.isBlank()) sb.append(", ").append(ward);
        sb.append(", ").append(district).append(", ").append(city);
        return sb.toString();
    }
}
