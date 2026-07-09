package kr.ac.dongguk.individualresearch.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HwpxTemplateService {
    public static final Set<String> REQUIRED = Set.of(
            "{{student_name}}", "{{student_number}}", "{{department}}",
            "{{professor_name}}", "{{research_title}}");
    private final DocumentRepository repository;
    private final Path root;

    public HwpxTemplateService(DocumentRepository repository,
            @Value("${app.storage.root-path:./storage}") String root) {
        this.repository = repository;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public TemplateUploadResponse upload(MultipartFile file, String name, Long noticeId,
                                         String semester, Integer version, boolean active) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null ||
                !file.getOriginalFilename().toLowerCase().endsWith(".hwpx")) {
            throw new DocumentException("UNSUPPORTED_FILE_TYPE", "HWPX 파일만 업로드할 수 있습니다.");
        }
        try {
            byte[] bytes = file.getBytes();
            String hash = FileSupport.sha256(bytes);
            if (repository.hashExists(hash)) {
                throw new DocumentException("DUPLICATE_TEMPLATE", "동일한 HWPX 템플릿이 이미 등록되어 있습니다.");
            }
            Set<String> found = inspect(bytes);
            Set<String> missing = new HashSet<>(REQUIRED);
            missing.removeAll(found);
            if (!missing.isEmpty()) {
                throw new DocumentException("REQUIRED_PLACEHOLDER_MISSING",
                        "필수 placeholder가 없습니다: " + String.join(", ", missing));
            }
            int actualVersion = version == null ? repository.nextVersion(noticeId) : version;
            Path directory = root.resolve("templates");
            Files.createDirectories(directory);
            String stored = "individual_research_application_v" + actualVersion + "_" + hash.substring(0, 8) + ".hwpx";
            Path target = directory.resolve(stored).normalize();
            ensureInside(target);
            Files.copy(new ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
            long id = repository.insertTemplate(noticeId, semester,
                    name == null || name.isBlank() ? "개별연구 수강신청원" : name.trim(),
                    file.getOriginalFilename(), stored, target.toString(), actualVersion, hash, active);
            return new TemplateUploadResponse(repository.template(id).orElseThrow(), true, found, missing);
        } catch (DocumentException e) {
            throw e;
        } catch (IOException e) {
            throw new DocumentException("FILE_STORAGE_FAILED", "HWPX 템플릿을 저장하지 못했습니다.");
        }
    }

    public Set<String> inspect(byte[] bytes) {
        boolean sectionFound = false;
        Set<String> found = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.matches("Contents/section[^/]*\\.xml")) {
                    sectionFound = true;
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    REQUIRED.stream().filter(xml::contains).forEach(found::add);
                }
            }
        } catch (IOException e) {
            throw new DocumentException("INVALID_HWPX", "HWPX ZIP 구조를 읽을 수 없습니다.");
        }
        if (!sectionFound) {
            throw new DocumentException("INVALID_HWPX", "Contents/section*.xml 파일이 없습니다.");
        }
        return found;
    }

    public java.util.List<TemplateRecord> list() { return repository.templates(); }

    public void activate(long id) {
        TemplateRecord value = repository.template(id)
                .orElseThrow(() -> new DocumentException("TEMPLATE_NOT_FOUND", "템플릿을 찾을 수 없습니다."));
        repository.activate(id, value.noticeId());
    }

    public void deactivate(long id) {
        if (repository.template(id).isEmpty()) throw new DocumentException("TEMPLATE_NOT_FOUND", "템플릿을 찾을 수 없습니다.");
        repository.deactivate(id);
    }

    public TemplateRecord get(long id) {
        return repository.template(id).orElseThrow(() -> new DocumentException("TEMPLATE_NOT_FOUND", "템플릿을 찾을 수 없습니다."));
    }

    private void ensureInside(Path path) {
        if (!path.startsWith(root)) throw new DocumentException("FILE_STORAGE_FAILED", "잘못된 파일 저장 경로입니다.");
    }
}
