package com.oneshop.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.targetBranch.id = :branchId OR n.targetBranch IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findRecentForBranch(@Param("branchId") Long branchId, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.targetBranch.id = :branchId OR n.targetBranch IS NULL) AND n.isRead = false")
    long countUnreadForBranch(@Param("branchId") Long branchId);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
