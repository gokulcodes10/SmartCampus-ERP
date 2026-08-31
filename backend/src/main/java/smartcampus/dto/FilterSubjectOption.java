package smartcampus.dto;

/** One subject option for an analytics filter dropdown. */
public record FilterSubjectOption(Long id, String code, String name, Long courseId, Integer semester) {}
