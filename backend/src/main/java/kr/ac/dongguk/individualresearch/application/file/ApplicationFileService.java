package kr.ac.dongguk.individualresearch.application.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.ac.dongguk.individualresearch.application.ApplicationRecord;
import kr.ac.dongguk.individualresearch.application.ApplicationService;
import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.student.ApplicationStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ApplicationFileService {
    private final ApplicationService applications;
    private final ApplicationFileRepository repository;
    private final ApplicationFileStorageService storage;

    public ApplicationFileService(
            ApplicationService applications,
            ApplicationFileRepository repository,
            ApplicationFileStorageService storage
    ) {
        this.applications = applications;
        this.repository = repository;
        this.storage = storage;
    }

    public ApplicationFileListResponse list(PublicUser student, long applicationId) {
        applications.findOwnedApplication(student, applicationId);
        return new ApplicationFileListResponse(repository.findByApplicationId(applicationId)
                .stream().map(ApplicationFileResponse::from).toList());
    }

    public ApplicationFileResponse upload(
            PublicUser student,
            long applicationId,
            ApplicationFileDocumentType documentType,
            MultipartFile file
    ) {
        ApplicationRecord application = applications.findOwnedApplication(student, applicationId);
        ensureMutable(application);
        if (documentType == null) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_INVALID_TYPE", "문서 종류를 선택해 주세요.");
        }
        if (documentType == ApplicationFileDocumentType.SIGNED_APPLICATION &&
                repository.existsByType(applicationId, documentType)) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_DUPLICATE",
                    "교수 서명본은 1개만 등록할 수 있습니다. 기존 파일을 교체해 주세요.");
        }
        var stored = storage.store(applicationId, file);
        try {
            long id = repository.insert(
                    applicationId, documentType, stored.originalName(), stored.storedName(),
                    stored.path(), stored.contentType(), stored.size(), stored.hash(), student.id());
            return ApplicationFileResponse.from(required(id));
        } catch (RuntimeException exception) {
            storage.deleteQuietly(Path.of(stored.path()));
            throw exception;
        }
    }

    public ApplicationFileResponse replace(PublicUser student, long fileId, MultipartFile file) {
        ApplicationFileRecord current = ownedFile(student, fileId);
        ApplicationRecord application = applications.findOwnedApplication(student, current.applicationId());
        ensureMutable(application);
        var stored = storage.store(current.applicationId(), file);
        try {
            repository.update(
                    fileId, stored.originalName(), stored.storedName(), stored.path(),
                    stored.contentType(), stored.size(), stored.hash());
            storage.deleteQuietly(Path.of(current.filePath()));
            return ApplicationFileResponse.from(required(fileId));
        } catch (RuntimeException exception) {
            storage.deleteQuietly(Path.of(stored.path()));
            throw exception;
        }
    }

    public void delete(PublicUser student, long fileId) {
        ApplicationFileRecord current = ownedFile(student, fileId);
        ApplicationRecord application = applications.findOwnedApplication(student, current.applicationId());
        ensureMutable(application);
        Path original = Path.of(current.filePath()).toAbsolutePath().normalize();
        Path quarantined = storage.quarantine(original);
        try {
            repository.delete(fileId);
            storage.deleteQuietly(quarantined);
        } catch (RuntimeException exception) {
            storage.restore(quarantined, original);
            throw new ApplicationFileException(
                    "APPLICATION_FILE_DELETE_FAILED", "파일을 삭제하지 못했습니다.", exception);
        }
    }

    public Download download(PublicUser student, long fileId) {
        ApplicationFileRecord file = ownedFile(student, fileId);
        return download(file);
    }

    public Download downloadForStaff(long fileId) {
        return download(required(fileId));
    }

    private Download download(ApplicationFileRecord file) {
        Path path = storage.readablePath(file.filePath());
        try {
            return new Download(file.originalFileName(), file.contentType(), Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new ApplicationFileException(
                    "APPLICATION_FILE_DOWNLOAD_FAILED", "파일을 다운로드하지 못했습니다.", exception);
        }
    }

    private ApplicationFileRecord ownedFile(PublicUser student, long fileId) {
        ApplicationFileRecord file = required(fileId);
        applications.findOwnedApplication(student, file.applicationId());
        return file;
    }

    private ApplicationFileRecord required(long fileId) {
        return repository.findById(fileId).orElseThrow(() ->
                new ApplicationFileException("APPLICATION_FILE_NOT_FOUND", "제출 파일을 찾을 수 없습니다."));
    }

    private void ensureMutable(ApplicationRecord application) {
        if (application.status() == ApplicationStatus.SUBMITTED ||
                application.status() == ApplicationStatus.APPROVED ||
                application.status() == ApplicationStatus.REJECTED) {
            throw new ApplicationFileException(
                    "APPLICATION_INVALID_STATUS", "제출 완료 후에는 파일을 변경할 수 없습니다.");
        }
    }

    public record Download(String filename, String contentType, byte[] content) {}
}
