package smartcampus.repository.projection;

/**
 * Per-announcement recipient count, produced by
 * {@code AnnouncementRepository.countRecipientsByAnnouncementIds}. Getter names
 * must match that query's JPQL {@code as} aliases exactly.
 */
public interface AnnouncementRecipientCount {

    Long getAnnouncementId();

    Long getRecipientCount();
}
