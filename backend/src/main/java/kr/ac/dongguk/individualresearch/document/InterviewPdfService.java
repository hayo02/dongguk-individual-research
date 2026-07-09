package kr.ac.dongguk.individualresearch.document;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import kr.ac.dongguk.individualresearch.draft.DraftResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class InterviewPdfService {
    private final TemplateEngine templates;
    private final Path font;

    public InterviewPdfService(TemplateEngine templates,
            @Value("${app.pdf.font-path:C:/Windows/Fonts/malgun.ttf}") String fontPath) {
        this.templates = templates;
        this.font = Path.of(fontPath);
    }

    public byte[] generate(DraftResponse draft) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("draft", draft);
        context.setVariable("courseName", blank(draft.courseName()) ? "개별연구" : draft.courseName());
        String html = templates.process("interview/interview-template", context).stripLeading();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            if (Files.isRegularFile(font)) {
                builder.useFont(font.toFile(), "KoreanDocumentFont");
            } else {
                throw new DocumentException("PDF_FONT_NOT_FOUND",
                        "PDF 한글 폰트를 찾을 수 없습니다: " + font);
            }
            builder.withHtmlContent(html, new File(".").toURI().toString());
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (DocumentException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentException("PDF_GENERATION_FAILED", "인터뷰 자료 PDF를 생성하지 못했습니다.", e);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
