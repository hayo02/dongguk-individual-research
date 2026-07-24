package kr.ac.dongguk.individualresearch.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
