package kr.ac.dongguk.individualresearch.application.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ApplicationFileStorageService {
    private static final Set<String> EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");
    private final Path root;
    private final long maxBytes;

    public ApplicationFileStorageService(
            @Value("${app.storage.root-path:./storage}") String root,
            @Value("${app.storage.upload-max-bytes:10485760}") long maxBytes
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredFile store(long applicationId, MultipartFile file) {
        validate(file);
        String original = safeOriginal(file.getOriginalFilename());
        String extension = extension(original);
        String storedName = UUID.randomUUID() + "." + extension;
        Path directory = root.resolve("uploads/applications").resolve(Long.toString(applicationId)).normalize();
        Path target = directory.resolve(storedName).normalize();
        ensureInside(target);
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
            try {
                file.transferTo(temporary);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredFile(
                    original, storedName, target.toString(), contentType(extension),
                    Files.size(target), sha256(target));
        } catch (IOException exception) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_UPLOAD_FAILED", "파일을 저장하지 못했습니다.", exception);
        }
    }

    public Path quarantine(Path source) {
        Path normalized = source.toAbsolutePath().normalize();
        ensureInside(normalized);
        if (!Files.isRegularFile(normalized)) {
            throw new ApplicationFileException("APPLICATION_FILE_NOT_FOUND", "저장된 파일을 찾을 수 없습니다.");
        }
        Path trash = root.resolve(".trash").resolve(UUID.randomUUID() + "_" + normalized.getFileName()).normalize();
        ensureInside(trash);
        try {
            Files.createDirectories(trash.getParent());
            return Files.move(normalized, trash, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_DELETE_FAILED", "기존 파일을 안전하게 이동하지 못했습니다.", exception);
        }
    }

    public void restore(Path quarantined, Path original) {
        try {
            Files.createDirectories(original.getParent());
            Files.move(quarantined, original, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The original exception remains authoritative; the trash file is retained for recovery.
        }
    }

    public void deleteQuietly(Path path) {
        try {
            if (path != null && path.toAbsolutePath().normalize().startsWith(root)) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Orphaned files are safe in storage/.trash and may be cleaned by maintenance.
        }
    }

    public Path readablePath(String filePath) {
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        ensureInside(path);
        if (!Files.isRegularFile(path)) {
            throw new ApplicationFileException("APPLICATION_FILE_NOT_FOUND", "저장된 파일을 찾을 수 없습니다.");
        }
        return path;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new ApplicationFileException("APPLICATION_FILE_INVALID_TYPE", "업로드할 파일을 선택해 주세요.");
        }
        if (file.getSize() > maxBytes) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_TOO_LARGE", "파일 크기는 10MB 이하여야 합니다.");
        }
        String extension = extension(file.getOriginalFilename());
        if (!EXTENSIONS.contains(extension)) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_INVALID_TYPE", "PDF, JPG, JPEG, PNG 파일만 업로드할 수 있습니다.");
        }
    }

    private String safeOriginal(String name) {
        String fileName = Path.of(name).getFileName().toString();
        return fileName.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void ensureInside(Path path) {
        if (!path.startsWith(root)) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_FORBIDDEN", "허용되지 않은 파일 경로입니다.");
        }
    }

    public record StoredFile(
            String originalName,
            String storedName,
            String path,
            String contentType,
            long size,
            String hash
    ) {}
}
