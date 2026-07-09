package kr.ac.dongguk.individualresearch.application.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ApplicationFileStorageServiceTest {
    @TempDir
    Path temporary;

    @Test
    void storesPdfJpgAndPngInsideApplicationDirectory() {
        ApplicationFileStorageService storage =
                new ApplicationFileStorageService(temporary.toString(), 10 * 1024 * 1024);

        for (String name : new String[]{"서명.pdf", "서명.jpg", "서명.jpeg", "서명.png"}) {
            var stored = storage.store(7L, new MockMultipartFile(
                    "file", name, "application/octet-stream", "content".getBytes(StandardCharsets.UTF_8)));
            assertThat(Path.of(stored.path())).isRegularFile().startsWith(temporary);
            assertThat(stored.originalName()).isEqualTo(name);
        }
    }

    @Test
    void rejectsUnsupportedOrOversizedFiles() {
        ApplicationFileStorageService storage = new ApplicationFileStorageService(temporary.toString(), 4);

        assertThatThrownBy(() -> storage.store(1L,
                new MockMultipartFile("file", "bad.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(ApplicationFileException.class)
                .hasMessageContaining("PDF");
        assertThatThrownBy(() -> storage.store(1L,
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[5])))
                .isInstanceOf(ApplicationFileException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void quarantineAllowsSafeDeleteAndRestore() throws Exception {
        ApplicationFileStorageService storage =
                new ApplicationFileStorageService(temporary.toString(), 1024);
        var stored = storage.store(1L,
                new MockMultipartFile("file", "sign.pdf", "application/pdf", new byte[]{1, 2, 3}));
        Path original = Path.of(stored.path());

        Path quarantined = storage.quarantine(original);
        assertThat(original).doesNotExist();
        assertThat(quarantined).exists();

        storage.restore(quarantined, original);
        assertThat(original).exists();
        assertThat(Files.readAllBytes(original)).containsExactly(1, 2, 3);
    }
}
