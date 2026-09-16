package kr.ac.dongguk.individualresearch.staff;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrawlingResultServiceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode fixture() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"notice":{"title":"공지","url":"https://cs.dongguk.edu/notice","body_html":"<script>bad()</script>"},
                 "crawled_at":"2026-07-13T17:46:47+09:00","research_items":[{"과목명":"연구","교원명":"교수"}],
                 "attachments":[{"name":"과목.xlsx","download_success":true,"parse_success":true,
                   "download_url":"https://cs.dongguk.edu/file","local_path":"private-path","analysis":{"secret":"private-analysis"}}],
                 "warnings":[],"errors":[],"schedule":{"application_start":{"value":null,"status":"NOT_FOUND"}},
                 "snapshot_path":"private-snapshot"}
                """);
    }

    private CrawlingResultService service() {
        return new CrawlingResultService(mapper, directory.resolve("latest.json").toString());
    }

    private void save(ObjectNode source) throws Exception {
        mapper.writeValue(directory.resolve("latest.json").toFile(), source);
    }

    @Test void reportsMissingAndInvalidFilesSeparately() throws Exception {
        assertThat(service().latest().path("status").asText()).isEqualTo("NOT_FOUND");
        Files.writeString(directory.resolve("latest.json"), "broken-json");
        assertThat(service().latest().path("status").asText()).isEqualTo("INVALID");
        Files.writeString(directory.resolve("latest.json"), "{}");
        assertThat(service().latest().path("available").asBoolean()).isFalse();
    }

    @Test void returnsDisplayFieldsWithoutHtmlOrInternalFilePaths() throws Exception {
        save(fixture());
        var result = service().latest();
        assertThat(result.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(result.path("researchItems").size()).isEqualTo(1);
        assertThat(result.path("schedule").path("application_start").path("value").isNull()).isTrue();
        assertThat(result.toString()).doesNotContain("private-path", "private-analysis", "private-snapshot", "<script>");
    }

    @Test void refreshesFileAndDistinguishesWarningsErrorsAndAttachmentFailures() throws Exception {
        var source = fixture();
        source.withArray("warnings").addObject().put("message", "연구 내용 없음").put("row", 41);
        save(source);
        var service = service();
        assertThat(service.latest().path("status").asText()).isEqualTo("NEEDS_REVIEW");
        source.withArray("errors").addObject().put("message", "수집 실패");
        save(source);
        assertThat(service.latest().path("status").asText()).isEqualTo("ERROR");
        source.putArray("errors"); source.putArray("warnings");
        ((ObjectNode) source.path("attachments").get(0)).put("parse_success", false);
        save(source);
        assertThat(service.latest().path("status").asText()).isEqualTo("ERROR");
    }

    @Test void rejectsExecutableAndCredentialBearingLinks() throws Exception {
        var source = fixture();
        ((ObjectNode) source.path("notice")).put("url", "javascript:alert(1)");
        ((ObjectNode) source.path("attachments").get(0)).put("download_url", "https://user:password@example.com/file");
        save(source);
        var result = service().latest();
        assertThat(result.path("notice").path("url").asText()).isEmpty();
        assertThat(result.path("attachments").get(0).path("url").asText()).isEmpty();
    }
}
