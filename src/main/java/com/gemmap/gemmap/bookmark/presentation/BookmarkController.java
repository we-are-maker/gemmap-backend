package com.gemmap.gemmap.bookmark.presentation;

import com.gemmap.gemmap.bookmark.application.dto.response.BookmarkCreateResponse;
import com.gemmap.gemmap.bookmark.application.service.BookmarkService;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookmarks")
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;

    /**
     * 젬 찜하기
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BookmarkCreateResponse> createBookmark(
            @UserId Long userId,
            @RequestParam Long spotId,
            @RequestParam EAttractionLevel attractionLevel
    ){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookmarkService.createBookmark(userId, spotId, attractionLevel));
    }

    /**
     * 찜하기 취소
     */
    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> deleteBookmark(
            @UserId Long userId,
            @PathVariable Long spotId
    ) {
        bookmarkService.deleteBookmark(userId, spotId);
        return ResponseEntity.noContent().build();
    }
}
