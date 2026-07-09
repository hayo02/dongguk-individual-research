package kr.ac.dongguk.individualresearch.draft;

public record DraftRequest(
        Long noticeId, Long researchTopicId, String semester, String studentName,
        String studentNumber, String department, String grade, String phone, String email,
        String professorName, String researchTitle, String researchContent, String courseName,
        String applicationReason, String researchPurpose, String relatedExperience,
        String researchPlan, String interviewQuestions, DraftStatus status
) {}
