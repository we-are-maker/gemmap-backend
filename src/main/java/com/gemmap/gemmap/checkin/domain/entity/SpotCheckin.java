package com.gemmap.gemmap.checkin.domain.entity;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "spot_checkins",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_spot_checkins_user_spot",
                columnNames = {"user_id", "spot_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotCheckin {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SpotPhoto photo;

    @Column(name = "recommendation_level", nullable = false)
    @Enumerated(EnumType.STRING)
    private ERecommendationLevel recommendationLevel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SpotCheckin(User user, Spot spot, SpotPhoto photo,
                       ERecommendationLevel recommendationLevel) {
        this.user = user;
        this.spot = spot;
        this.photo = photo;
        this.recommendationLevel = recommendationLevel;
    }
}
