package kr.ac.dongguk.individualresearch.staff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CrawlingResultService {
    private final ObjectMapper mapper;
    private final String snapshotPath;

    public CrawlingResultService(ObjectMapper mapper,
            @Value("${app.notice.snapshot-path:../data/snapshots/individual-research/latest.json}") String snapshotPath) {
        this.mapper = mapper;
        this.snapshotPath = snapshotPath;
    }

    public ObjectNode latest() {
        Path file = Path.of(snapshotPath);
        if (!Files.exists(file) && snapshotPath.equals("../data/snapshots/individual-research/latest.json")) {
            file = Path.of("data/snapshots/individual-research/latest.json");
        }
        if (!Files.exists(file)) return unavailable("NOT_FOUND", "아직 저장된 크롤링 결과가 없습니다.");
        try {
            JsonNode source = mapper.readTree(file.toFile());
            if (source == null || !source.isObject() || !source.path("notice").isObject()
                    || !source.path("research_items").isArray() || !source.path("attachments").isArray()
                    || !source.path("warnings").isArray() || !source.path("errors").isArray()) {
                return unavailable("INVALID", "크롤링 결과 파일의 형식을 확인해 주세요.");
            }
            ObjectNode result = mapper.createObjectNode();
            result.put("available", true);
            result.put("crawledAt", source.path("crawled_at").asText(""));
            result.put("parserVersion", source.path("parser_version").asText(""));
            result.put("comparisonStatus", source.path("comparison_status").asText("UNKNOWN"));
            result.set("notice", pick(source.path("notice"), "title", "written_at", "body_text"));
            ((ObjectNode) result.get("notice")).put("url", safeUrl(source.path("notice").path("url").asText()));
            result.set("warnings", issues(source.path("warnings")));
            result.set("errors", issues(source.path("errors")));
            var attachments = result.putArray("attachments");
            boolean attachmentFailure = false;
            for (JsonNode attachment : source.path("attachments")) {
                ObjectNode item = pick(attachment, "name", "role", "size", "download_success", "parse_success");
                item.put("url", safeUrl(attachment.path("download_url").asText()));
                if (!attachment.path("download_success").asBoolean() || !attachment.path("parse_success").asBoolean()) {
                    attachmentFailure = true;
                    item.put("message", "수집 또는 분석을 완료하지 못했습니다. 원문 첨부파일을 확인해 주세요.");
                }
                attachments.add(item);
            }
            var research = result.putArray("researchItems");
            for (JsonNode item : source.path("research_items")) {
                research.add(pick(item, "순번", "개설학부", "교원명", "과목명", "학수강좌번호", "연구내용",
                        "수강정원", "인터뷰 일정", "주당 연구시간", "수강 자격사항"));
            }
            ObjectNode schedule = result.putObject("schedule");
            for (String key : new String[]{"application_start", "application_deadline", "interview_period",
                    "document_submission_period", "course_registration_period", "research_period"}) {
                schedule.set(key, pick(source.path("schedule").path(key), "value", "status", "source_text"));
            }
            ObjectNode submission = result.putObject("submission");
            for (String key : new String[]{"method", "location", "contact", "email_submission_allowed",
                    "proxy_submission_allowed", "separate_course_registration_required"}) {
                submission.set(key, pick(source.path("submission").path(key), "value", "status", "source_text"));
            }
            var documents = result.putArray("requiredDocuments");
            for (JsonNode doc : source.path("submission").path("required_documents")) {
                documents.add(pick(doc, "name", "required", "requirement_type"));
            }
            var changes = result.putArray("changes");
            for (JsonNode change : source.path("changes")) {
                changes.add(pick(change, "field", "type", "message"));
            }
            result.put("status", !source.path("errors").isEmpty() || attachmentFailure ? "ERROR"
                    : !source.path("warnings").isEmpty() ? "NEEDS_REVIEW" : "SUCCESS");
            return result;
        } catch (Exception exception) {
            return unavailable("INVALID", "크롤링 결과 파일을 읽지 못했습니다. 파일 상태를 확인해 주세요.");
        }
    }

    private ObjectNode unavailable(String status, String message) {
        return mapper.createObjectNode().put("available", false).put("status", status).put("message", message);
    }

    private ObjectNode pick(JsonNode source, String... fields) {
        ObjectNode result = mapper.createObjectNode();
        for (String field : fields) if (source.has(field)) result.set(field, source.get(field));
        return result;
    }

    private JsonNode issues(JsonNode source) {
        var result = mapper.createArrayNode();
        for (JsonNode issue : source) {
            result.add(issue.isObject() ? pick(issue, "code", "message", "row", "fields")
                    : mapper.createObjectNode().put("message", issue.asText()));
        }
        return result;
    }

    private String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? value : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}
