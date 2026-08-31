package smartcampus.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import smartcampus.dto.NotificationDispatch;
import smartcampus.entity.ApplicationStatus;
import smartcampus.entity.ContestStatus;
import smartcampus.entity.InterviewStatus;
import smartcampus.entity.NotificationPriority;
import smartcampus.entity.NotificationReferenceType;
import smartcampus.entity.NotificationType;

/**
 * The single place every real-domain-event notification's title, message, link and
 * dedupe key are built, so wording (and the exact dedupe-key format the {@code
 * notifications} unique index relies on for flood control) is never duplicated across
 * {@code AttendanceService}, {@code JobService}, {@code PlacementApplicationService},
 * {@code InterviewSchedulingService}, {@code CodingContestService} or {@code
 * ContestScoringService}.
 *
 * <p>Every dedupe key here is derived ONLY from stable ids and the new state — never a
 * timestamp of "now", never a random value — because the key is what makes a retried or
 * re-run producer idempotent (see each method's javadoc for the exact key it emits).
 *
 * <p>The link targets below are the real routes verified in {@code AppRouter.tsx} (see
 * the Phase 11 contract §11): a link that points at a route which does not exist is a
 * §69 "button that does nothing".
 */
final class NotificationMessages {

    private NotificationMessages() {}

    // ------------------------------------------------------------------
    // A. ATTENDANCE_WARNING — AttendanceService.bulkMark
    // ------------------------------------------------------------------

    /**
     * One per (student, subject, academicYear, semester): {@code
     * "attendance-warning:<subjectId>:<academicYear>:<semester>"} so a student is warned
     * once per subject per term, never once per class marked.
     */
    static NotificationDispatch attendanceWarning(
            Long userId,
            Long subjectId,
            String subjectCode,
            String subjectName,
            String academicYear,
            Integer semester,
            BigDecimal percentage,
            BigDecimal minimumPercentage) {
        String title = "Low attendance warning";
        String message =
                "Your attendance in "
                        + subjectName
                        + " ("
                        + subjectCode
                        + ") for semester "
                        + semester
                        + " of "
                        + academicYear
                        + " is "
                        + percentage
                        + "%, below the required minimum of "
                        + minimumPercentage
                        + "%.";
        String dedupeKey = "attendance-warning:" + subjectId + ":" + academicYear + ":" + semester;
        return new NotificationDispatch(
                userId,
                NotificationType.ATTENDANCE_WARNING,
                title,
                message,
                NotificationPriority.HIGH,
                "/student/attendance",
                NotificationReferenceType.SUBJECT,
                subjectId,
                null,
                dedupeKey);
    }

    // ------------------------------------------------------------------
    // B. PLACEMENT_UPDATE — JobService.updateStatus (DRAFT/CLOSED -> OPEN)
    // ------------------------------------------------------------------

    /**
     * One per (job, recipient): {@code "job-open:<jobId>:<userId>"} so re-opening the
     * same drive later (a genuinely new event) still notifies, while a retried status
     * update does not double up.
     */
    static NotificationDispatch placementDriveOpen(Long userId, Long jobId, String jobTitle, String companyName) {
        String title = "New placement drive open";
        String message =
                "A new drive is open: " + jobTitle + " at " + companyName
                        + ". Check your eligibility and apply before the deadline.";
        String dedupeKey = "job-open:" + jobId + ":" + userId;
        return new NotificationDispatch(
                userId,
                NotificationType.PLACEMENT_UPDATE,
                title,
                message,
                NotificationPriority.NORMAL,
                "/student/jobs/" + jobId,
                NotificationReferenceType.JOB,
                jobId,
                null,
                dedupeKey);
    }

    // ------------------------------------------------------------------
    // C. APPLICATION_UPDATE — PlacementApplicationService.updateStatus / bulkUpdateStatus
    // ------------------------------------------------------------------

    /**
     * One per (application, new status): {@code
     * "application:<applicationId>:<to.name()>"} so a genuine second transition (e.g.
     * SHORTLISTED then SELECTED) notifies again, while a retried write of the same
     * transition does not.
     */
    static NotificationDispatch applicationStatusChanged(
            Long userId,
            Long applicationId,
            String jobTitle,
            ApplicationStatus from,
            ApplicationStatus to,
            String decisionNote) {
        String title = "Application update: " + jobTitle;
        StringBuilder message = new StringBuilder();
        message.append("Your application for ")
                .append(jobTitle)
                .append(" moved from ")
                .append(from.name())
                .append(" to ")
                .append(to.name())
                .append('.');
        if (decisionNote != null && !decisionNote.isBlank()) {
            message.append(" Note: ").append(decisionNote);
        }
        String dedupeKey = "application:" + applicationId + ":" + to.name();
        return new NotificationDispatch(
                userId,
                NotificationType.APPLICATION_UPDATE,
                title,
                message.toString(),
                NotificationPriority.HIGH,
                "/student/applications",
                NotificationReferenceType.PLACEMENT_APPLICATION,
                applicationId,
                null,
                dedupeKey);
    }

