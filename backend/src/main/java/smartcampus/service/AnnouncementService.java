package smartcampus.service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.AnnouncementCreateRequest;
import smartcampus.dto.AnnouncementResponse;
import smartcampus.dto.AnnouncementUpdateRequest;
import smartcampus.dto.NotificationDispatch;
import smartcampus.dto.PageResponse;
import smartcampus.entity.Announcement;
import smartcampus.entity.AnnouncementAudience;
import smartcampus.entity.Department;
import smartcampus.entity.NotificationReferenceType;
import smartcampus.entity.NotificationType;
import smartcampus.entity.Role;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.exception.ResourceNotFoundException;
import smartcampus.repository.AnnouncementRepository;
import smartcampus.repository.DepartmentRepository;
import smartcampus.repository.FacultyRepository;
import smartcampus.repository.NotificationRecipientRepository;
import smartcampus.repository.NotificationRepository;
import smartcampus.repository.StudentRepository;
import smartcampus.repository.projection.AnnouncementRecipientCount;

/**
 * §42 admin announcements: create/update/delete, the ACTIVE board (role/department
 * scoped) and the ADMIN management list, plus the audience -&gt; recipient fan-out that
 * turns one announcement into one owned {@code notifications} row per eligible user.
 *
 * <p>All the invariants the {@code announcements} CHECK constraints enforce are
 * validated in Java <b>before</b> the row is written — a CHECK violation surfaces as a
 * {@link org.springframework.dao.DataIntegrityViolationException}, i.e. an HTTP 500,
 * not the §47 400 the caller needs.
 */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final NotificationService notificationService;
    private final ScopedWriteAuthorizer scopedWriteAuthorizer;
    private final StudentRepository studentRepository;
    private final FacultyRepository facultyRepository;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            NotificationRepository notificationRepository,
            DepartmentRepository departmentRepository,
            NotificationRecipientRepository notificationRecipientRepository,
            NotificationService notificationService,
            ScopedWriteAuthorizer scopedWriteAuthorizer,
            StudentRepository studentRepository,
            FacultyRepository facultyRepository) {
        this.announcementRepository = announcementRepository;
        this.notificationRepository = notificationRepository;
        this.departmentRepository = departmentRepository;
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.notificationService = notificationService;
        this.scopedWriteAuthorizer = scopedWriteAuthorizer;
        this.studentRepository = studentRepository;
        this.facultyRepository = facultyRepository;
    }

    // ------------------------------------------------------------------
    // Write
    // ------------------------------------------------------------------

    @Transactional
    public AnnouncementResponse create(AnnouncementCreateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);

        Department department = validateAudienceAndDepartment(request.audience(), request.departmentId());
        LocalDateTime publishedAt = LocalDateTime.now();
        validateExpiry(request.expiresAt(), publishedAt);
        validateNotBlank(request.title(), request.body());

        Announcement announcement =
                Announcement.builder()
                        .title(request.title())
                        .body(request.body())
                        .audience(request.audience())
                        .department(department)
                        .priority(request.priority() == null ? smartcampus.entity.NotificationPriority.NORMAL : request.priority())
                        .publishedAt(publishedAt)
                        .expiresAt(request.expiresAt())
                        .createdBy(caller)
                        .build();

        announcement = announcementRepository.save(announcement);

        int recipientCount = fanOut(announcement);

        return toResponse(announcement, (long) recipientCount);
    }

    @Transactional
    public AnnouncementResponse update(Long id, AnnouncementUpdateRequest request, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Announcement announcement =
                announcementRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));

        // Re-validate the expiry rule against the STORED publishedAt — this endpoint
        // deliberately carries no audience/departmentId, so re-targeting is out of scope.
        validateExpiry(request.expiresAt(), announcement.getPublishedAt());
        validateNotBlank(request.title(), request.body());

        announcement.setTitle(request.title());
        announcement.setBody(request.body());
        announcement.setPriority(
                request.priority() == null ? smartcampus.entity.NotificationPriority.NORMAL : request.priority());
        announcement.setExpiresAt(request.expiresAt());

        return toResponse(announcement, null);
    }

    @Transactional
    public void delete(Long id, User caller) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Announcement announcement =
                announcementRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        // fk_notifications_announcement is ON DELETE CASCADE — this withdraws the
        // announcement from every recipient's notification centre.
        announcementRepository.delete(announcement);
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> board(User caller, Pageable pageable) {
        Set<AnnouncementAudience> audiences = boardAudiences(caller);
        Long departmentId = callerDepartmentId(caller);
        boolean admin = scopedWriteAuthorizer.isAdmin(caller);

        Page<Announcement> page =
                announcementRepository.findVisible(audiences, departmentId, LocalDateTime.now(), pageable);

        return buildResponsePage(page, admin);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> manage(
            AnnouncementAudience audience, Boolean includeExpired, String q, User caller, Pageable pageable) {
        scopedWriteAuthorizer.requireAdmin(caller);
        Specification<Announcement> spec = buildManageFilter(audience, includeExpired, q);
        Page<Announcement> page = announcementRepository.findAll(spec, pageable);
        return buildResponsePage(page, true);
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getById(Long id, User caller) {
        boolean admin = scopedWriteAuthorizer.isAdmin(caller);
        Announcement announcement;
        if (admin) {
            announcement =
                    announcementRepository
                            .findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        } else {
            Set<AnnouncementAudience> audiences = boardAudiences(caller);
            Long departmentId = callerDepartmentId(caller);
            announcement =
                    announcementRepository
                            .findVisibleById(id, audiences, departmentId, LocalDateTime.now())
                            .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
        }
        Long recipientCount = admin ? recipientCountFor(announcement.getId()) : null;
        return toResponse(announcement, recipientCount);
    }

    // ------------------------------------------------------------------
    // Fan-out (audience -> recipient userIds -> NotificationDispatch)
    // ------------------------------------------------------------------

    private int fanOut(Announcement announcement) {
        Set<Long> recipientUserIds = resolveRecipients(announcement);

        List<NotificationDispatch> dispatches = new ArrayList<>(recipientUserIds.size());
        String link = "/notifications";
        for (Long userId : recipientUserIds) {
            dispatches.add(
                    new NotificationDispatch(
                            userId,
                            NotificationType.ANNOUNCEMENT,
                            announcement.getTitle(),
                            announcement.getBody(),
                            announcement.getPriority(),
                            link,
                            NotificationReferenceType.ANNOUNCEMENT,
                            announcement.getId(),
                            announcement.getId(),
                            "announcement:" + announcement.getId()));
        }

        return notificationService.dispatchAll(dispatches);
    }

    private Set<Long> resolveRecipients(Announcement announcement) {
        Set<Long> ids = new LinkedHashSet<>();
        switch (announcement.getAudience()) {
            case ALL -> ids.addAll(notificationRecipientRepository.findAllEnabledUserIds());
            case STUDENTS -> ids.addAll(notificationRecipientRepository.findEnabledUserIdsByRole(Role.STUDENT));
            case FACULTY -> ids.addAll(notificationRecipientRepository.findEnabledUserIdsByRole(Role.FACULTY));
            case DEPARTMENT -> {
                Long departmentId = announcement.getDepartment().getId();
                ids.addAll(notificationRecipientRepository.findEnabledStudentUserIdsByDepartment(departmentId));
                ids.addAll(notificationRecipientRepository.findEnabledFacultyUserIdsByDepartment(departmentId));
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private Department validateAudienceAndDepartment(AnnouncementAudience audience, Long departmentId) {
        if (audience == AnnouncementAudience.DEPARTMENT) {
            if (departmentId == null) {
                throw new BadRequestException("A DEPARTMENT announcement requires a departmentId.");
            }
            return departmentRepository
                    .findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));
        }
        if (departmentId != null) {
            throw new BadRequestException("departmentId is only valid for a DEPARTMENT announcement.");
        }
        return null;
    }

    private static void validateExpiry(LocalDateTime expiresAt, LocalDateTime publishedAt) {
        if (expiresAt != null && !expiresAt.isAfter(publishedAt)) {
            throw new BadRequestException("expiresAt must be after the publication time.");
        }
    }

    private static void validateNotBlank(String title, String body) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("title must not be blank.");
        }
        if (body == null || body.isBlank()) {
            throw new BadRequestException("body must not be blank.");
        }
    }

    // ------------------------------------------------------------------
    // Visibility helpers
    // ------------------------------------------------------------------

    private Set<AnnouncementAudience> boardAudiences(User caller) {
        if (caller == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authentication is required for this operation.");
        }
        return switch (caller.getRole()) {
            case ADMIN -> java.util.EnumSet.of(
                    AnnouncementAudience.ALL,
                    AnnouncementAudience.STUDENTS,
                    AnnouncementAudience.FACULTY,
                    AnnouncementAudience.DEPARTMENT);
            case STUDENT -> java.util.EnumSet.of(AnnouncementAudience.ALL, AnnouncementAudience.STUDENTS);
            case FACULTY -> java.util.EnumSet.of(AnnouncementAudience.ALL, AnnouncementAudience.FACULTY);
        };
    }

    private Long callerDepartmentId(User caller) {
        if (caller.getRole() == Role.STUDENT) {
            return studentRepository
                    .findByUserId(caller.getId())
                    .map(s -> s.getDepartment() == null ? null : s.getDepartment().getId())
                    .orElse(null);
        }
        if (caller.getRole() == Role.FACULTY) {
            return facultyRepository
                    .findByUserId(caller.getId())
                    .map(f -> f.getDepartment() == null ? null : f.getDepartment().getId())
                    .orElse(null);
        }
        return null; // ADMIN passes all four audiences and sees everything active.
    }

    // ------------------------------------------------------------------
    // Response assembly — recipientCount populated with ONE batched query per page.
    // ------------------------------------------------------------------

    private PageResponse<AnnouncementResponse> buildResponsePage(Page<Announcement> page, boolean admin) {
        Map<Long, Long> recipientCounts = Map.of();
        if (admin) {
            List<Long> ids = page.getContent().stream().map(Announcement::getId).toList();
            if (!ids.isEmpty()) {
                recipientCounts =
                        notificationRepository.countRecipientsByAnnouncementIds(ids).stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                AnnouncementRecipientCount::getAnnouncementId,
                                                AnnouncementRecipientCount::getRecipientCount));
            }
        }
        Map<Long, Long> counts = recipientCounts;
        return PageResponse.of(
                page, a -> toResponse(a, admin ? counts.getOrDefault(a.getId(), 0L) : null));
    }

    private Long recipientCountFor(Long announcementId) {
        return notificationRepository.countRecipientsByAnnouncementIds(List.of(announcementId)).stream()
                .map(AnnouncementRecipientCount::getRecipientCount)
                .findFirst()
                .orElse(0L);
    }

    private static AnnouncementResponse toResponse(Announcement a, Long recipientCount) {
        LocalDateTime now = LocalDateTime.now();
        boolean active =
                !a.getPublishedAt().isAfter(now) && (a.getExpiresAt() == null || a.getExpiresAt().isAfter(now));
        return new AnnouncementResponse(
                a.getId(),
                a.getTitle(),
                a.getBody(),
                a.getAudience(),
                a.getDepartment() == null ? null : a.getDepartment().getId(),
                a.getDepartment() == null ? null : a.getDepartment().getName(),
                a.getPriority(),
                a.getPublishedAt(),
                a.getExpiresAt(),
                active,
                a.getCreatedBy() == null ? null : a.getCreatedBy().getId(),
                a.getCreatedBy() == null ? null : a.getCreatedBy().getFullName(),
                recipientCount,
                a.getCreatedAt(),
                a.getUpdatedAt());
    }

    // ------------------------------------------------------------------
    // Manage filter
    // ------------------------------------------------------------------

    private Specification<Announcement> buildManageFilter(
            AnnouncementAudience audience, Boolean includeExpired, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (audience != null) {
                predicates.add(cb.equal(root.get("audience"), audience));
            }
            if (!Boolean.TRUE.equals(includeExpired)) {
                LocalDateTime now = LocalDateTime.now();
                predicates.add(
                        cb.or(
                                cb.isNull(root.get("expiresAt")),
                                cb.greaterThan(root.get("expiresAt"), now)));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                predicates.add(
                        cb.or(
                                cb.like(cb.lower(root.get("title")), like),
                                cb.like(cb.lower(root.get("body")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
