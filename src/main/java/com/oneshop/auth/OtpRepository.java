package com.oneshop.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByEmailIgnoreCaseAndTypeOrderByCreatedAtDesc(String email, OtpType type);

    Optional<OtpVerification> findByResetToken(String resetToken);

    void deleteByExpiresAtBefore(LocalDateTime time);
}
