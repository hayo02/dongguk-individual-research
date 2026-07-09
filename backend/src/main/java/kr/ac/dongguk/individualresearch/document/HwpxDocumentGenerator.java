package kr.ac.dongguk.individualresearch.document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.*;
import kr.ac.dongguk.individualresearch.draft.DraftResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HwpxDocumentGenerator {
    private static final Logger log = LoggerFactory.getLogger(HwpxDocumentGenerator.class);

    public void generate(Path template, Path output, DraftResponse d) {
        Map<String,String> values = values(d);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(template));
             ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = input.readAllBytes();
                if (entry.getName().replace('\\','/').matches("Contents/section[^/]*\\.xml")) {
                    String xml = new String(content, StandardCharsets.UTF_8);
                    for (var value : values.entrySet()) xml = xml.replace(value.getKey(), value.getValue());
                    if (xml.matches("(?s).*\\{\\{[^}]+}}.*")) log.warn("Unreplaced placeholder remains in {}", entry.getName());
                    content = xml.getBytes(StandardCharsets.UTF_8);
                }
                ZipEntry copy = copyEntry(entry, content);
                zip.putNextEntry(copy);
                zip.write(content);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new DocumentException("HWPX_GENERATION_FAILED", "HWPX 문서를 생성하지 못했습니다.");
        }
    }

    private ZipEntry copyEntry(ZipEntry source, byte[] content) {
        ZipEntry target = new ZipEntry(source.getName());
        if (source.getTime() >= 0) target.setTime(source.getTime());
        if (source.getComment() != null) target.setComment(source.getComment());
        if (source.getExtra() != null) target.setExtra(source.getExtra());
        if (source.getMethod() == ZipEntry.STORED) {
            CRC32 crc = new CRC32();
            crc.update(content);
            target.setMethod(ZipEntry.STORED);
            target.setSize(content.length);
            target.setCompressedSize(content.length);
            target.setCrc(crc.getValue());
        } else {
            target.setMethod(ZipEntry.DEFLATED);
        }
        return target;
    }

    private Map<String,String> values(DraftResponse d) {
        Map<String,String> v = new LinkedHashMap<>();
        v.put("{{student_name}}", clean(d.studentName())); v.put("{{student_number}}", clean(d.studentNumber()));
        v.put("{{department}}", clean(d.department())); v.put("{{grade}}", clean(d.grade()));
        v.put("{{phone}}", clean(d.phone())); v.put("{{email}}", clean(d.email()));
        v.put("{{semester}}", clean(d.semester())); v.put("{{professor_name}}", clean(d.professorName()));
        v.put("{{research_title}}", clean(d.researchTitle())); v.put("{{research_content}}", clean(d.researchContent()));
        v.put("{{course_name}}", clean(d.courseName())); v.put("{{application_reason}}", clean(d.applicationReason()));
        v.put("{{research_purpose}}", clean(d.researchPurpose())); v.put("{{related_experience}}", clean(d.relatedExperience()));
        v.put("{{research_plan}}", clean(d.researchPlan())); v.put("{{interview_questions}}", clean(d.interviewQuestions()));
        return v;
    }
    private String clean(String value) {
        return value == null ? "" : value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
}
