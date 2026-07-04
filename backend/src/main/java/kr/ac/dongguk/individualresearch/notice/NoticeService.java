package kr.ac.dongguk.individualresearch.notice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NoticeService {
    private final NoticeRepository noticeRepository;
    private final ObjectMapper objectMapper;

    public NoticeService(NoticeRepository noticeRepository, ObjectMapper objectMapper) {
        this.noticeRepository = noticeRepository;
        this.objectMapper = objectMapper;
    }

    public Notice currentNotice() {
        return noticeRepository.findCurrent()
                .orElseThrow(() -> new IllegalArgumentException("신청 안내 공지가 없습니다."));
    }

    public Notice notice(long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지 정보를 찾을 수 없습니다."));
    }

    public NoticeResponse toResponse(Notice notice) {
        return new NoticeResponse(
                notice.id(),
                notice.title(),
                notice.semester(),
                notice.startDate(),
                notice.endDate(),
                notice.originalUrl(),
                notice.needsReview(),
                readList(notice.requiredDocumentsJson()),
                readMap(notice.scheduleInfoJson()),
                readMap(notice.submissionInfoJson()),
                notice.noticeNotes(),
                notice.bodyText(),
                notice.publishedAt()
        );
    }

    public NoticeSummary toSummary(Notice notice) {
        return new NoticeSummary(notice.id(), notice.title(), notice.publishedAt(), notice.needsReview());
    }

    private List<String> readList(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, String> readMap(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
