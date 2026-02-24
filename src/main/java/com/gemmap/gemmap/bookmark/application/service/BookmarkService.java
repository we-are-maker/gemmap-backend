package com.gemmap.gemmap.bookmark.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.bookmark.application.dto.response.BookmarkCreateResponse;
import com.gemmap.gemmap.bookmark.application.dto.response.MyBookmarksResponse;
import com.gemmap.gemmap.bookmark.domain.entity.SpotBookmark;
import com.gemmap.gemmap.bookmark.domain.repository.SpotBookmarkRepository;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.spot.application.dto.response.SpotSummary;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final UserRepository userRepository;
    private final SpotRepository spotRepository;
    private final SpotBookmarkRepository spotBookmarkRepository;
    private final SpotPhotoRepository spotPhotoRepository;

    /**
     * 젬 찜하기 (북마크 생성)
     */
    @Transactional
    public BookmarkCreateResponse createBookmark(
            Long userId, Long spotId, EAttractionLevel attractionLevel
    ) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 스팟 조회
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CommonException(ErrorCode.SPOT_NOT_FOUND));

        // 중복 찜 확인
        if (spotBookmarkRepository.existsByUserAndSpot(user, spot)) {
            throw new CommonException(ErrorCode.BOOKMARK_ALREADY_EXISTS);
        }

        // SpotBookmark 엔티티 생성 + 저장
        SpotBookmark bookmark = SpotBookmark.builder()
                .user(user)
                .spot(spot)
                .attractionLevel(attractionLevel)
                .build();
        spotBookmarkRepository.save(bookmark);

        return BookmarkCreateResponse.of(bookmark);
    }

    /**
     * 찜하기 취소
     */
    @Transactional
    public void deleteBookmark(Long userId, Long spotId) {
        // 북마크 조회
        SpotBookmark bookmark = spotBookmarkRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new CommonException(ErrorCode.BOOKMARK_NOT_FOUND));

        spotBookmarkRepository.delete(bookmark);
    }

    /**
     * 끌림지수 조회
     * → SpotService.getSpotDetail() 에서 호출
     *
     * @return 끌림지수 (북마크 없으면 null)
     */
    @Transactional(readOnly = true)
    public EAttractionLevel getAttractionLevel(Long userId, Long spotId) {
        return spotBookmarkRepository.findByUserIdAndSpotId(userId, spotId)
                .map(SpotBookmark::getAttractionLevel)
                .orElse(null);
    }

    /**
     * 찜한 젬 목록 조회 (마이스팟)
     * → SpotController GET /me/bookmarked 에서 호출
     */
    @Transactional(readOnly = true)
    public MyBookmarksResponse getMyBookmarkedSpots(Long userId) {
        // 찜한 북마크 목록 조회 (최신순)
        List<SpotBookmark> bookmarks = spotBookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // 각 북마크 -> 스팟 사진 URL 조회 -> SpotSummary 변환
        List<SpotSummary> spotSummaries = bookmarks.stream()
                .map(bookmark -> {
                    Spot spot = bookmark.getSpot();
                    String fileUrl = spotPhotoRepository
                            .findFirstBySpotAndTypeOrderByCreatedAtDesc(spot, ESpotPhotoType.SPOT)
                            .map(SpotPhoto::getFileUrl)
                            .orElse(null);
                    return SpotSummary.of(spot, fileUrl);
                })
                .toList();

        return MyBookmarksResponse.of(spotSummaries);
    }

    /**
     * 찜한 젬 개수 조회
     * → SpotService.getMySpots() 에서 호출
     */
    @Transactional(readOnly = true)
    public Integer getBookmarkedCount(Long userId) {
        return spotBookmarkRepository.countByUserId(userId);
    }

    /**
     * 스팟의 찜 받은 개수 조회
     * → SpotService.getSpotDetail() 에서 호출
     */
    @Transactional(readOnly = true)
    public Integer getBookmarkedCountBySpot(Long spotId) {
        return spotBookmarkRepository.countBySpotId(spotId);
    }
}
