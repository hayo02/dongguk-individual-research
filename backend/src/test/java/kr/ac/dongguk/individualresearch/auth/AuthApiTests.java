package kr.ac.dongguk.individualresearch.auth;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTests {
    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void loginMeAndLogout() {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of("loginId", "2026123456", "password", "1234"),
                Map.class
        );

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map data = (Map) login.getBody().get("data");
        String accessToken = (String) data.get("accessToken");
        assertThat(accessToken).contains(".");

        ResponseEntity<Map> me = restTemplate.exchange(
                url("/api/auth/me"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)),
                Map.class
        );
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> logout = restTemplate.exchange(
                url("/api/auth/logout"),
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(accessToken)),
                Map.class
        );
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> revokedMe = restTemplate.exchange(
                url("/api/auth/me"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)),
                Map.class
        );
        assertThat(revokedMe.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void studentDashboardRequiresStudentToken() {
        String studentToken = login("2026123456", "1234");

        ResponseEntity<Map> dashboard = restTemplate.exchange(
                url("/api/student/dashboard"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                Map.class
        );

        assertThat(dashboard.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map data = (Map) dashboard.getBody().get("data");
        assertThat(data.get("applicationStatus")).isEqualTo("NO_APPLICATION");
        assertThat(data.get("primaryAction")).isEqualTo("개별연구 신청하기");
    }

    @Test
    void studentCanCreateApplicationAndReadCurrentApplication() {
        String studentToken = login("2026123456", "1234");
        String staffToken = login("2025123456", "5678");

        ResponseEntity<Map> current = restTemplate.exchange(
                url("/api/applications/me/current"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                Map.class
        );
        assertThat(current.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NOT_FOUND);

        Long courseId = firstCourseId(staffToken);
        ResponseEntity<Map> created = restTemplate.exchange(
                url("/api/applications"),
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("courseId", courseId),
                        authHeaders(studentToken)
                ),
                Map.class
        );
        assertThat(created.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);

        ResponseEntity<Map> currentAfterCreate = restTemplate.exchange(
                url("/api/applications/me/current"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(studentToken)),
                Map.class
        );
        assertThat(currentAfterCreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map currentData = (Map) currentAfterCreate.getBody().get("data");
        assertThat(currentData.get("status")).isIn("DRAFT", "SUBMITTED");
        assertThat(currentData.get("student")).isNotNull();
        assertThat(currentData.get("course")).isNotNull();
    }

    private String login(String loginId, String password) {
        ResponseEntity<Map> login = restTemplate.postForEntity(
                url("/api/auth/login"),
                Map.of("loginId", loginId, "password", password),
                Map.class
        );
        Map data = (Map) login.getBody().get("data");
        return (String) data.get("accessToken");
    }

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private Long firstCourseId(String accessToken) {
        ResponseEntity<Map> courses = restTemplate.exchange(
                url("/api/courses"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(accessToken)),
                Map.class
        );
        Map data = (Map) courses.getBody().get("data");
        List items = (List) data.get("courses");
        Map firstCourse = (Map) items.get(0);
        return ((Number) firstCourse.get("id")).longValue();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
