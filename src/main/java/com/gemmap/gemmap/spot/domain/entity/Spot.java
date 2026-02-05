package com.gemmap.gemmap.spot.domain.entity;

import com.gemmap.gemmap.auth.domain.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "spots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Spot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 스팟 id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 생성자 (사용자 id)

    @Column(length = 20, nullable = false)
    private String sido; // 시/도 - 예: 경기도, 서울특별시

    @Column(length = 20, nullable = false)
    private String sigungu; // 시/군/구 - 예: 남앙주시, 강남구

    @Column(length = 20)
    private String eupmyeondong; // 읍/면/동 = 예: 와부읍, 삼성동

    @Column(length = 100)
    private String roadName; // 도로명 - 예: 경걍로926번길

    @Column(length = 20)
    private String buildingNo; // 건물번호 - 예: 20

    @Column(length = 255, nullable = false)
    private String fullAddress; // 전체 주소 - 예: 대한민국 경기도 남양주시 와부읍... (상세 화면용)

    @Column(length = 50, nullable = false)
    private String shortAddress; // 단축 주소 - 예: 경기도 남양주시 (리스트 화면용)

    @Column(length = 200, nullable = false)
    private String alias; // 별칭 - 사용자 입력

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성 시각

    @Builder
    public Spot(User user,
                String sido, String sigungu, String eupmyeondong, String roadName, String buildingNo,
                String fullAddress, String shortAddress,
                String alias, LocalDateTime createdAt) {
        this.user = user;
        this.sido = sido;
        this.sigungu = sigungu;
        this.eupmyeondong = eupmyeondong;
        this.roadName = roadName;
        this.buildingNo = buildingNo;
        this.fullAddress = fullAddress;
        this.shortAddress = shortAddress;
        this.alias = alias;
    }
}
