package com.gemmap.gemmap.bookmark.domain.entity;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "spot_bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_spot_bookmarks_user_spot",
                columnNames = {"user_id", "spot_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotBookmark {

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

    @Column(name = "attraction_level", nullable = false)
    @Enumerated(EnumType.STRING)
    private EAttractionLevel attractionLevel;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SpotBookmark(User user, Spot spot, EAttractionLevel attractionLevel) {
        this.user = user;
        this.spot = spot;
        this.attractionLevel = attractionLevel;
    }
}