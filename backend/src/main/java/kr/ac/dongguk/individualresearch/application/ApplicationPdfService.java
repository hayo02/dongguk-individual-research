package kr.ac.dongguk.individualresearch.application;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class ApplicationPdfService {
    private final ApplicationService applications;
    private final TemplateEngine templates;
    private final Path font;

    public ApplicationPdfService(ApplicationService applications, TemplateEngine templates,
            @Value("${app.pdf.font-path:C:/Windows/Fonts/malgun.ttf}") String fontPath) {
        this.applications = applications;
        this.templates = templates;
        this.font = Path.of(fontPath);
    }

    public ApplicationDocumentResponse generate(PublicUser student, long applicationId) {
        ApplicationRecord application = applications.findOwnedApplication(student, applicationId);
        Context context = new Context(Locale.KOREAN);
        context.setVariable("application", application);
        context.setVariable("contact", contact(application));
        context.setVariable("researchTitle", application.courseName());
        context.setVariable("researchContent", "");
        context.setVariable("relatedExperience", "");
        context.setVariable("researchPlan", "");
        context.setVariable("interviewQuestions", "");
        context.setVariable("generatedAt", LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        String html = templates.process("application/application-preview", context).stripLeading();

        if (!Files.isRegularFile(font)) {
            throw new ApplicationFlowException("APPLICATION_PDF_GENERATION_FAILED",
                    "PDF 한글 폰트를 찾을 수 없습니다: " + font);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(font.toFile(), "KoreanDocumentFont");
            builder.withHtmlContent(html, new File(".").toURI().toString());
            builder.toStream(output);
            builder.run();
            String filename = "개별연구_신청서_" + safe(application.studentName()) + ".pdf";
            return new ApplicationDocumentResponse(output.toByteArray(), filename, "application/pdf");
        } catch (Exception exception) {
            throw new ApplicationFlowException("APPLICATION_PDF_GENERATION_FAILED",
                    "신청서 PDF를 생성하지 못했습니다.");
        }
    }

    private String contact(ApplicationRecord application) {
        return StringUtils.hasText(application.contact()) ? application.contact() : application.studentPhone();
    }

    private String safe(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return cleaned.isBlank() ? "미입력" : cleaned;
    }
}
