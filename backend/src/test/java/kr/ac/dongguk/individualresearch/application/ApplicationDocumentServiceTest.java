package kr.ac.dongguk.individualresearch.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

class ApplicationDocumentServiceTest {
    @Test
    void hwpFillsOriginalTemplateCells() throws Exception {
        ApplicationDocumentService service = new ApplicationDocumentService(
                "../data/attachments/1390/2. 2026-여름학기 개별연구 수강신청원(학생용).hwp"
        );
        ApplicationRecord application = new ApplicationRecord(
                1L,
                10L,
                "2026123456",
                "홍길동",
                "컴퓨터·AI학과",
                "student@example.com",
                "010-0000-0000",
                "010-1111-2222",
                20L,
                30L,
                "2026-여름학기",
                "컴퓨터·AI학과",
                "개별연구 과제",
                "개별연구",
                "CSE0000",
                3,
                "김교수",
                ApplicationStatus.DRAFT,
                "신청 사유입니다.",
                "연구 목적입니다.",
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        byte[] document = service.hwp(application).content();
        byte[] section = section0(document);

        assertThat(section).contains("홍길동\r".getBytes(StandardCharsets.UTF_16LE));
        assertThat(section).contains("010-1111-2222\r".getBytes(StandardCharsets.UTF_16LE));
        assertThat(section).contains("김교수\r".getBytes(StandardCharsets.UTF_16LE));
    }

    private byte[] section0(byte[] document) throws Exception {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(document))) {
            DirectoryNode bodyText = (DirectoryNode) fileSystem.getRoot().getEntry("BodyText");
            byte[] compressed = bodyText.createDocumentInputStream("Section0").readAllBytes();
            try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed), new Inflater(true));
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                input.transferTo(output);
                return output.toByteArray();
            }
        }
    }
}
