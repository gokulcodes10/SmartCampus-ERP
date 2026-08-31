package smartcampus.dto;

import java.util.List;

/**
 * The dropdown options for the analytics screens' filter bar. For a FACULTY caller
 * every list is derived ONLY from {@code TeachingService#myClasses} — a faculty must
 * never be offered a filter value covering a class they do not teach. For an ADMIN
 * caller every list is drawn from the full reference data.
 */
public record AnalyticsFilterOptionsResponse(
        List<FilterCourseOption> courses,
        List<FilterSubjectOption> subjects,
        List<String> academicYears,
        List<Integer> semesters,
        List<String> sections) {}
