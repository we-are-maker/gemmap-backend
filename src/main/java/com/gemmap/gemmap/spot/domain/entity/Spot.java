package com.gemmap.gemmap.spot.domain.entity;

import com.gemmap.gemmap.auth.domain.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "spots")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 스팟 id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 생성자 (사용자 id)

    @Column(length = 400, nullable = false)
    private String address; // 도로명주소

    @Column(length = 200, nullable = false)
    private String alias; // 별칭 - 사용자 입력

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성 시각
}
