package kr.ac.dongguk.individualresearch.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import kr.ac.dongguk.individualresearch.draft.DraftResponse;
import kr.ac.dongguk.individualresearch.draft.DraftStatus;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class DocumentGenerationServiceTest {
    @Test
    void replacesRequiredPlaceholdersInHwpx() throws Exception {
        Path template = Files.createTempFile("template", ".hwpx");
        Path output = Files.createTempFile("generated", ".hwpx");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(template))) {
            zip.putNextEntry(new ZipEntry("Contents/section0.xml"));
            zip.write("<root><p>{{student_name}}</p><p>{{professor_name}}</p></root>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        new HwpxDocumentGenerator().generate(template, output, draft());

        try (ZipFile zip = new ZipFile(output.toFile())) {
            String xml = new String(zip.getInputStream(zip.getEntry("Contents/section0.xml")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertThat(xml).contains("홍길동", "김교수").doesNotContain("{{student_name}}");
        }
    }

    @Test
    void createsKoreanInterviewPdf() throws Exception {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        InterviewPdfService service = new InterviewPdfService(engine, "C:/Windows/Fonts/malgun.ttf");

        byte[] result = service.generate(draft());

        assertThat(result).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        Path output = Path.of("../output/pdf/interview-sample.pdf");
        Files.createDirectories(output.getParent());
        Files.write(output, result);
    }

    @Test
    void sampleHwpxRemainsValidAndPreservesStoredMimetype() throws Exception {
        Path template = Path.of("../samples/2. 2026-여름학기 개별연구 수강신청원(학생용).hwpx");
        Path output = Files.createTempFile("sample-generated", ".hwpx");

        new HwpxDocumentGenerator().generate(template, output, draft());

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertThat(zip.getEntry("mimetype")).isNotNull();
            assertThat(zip.getEntry("mimetype").getMethod()).isEqualTo(ZipEntry.STORED);
            assertThat(zip.getEntry("Contents/section0.xml")).isNotNull();
            assertThat(zip.getEntry("META-INF/container.xml")).isNotNull();
        }
    }

    private DraftResponse draft() {
        LocalDateTime now = LocalDateTime.now();
        return new DraftResponse(1, 1, 1L, 1L, "2026학년도 여름학기", "홍길동",
                "2026123456", "컴퓨터·AI학부", "3", "010-1234-5678",
                "student@dongguk.edu", "김교수", "생성형 AI 연구", "연구 내용",
                "개별연구", "전공 지식을 실제 연구에 적용하고 싶어 신청합니다.",
                "생성형 AI 모델의 신뢰성을 분석하고 개선 방법을 탐구합니다.",
                "관련 프로젝트 경험", "주차별 연구 계획", "면담 질문", DraftStatus.DRAFT, now, now);
    }
}
