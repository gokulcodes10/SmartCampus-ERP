package smartcampus.entity;

/**
 * The four fixed performance categories a student's aggregates can classify into (§22-§24,
 * §60). The set is closed — it is fixed by the {@code chk_performance_bands_category} CHECK
 * constraint in {@code V5__analytics.sql} and by this enum together; there is no create/delete
 * endpoint for categories, only {@code PUT} to reconfigure a category's thresholds.
 *
 * <p><b>Java declaration order below is NOT the evaluation priority.</b> The order in which
 * bands are tested against a student's figures is {@code performance_bands.display_order},
 * read from the database via
 * {@code PerformanceBandRepository#findAllByOrderByDisplayOrderAsc()} — never this enum's
 * ordinal. EXCELLENT happens to be both declared first and seeded at {@code display_order = 1},
 * but an admin could in principle repoint the ordering through the thresholds without touching
 * this file; nothing in the classifier may assume otherwise.
 */
public enum PerformanceCategory {
    EXCELLENT,
    GOOD,
    AVERAGE,
    AT_RISK
}
