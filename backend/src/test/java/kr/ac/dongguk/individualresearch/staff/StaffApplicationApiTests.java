package kr.ac.dongguk.individualresearch.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class StaffApplicationApiTests {
    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configureHttpClient() {
        // Read 401 POST responses directly instead of HttpURLConnection's streaming retry.
        restTemplate.getRestTemplate().setRequestFactory(
                new org.springframework.http.client.JdkClientHttpRequestFactory());
    }

    @Test
    void staffCanFilterApplicationsAndReadDetail() {
        String studentToken = login("2026123456", "1234");
        String staffToken = login("2025123456", "5678");
        long applicationId = createSubmittedApplication(studentToken, staffToken);

        ResponseEntity<Map> list = restTemplate.exchange(
                url("/api/staff/applications?status=SUBMITTED&studentName=테스트&studentLoginId=2026123456"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(staffToken)),
                Map.class
        );
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map listData = (Map) list.getBody().get("data");
        assertThat(((Number) listData.get("totalElements")).longValue()).isEqualTo(1);
        List applications = (List) listData.get("applications");
        assertThat(((Number) ((Map) applications.get(0)).get("id")).longValue()).isEqualTo(applicationId);

        ResponseEntity<Map> detail = restTemplate.exchange(
                url("/api/staff/applications/" + applicationId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(staffToken)),
                Map.class
        );
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map detailData = (Map) detail.getBody().get("data");
        assertThat(detailData.get("status")).isEqualTo("SUBMITTED");
        assertThat(((Map) detailData.get("student")).get("loginId")).isEqualTo("2026123456");
        assertThat(detailData.get("files")).isNotNull();
        assertThat(detailData.get("reviewHistories")).isNotNull();

        ResponseEntity<Map> revision = restTemplate.exchange(
                url("/api/staff/applications/" + applicationId + "/revision-request"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "reason", "서명본을 선명하게 다시 제출해 주세요.",
                                "requireSignedApplication", true,
                                "revisionItems", List.of("SIGNED_APPLICATION")
                        ),
                        authHeaders(staffToken)
                ),
                Map.class
        );
        assertThat(revision.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) revision.getBody().get("data")).get("status"))
                .isEqualTo("REVISION_REQUESTED");

        ResponseEntity<Map> studentDashboard = restTemplate.exchange(
                url("/api/student/dashboard"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                Map.class
        );
        Map dashboardData = (Map) studentDashboard.getBody().get("data");
        assertThat(dashboardData.get("applicationStatus")).isEqualTo("REVISION_REQUESTED");
        Map notification = (Map) dashboardData.get("notification");
        assertThat(notification.get("type")).isEqualTo("REVISION_REQUESTED");
        assertThat((String) notification.get("message")).contains("서명본");

        ResponseEntity<Map> studentApplication = restTemplate.exchange(
                url("/api/applications/me/current"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                Map.class
        );
        List histories = (List) ((Map) studentApplication.getBody().get("data")).get("reviewHistories");
        assertThat(histories).hasSize(1);
    }

    @Test
    void studentCannotUseStaffApplicationApi() {
        String studentToken = login("2026123456", "1234");
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/staff/applications"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void approvalUpdatesHistoryAndStudentStatusAndRejectsDuplicate() {
        String studentToken = login("2026123456", "1234");
        String staffToken = login("2025123456", "5678");
        long id = createSubmittedApplication(studentToken, staffToken);
        var submittedAt = jdbcTemplate.queryForObject(
                "SELECT submitted_at FROM applications WHERE id=?", java.sql.Timestamp.class, id);

        ResponseEntity<Map> response = approve(id, staffToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map data = (Map) response.getBody().get("data");
        assertThat(data.get("status")).isEqualTo("APPROVED");
        List histories = (List) data.get("reviewHistories");
        assertThat(histories).hasSize(1);
        Map history = (Map) histories.get(0);
        assertThat(history.get("previousStatus")).isEqualTo("SUBMITTED");
        assertThat(history.get("changedStatus")).isEqualTo("APPROVED");
        assertThat(history.get("reviewedAt")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reviewer_id FROM application_review_history WHERE application_id=?", Long.class, id))
                .isEqualTo(jdbcTemplate.queryForObject("SELECT id FROM users WHERE login_id='2025123456'", Long.class));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT submitted_at FROM applications WHERE id=?", java.sql.Timestamp.class, id)).isEqualTo(submittedAt);

        ResponseEntity<Map> dashboard = restTemplate.exchange(url("/api/student/dashboard"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)), Map.class);
        assertThat(((Map) dashboard.getBody().get("data")).get("applicationStatus")).isEqualTo("APPROVED");
        Map notification = (Map) ((Map) dashboard.getBody().get("data")).get("notification");
        assertThat(notification.get("type")).isEqualTo("APPROVED");
        assertThat((String) notification.get("title")).contains("승인");
        assertThat(notification.get("createdAt")).isNotNull();
        ResponseEntity<Map> studentDetail = restTemplate.exchange(url("/api/applications/me/current"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)), Map.class);
        Map studentData = (Map) studentDetail.getBody().get("data");
        assertThat(studentData.get("status")).isEqualTo("APPROVED");
        assertThat(((Map) ((List) studentData.get("reviewHistories")).get(0)).get("changedStatus"))
                .isEqualTo("APPROVED");
        assertThat(approve(id, staffToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_review_history WHERE application_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void approvalRequiresStaffAndExistingSubmittedApplication() {
        String studentToken = login("2026123456", "1234");
        String staffToken = login("2025123456", "5678");
        long id = createSubmittedApplication(studentToken, staffToken);
        assertThat(approve(id, studentToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.postForEntity(url("/api/staff/applications/" + id + "/approve"), null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(approve(Long.MAX_VALUE, staffToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (String status : List.of("DRAFT", "REVISION_REQUESTED", "REJECTED")) {
            jdbcTemplate.update("UPDATE applications SET status=? WHERE id=?", status, id);
            assertThat(approve(id, staffToken).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM applications WHERE id=?", String.class, id))
                    .isEqualTo(status);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM application_review_history WHERE application_id=?", Integer.class, id)).isZero();
    }

    @Test
    void crawlingResultsRequireStaffAuthentication() {
        String staffToken = login("2025123456", "5678");
        String studentToken = login("2026123456", "1234");
        var allowed = restTemplate.exchange(url("/api/staff/crawling/latest"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(staffToken)), Map.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) allowed.getBody().get("data")).get("available")).isInstanceOf(Boolean.class);
        var denied = restTemplate.exchange(url("/api/staff/crawling/latest"), HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)), Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/api/staff/crawling/latest"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> approve(long id, String token) {
        return restTemplate.exchange(url("/api/staff/applications/" + id + "/approve"), HttpMethod.POST,
                new HttpEntity<>(authHeaders(token)), Map.class);
    }

    private long createSubmittedApplication(String studentToken, String staffToken) {
        long courseId = firstCourseId(staffToken);
        ResponseEntity<Map> created = restTemplate.exchange(
                url("/api/applications"),
                HttpMethod.POST,
                new HttpEntity<>(Map.of("courseId", courseId), authHeaders(studentToken)),
                Map.class
        );
        Map createdData = (Map) created.getBody().get("data");
        long applicationId = ((Number) createdData.get("applicationId")).longValue();
        jdbcTemplate.update(
                "UPDATE applications SET status='SUBMITTED', submitted_at=CURRENT_TIMESTAMP WHERE id=?",
                applicationId
        );
        return applicationId;
    }

    private long firstCourseId(String token) {
        ResponseEntity<Map> response = restTemplate.exchange(
                url("/api/courses"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                Map.class
        );
        Map data = (Map) response.getBody().get("data");
        return ((Number) ((Map) ((List) data.get("courses")).get(0)).get("id")).longValue();
    }

    private String login(String loginId, String password) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of("loginId", loginId, "password", password),
                Map.class
        );
        return (String) ((Map) response.getBody().get("data")).get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
