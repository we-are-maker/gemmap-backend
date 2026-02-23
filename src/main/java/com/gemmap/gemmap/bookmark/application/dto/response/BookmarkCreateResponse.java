package com.gemmap.gemmap.bookmark.application.dto.response;

import com.gemmap.gemmap.bookmark.domain.entity.SpotBookmark;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import lombok.Builder;

@Builder
public record BookmarkCreateResponse(
        Long bookmarkId,
        Long spotId,
        EAttractionLevel attractionLevel
) {

    public static BookmarkCreateResponse of(SpotBookmark bookmark) {
        return BookmarkCreateResponse.builder()
                .bookmarkId(bookmark.getId())
                .spotId(bookmark.getSpot().getId())
                .attractionLevel(bookmark.getAttractionLevel())
                .build();
    }
}
