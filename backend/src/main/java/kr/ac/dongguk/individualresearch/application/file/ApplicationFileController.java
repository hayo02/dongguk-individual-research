package kr.ac.dongguk.individualresearch.application.file;

import java.nio.charset.StandardCharsets;
import kr.ac.dongguk.individualresearch.auth.AuthFacade;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.auth.UserRole;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ApplicationFileController {
    private final AuthFacade auth;
    private final ApplicationFileService files;

    public ApplicationFileController(AuthFacade auth, ApplicationFileService files) {
        this.auth = auth;
        this.files = files;
    }

    @GetMapping("/api/applications/{applicationId}/files")
    public ApiResponse<ApplicationFileListResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long applicationId
    ) {
        return ApiResponse.ok(files.list(student(authorization), applicationId));
    }

    @PostMapping(value = "/api/applications/{applicationId}/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ApplicationFileResponse>> upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long applicationId,
            @RequestParam ApplicationFileDocumentType documentType,
            @RequestPart MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(files.upload(student(authorization), applicationId, documentType, file)));
    }

    @PutMapping(value = "/api/application-files/{fileId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ApplicationFileResponse> replace(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long fileId,
            @RequestPart MultipartFile file
    ) {
        return ApiResponse.ok(files.replace(student(authorization), fileId, file));
    }

    @DeleteMapping("/api/application-files/{fileId}")
    public ApiResponse<Void> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long fileId
    ) {
        files.delete(student(authorization), fileId);
        return ApiResponse.okMessage("제출 파일을 삭제했습니다.");
    }

    @GetMapping("/api/application-files/{fileId}/download")
    public ResponseEntity<byte[]> download(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long fileId
    ) {
        ApplicationFileService.Download value = files.download(student(authorization), fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(value.filename(), StandardCharsets.UTF_8).build().toString())
                .body(value.content());
    }

    @GetMapping("/api/staff/application-files/{fileId}/download")
    public ResponseEntity<byte[]> downloadForStaff(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long fileId
    ) {
        auth.currentUser(authorization, UserRole.STAFF);
        ApplicationFileService.Download value = files.downloadForStaff(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(value.filename(), StandardCharsets.UTF_8).build().toString())
                .body(value.content());
    }

    private PublicUser student(String authorization) {
        return auth.currentUser(authorization, UserRole.STUDENT);
    }
}
