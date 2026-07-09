package kr.ac.dongguk.individualresearch.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.draft.DraftRepository;
import kr.ac.dongguk.individualresearch.draft.DraftResponse;
import kr.ac.dongguk.individualresearch.draft.DraftService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final DraftRepository drafts;
    private final DraftService draftService;
    private final HwpxDocumentGenerator hwpx;
    private final InterviewPdfService pdf;
    private final Path root;
    private final Path sampleHwpx;

    public DocumentService(DocumentRepository documents, DraftRepository drafts, DraftService draftService,
                           HwpxDocumentGenerator hwpx, InterviewPdfService pdf,
                           @Value("${app.storage.root-path:./storage}") String root,
                           @Value("${app.documents.sample-hwpx-path:../samples/2. 2026-여름학기 개별연구 수강신청원(학생용).hwpx}") String sampleHwpx) {
        this.documents=documents; this.drafts=drafts; this.draftService=draftService;
        this.hwpx=hwpx; this.pdf=pdf; this.root=Path.of(root).toAbsolutePath().normalize();
        this.sampleHwpx=Path.of(sampleHwpx).toAbsolutePath().normalize();
    }

    public DocumentGenerationResponse applicationHwpx(PublicUser user, long draftId) {
        DraftResponse draft = draftService.get(user, draftId);
        TemplateRecord template = documents.activeTemplate(draft.noticeId()).orElse(null);
        Path templatePath;
        Long templateId;
        if (template != null) {
            templatePath = Path.of(template.filePath());
            templateId = template.id();
        } else if (Files.isRegularFile(sampleHwpx)) {
            templatePath = sampleHwpx;
            templateId = null;
        } else {
            throw new DocumentException("TEMPLATE_NOT_FOUND", "활성 HWPX 템플릿과 샘플 HWPX가 없습니다.");
        }
        String filename = "개별연구_수강신청원_" + FileSupport.safeName(draft.studentName()) + ".hwpx";
        Path target = target("generated/application", draftId + "_" + filename);
        createParent(target);
        hwpx.generate(templatePath, target, draft);
        long id = documents.insertGenerated(draftId, templateId, "APPLICATION_HWPX", filename,
                target.toString(), FileSupport.sha256(target));
        drafts.markGenerated(draftId);
        return response(id, "APPLICATION_HWPX", filename);
    }

    public DocumentGenerationResponse interviewPdf(PublicUser user, long draftId) {
        DraftResponse draft = draftService.get(user, draftId);
        String filename = "개별연구_인터뷰자료_" + FileSupport.safeName(draft.studentName()) + ".pdf";
        Path target = target("generated/interview", draftId + "_" + filename);
        createParent(target);
        try {
            Files.write(target, pdf.generate(draft));
        } catch (IOException e) {
            throw new DocumentException("FILE_STORAGE_FAILED", "생성된 PDF를 저장하지 못했습니다.");
        }
        long id = documents.insertGenerated(draftId, null, "INTERVIEW_PDF", filename,
                target.toString(), FileSupport.sha256(target));
        drafts.markGenerated(draftId);
        return response(id, "INTERVIEW_PDF", filename);
    }

    public Download download(PublicUser user, long id) {
        GeneratedDocument document = documents.generated(id)
                .orElseThrow(() -> new DocumentException("DOCUMENT_NOT_FOUND", "생성 문서를 찾을 수 없습니다."));
        draftService.get(user, document.draftId());
        return read(document.filename(), document.documentType(), Path.of(document.filePath()));
    }

    public Download templateDownload(long id) {
        TemplateRecord template = documents.template(id)
                .orElseThrow(() -> new DocumentException("TEMPLATE_NOT_FOUND", "템플릿을 찾을 수 없습니다."));
        return read(template.originalFilename(), "APPLICATION_HWPX", Path.of(template.filePath()));
    }

    private Download read(String filename, String type, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || !Files.isRegularFile(normalized)) {
            throw new DocumentException("DOCUMENT_NOT_FOUND", "다운로드할 파일이 없습니다.");
        }
        try {
            String media = type.endsWith("PDF") ? "application/pdf" : "application/vnd.hancom.hwpx";
            return new Download(filename, media, Files.readAllBytes(normalized));
        } catch (IOException e) {
            throw new DocumentException("FILE_STORAGE_FAILED", "파일을 읽지 못했습니다.");
        }
    }

    private Path target(String directory, String filename) {
        Path path = root.resolve(directory).resolve(FileSupport.safeName(filename)).normalize();
        if (!path.startsWith(root)) throw new DocumentException("FILE_STORAGE_FAILED", "잘못된 파일 경로입니다.");
        return path;
    }
    private void createParent(Path path) {
        try { Files.createDirectories(path.getParent()); }
        catch (IOException e) { throw new DocumentException("FILE_STORAGE_FAILED", "저장 폴더를 만들지 못했습니다."); }
    }
    private DocumentGenerationResponse response(long id, String type, String filename) {
        return new DocumentGenerationResponse(id, type, filename, "/api/documents/" + id + "/download");
    }
    public record Download(String filename, String contentType, byte[] content) {}
}