    // ------------------------------------------------------------------
    // D. INTERVIEW_UPDATE — InterviewSchedulingService.schedule/reschedule/updateStatus
    // ------------------------------------------------------------------

    /** {@code "interview:<interviewId>:SCHEDULED"} — fired once, when the interview is created. */
    static NotificationDispatch interviewScheduled(
            Long userId, Long interviewId, String interviewTitle, LocalDateTime scheduledStart) {
        String title = "Interview scheduled";
        String message = "An interview has been scheduled: \"" + interviewTitle + "\" on " + scheduledStart + ".";
        String dedupeKey = "interview:" + interviewId + ":" + InterviewStatus.SCHEDULED.name();
        return interviewDispatch(userId, interviewId, title, message, dedupeKey);
    }

    /**
     * {@code "interview:<interviewId>:<newScheduledStart>"} — keyed on the NEW start
     * instant (not just the status) so a genuine second reschedule notifies again while a
     * retried write of the same reschedule does not.
     */
    static NotificationDispatch interviewRescheduled(
            Long userId, Long interviewId, String interviewTitle, LocalDateTime newScheduledStart) {
        String title = "Interview rescheduled";
        String message =
                "Your interview \"" + interviewTitle + "\" has been rescheduled to " + newScheduledStart + ".";
        String dedupeKey = "interview:" + interviewId + ":" + newScheduledStart;
        return interviewDispatch(userId, interviewId, title, message, dedupeKey);
    }

    /** {@code "interview:<interviewId>:<newStatus.name()>"} — one per distinct target status. */
    static NotificationDispatch interviewStatusChanged(
            Long userId, Long interviewId, String interviewTitle, InterviewStatus newStatus) {
        String title = "Interview status update";
        String message =
                "Your interview \"" + interviewTitle + "\" status changed to " + newStatus.name() + ".";
        String dedupeKey = "interview:" + interviewId + ":" + newStatus.name();
        return interviewDispatch(userId, interviewId, title, message, dedupeKey);
    }

    private static NotificationDispatch interviewDispatch(
            Long userId, Long interviewId, String title, String message, String dedupeKey) {
        return new NotificationDispatch(
                userId,
                NotificationType.INTERVIEW_UPDATE,
                title,
                message,
                NotificationPriority.HIGH,
                "/student/interviews",
                NotificationReferenceType.INTERVIEW,
                interviewId,
                null,
                dedupeKey);
    }

    // ------------------------------------------------------------------
    // E. CONTEST_UPDATE — CodingContestService.update (window or status changed)
    // ------------------------------------------------------------------

    /**
     * {@code "contest:<contestId>:<newStartTime>_<newEndTime>_<newStatus>"} — the whole
     * new window+status is the signature, so an unchanged re-save (identical window and
     * status) never notifies anybody.
     */
    static NotificationDispatch contestUpdated(
            Long userId,
            Long contestId,
            String contestTitle,
            LocalDateTime startTime,
            LocalDateTime endTime,
            ContestStatus status) {
        String title = "Contest updated";
        String message =
                "The contest \"" + contestTitle + "\" has been updated. New window: " + startTime + " to "
                        + endTime + ", status " + status.name() + ".";
        String signature = startTime + "_" + endTime + "_" + status.name();
        String dedupeKey = "contest:" + contestId + ":" + signature;
        return new NotificationDispatch(
                userId,
                NotificationType.CONTEST_UPDATE,
                title,
                message,
                NotificationPriority.NORMAL,
                "/coding/contests/" + contestId,
                NotificationReferenceType.CONTEST,
                contestId,
                null,
                dedupeKey);
    }

    // ------------------------------------------------------------------
    // F. LEADERBOARD_UPDATE — ContestScoringService.recomputeContest
    // ------------------------------------------------------------------

    /**
     * {@code "leaderboard:<contestId>:<studentId>:<newRank>"} — a participant is
     * notified once per rank they land on; landing on the same rank again (e.g. after a
     * later recompute reverts the movement) is a no-op, never a fresh row.
     */
    static NotificationDispatch leaderboardMoved(
            Long userId, Long contestId, String contestTitle, Long studentId, int newRank) {
        String title = "Leaderboard update";
        String message = "Your rank in \"" + contestTitle + "\" is now #" + newRank + ".";
        String dedupeKey = "leaderboard:" + contestId + ":" + studentId + ":" + newRank;
        return new NotificationDispatch(
                userId,
                NotificationType.LEADERBOARD_UPDATE,
                title,
                message,
                NotificationPriority.LOW,
                "/coding/contests/" + contestId,
                NotificationReferenceType.CONTEST,
                contestId,
                null,
                dedupeKey);
    }
}
