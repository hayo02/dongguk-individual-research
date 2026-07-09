package kr.ac.dongguk.individualresearch.document;

import kr.ac.dongguk.individualresearch.auth.*;
import kr.ac.dongguk.individualresearch.common.ApiResponse;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class DocumentController {
    private final AuthFacade auth;
    private final HwpxTemplateService templates;
    private final DocumentService documents;

    public DocumentController(AuthFacade auth, HwpxTemplateService templates, DocumentService documents) {
        this.auth=auth; this.templates=templates; this.documents=documents;
    }

    @PostMapping(value="/api/staff/document-templates", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TemplateUploadResponse>> upload(
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestPart("file") MultipartFile file, @RequestParam String templateName,
            @RequestParam(required=false) Long noticeId, @RequestParam(required=false) String semester,
            @RequestParam(required=false) Integer templateVersion,
            @RequestParam(defaultValue="false") boolean active) {
        auth.currentUser(authorization, UserRole.STAFF);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                templates.upload(file, templateName, noticeId, semester, templateVersion, active)));
    }

    @GetMapping("/api/staff/document-templates")
    public ApiResponse<java.util.List<TemplateRecord>> list(
            @RequestHeader(value="Authorization", required=false) String authorization) {
        auth.currentUser(authorization, UserRole.STAFF);
        return ApiResponse.ok(templates.list());
    }

    @PatchMapping("/api/staff/document-templates/{id}/activate")
    public ApiResponse<Void> activate(@RequestHeader(value="Authorization", required=false) String authorization,
                                      @PathVariable long id) {
        auth.currentUser(authorization, UserRole.STAFF); templates.activate(id);
        return ApiResponse.okMessage("활성 템플릿을 변경했습니다.");
    }

    @DeleteMapping("/api/staff/document-templates/{id}")
    public ApiResponse<Void> deactivate(@RequestHeader(value="Authorization", required=false) String authorization,
                                        @PathVariable long id) {
        auth.currentUser(authorization, UserRole.STAFF); templates.deactivate(id);
        return ApiResponse.okMessage("템플릿을 비활성화했습니다.");
    }

    @GetMapping("/api/staff/document-templates/{id}/download")
    public ResponseEntity<byte[]> templateDownload(@RequestHeader(value="Authorization", required=false) String authorization,
                                                   @PathVariable long id) {
        auth.currentUser(authorization, UserRole.STAFF);
        return download(documents.templateDownload(id));
    }

    @PostMapping("/api/drafts/{id}/documents/application-hwpx")
    public ApiResponse<DocumentGenerationResponse> hwpx(
            @RequestHeader(value="Authorization", required=false) String authorization, @PathVariable long id) {
        return ApiResponse.ok(documents.applicationHwpx(auth.currentUser(authorization, UserRole.STUDENT), id));
    }

    @PostMapping("/api/drafts/{id}/documents/interview-pdf")
    public ApiResponse<DocumentGenerationResponse> pdf(
            @RequestHeader(value="Authorization", required=false) String authorization, @PathVariable long id) {
        return ApiResponse.ok(documents.interviewPdf(auth.currentUser(authorization, UserRole.STUDENT), id));
    }

    @GetMapping("/api/documents/{id}/download")
    public ResponseEntity<byte[]> generatedDownload(
            @RequestHeader(value="Authorization", required=false) String authorization, @PathVariable long id) {
        return download(documents.download(auth.currentUser(authorization, UserRole.STUDENT), id));
    }

    private ResponseEntity<byte[]> download(DocumentService.Download value) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(value.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(value.content());
    }
}
