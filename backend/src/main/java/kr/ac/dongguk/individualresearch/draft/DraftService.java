package kr.ac.dongguk.individualresearch.draft;

import kr.ac.dongguk.individualresearch.auth.PublicUser;
import kr.ac.dongguk.individualresearch.document.DocumentException;
import org.springframework.stereotype.Service;

@Service
public class DraftService {
    private final DraftRepository repository;

    public DraftService(DraftRepository repository) {
        this.repository = repository;
    }

    public DraftResponse create(PublicUser user, DraftRequest request) {
        DraftRequest value = request == null ? empty(user) : request;
        DraftResponse existing = repository.findLatest(user.id(), value.researchTopicId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        long id = repository.create(user.id(), value);
        return get(user, id);
    }

    public DraftResponse get(PublicUser user, long id) {
        DraftResponse draft = repository.find(id)
                .orElseThrow(() -> new DocumentException("DRAFT_NOT_FOUND", "신청 초안을 찾을 수 없습니다."));
        if (draft.userId() != user.id()) {
            throw new DocumentException("FORBIDDEN", "본인의 신청 초안만 조회할 수 있습니다.");
        }
        return draft;
    }

    public DraftResponse update(PublicUser user, long id, DraftRequest request) {
        get(user, id);
        if (request == null) throw new IllegalArgumentException("저장할 내용을 입력해 주세요.");
        repository.update(id, request);
        return get(user, id);
    }

    private DraftRequest empty(PublicUser user) {
        return new DraftRequest(null, null, null, user.name(), user.loginId(), user.department(),
                null, user.phone(), user.email(), null, null, null, "개별연구", null, null,
                null, null, null, DraftStatus.DRAFT);
    }
}
