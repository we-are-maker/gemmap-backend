package com.gemmap.gemmap.report.domain.entity;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.EReportReason;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "spot_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_spot_reports_user_spot",
                columnNames = {"user_id", "spot_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Spot spot;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private EReportReason reason;

    @Column(name = "content", length = 100)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SpotReport(User user, Spot spot, EReportReason reason, String content) {
        this.user = user;
        this.spot = spot;
        this.reason = reason;
        this.content = content;
    }
}
