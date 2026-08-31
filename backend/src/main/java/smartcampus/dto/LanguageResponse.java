package smartcampus.dto;

import smartcampus.entity.ProgrammingLanguage;

/** One entry of {@code GET /api/coding/languages} — what the Monaco editor needs to boot. */
public record LanguageResponse(
        ProgrammingLanguage language,
        String label,
        Integer judge0LanguageId,
        String monacoLanguageId,
        String defaultTemplate) {}
