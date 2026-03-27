package com.app.communityhub.media.api;

import com.app.communityhub.auth.security.CurrentUserService;
import com.app.communityhub.media.attachment.MediaAttachmentService;
import com.app.communityhub.media.reservation.MediaReservationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaReservationService mediaReservationService;
    private final MediaAttachmentService mediaAttachmentService;
    private final CurrentUserService currentUserService;

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateMediaReservationResponse reserve(@Valid @RequestBody CreateMediaReservationRequest request) {
        return mediaReservationService.reserve(currentUserService.requireUserId(), request);
    }

    @PostMapping("/{mediaKey}/complete")
    public CompleteMediaResponse complete(@PathVariable String mediaKey) {
        return mediaReservationService.complete(currentUserService.requireUserId(), mediaKey);
    }

    @PostMapping("/read-urls")
    public List<ReadMediaUrlResponse> readUrls(@Valid @RequestBody ReadMediaUrlsRequest request) {
        return mediaAttachmentService.resolveReadUrls(request.mediaKeys());
    }
}
