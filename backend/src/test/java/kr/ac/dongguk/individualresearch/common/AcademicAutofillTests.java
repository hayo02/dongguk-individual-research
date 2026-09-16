package kr.ac.dongguk.individualresearch.common;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import kr.ac.dongguk.individualresearch.application.ApplicationService;
import kr.ac.dongguk.individualresearch.auth.UserRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:academic_autofill_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AcademicAutofillTests {
    @Autowired DatabaseInitializer initializer;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationService applications;
    @Autowired UserRepository users;

    @Test void repeatedImportPreservesCourseReferencesAndAutofillsGrade() {
        var student = users.findByLoginId("2026123456").orElseThrow().toPublicUser();
        Long course = jdbc.queryForObject("SELECT MIN(id) FROM courses", Long.class);
        jdbc.update("INSERT INTO applications(student_id,course_id,status) VALUES (?,?,'REVISION_REQUESTED')", student.id(), course);
        var before = applications.current(student);
        initializer.run(null);
        var after = applications.current(student);
        assertThat(after.course().id()).isEqualTo(course);
        assertThat(after.course().semester()).isNotBlank().isEqualTo(before.course().semester());
        assertThat(after.course().professorName()).isNotBlank();
        assertThat(after.student().grade()).isEqualTo("3");
        assertThat(jdbc.queryForObject("SELECT grade FROM users WHERE login_id='2027123456'", String.class)).isEqualTo("4");
    }

    @Test void repairsOrphanOnlyWhenSavedDraftIdentifiesUniqueCourse() {
        var student = users.findByLoginId("2026123456").orElseThrow().toPublicUser();
        Long course = jdbc.queryForObject("SELECT MIN(id) FROM courses", Long.class);
        jdbc.update("INSERT INTO applications(student_id,course_id,status) VALUES (?,999999,'REVISION_REQUESTED')", student.id());
        jdbc.update("""
                INSERT INTO application_draft(user_id,research_topic_id,notice_id,professor_name,course_name,status)
                SELECT ?,999999,notice_id,professor_name,course_name,'DRAFT' FROM courses WHERE id=?
                """, student.id(), course);
        initializer.run(null);
        assertThat(applications.current(student).course().id()).isEqualTo(course);
        assertThat(jdbc.queryForObject("SELECT research_topic_id FROM application_draft WHERE user_id=?", Long.class, student.id())).isEqualTo(course);
    }
}
