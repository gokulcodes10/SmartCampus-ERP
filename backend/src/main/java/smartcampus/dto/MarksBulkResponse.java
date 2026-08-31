package smartcampus.dto;

import java.math.BigDecimal;
import java.util.List;

/** Response for {@code POST /api/marks/bulk} — honest created/updated counts (UPSERT). */
public record MarksBulkResponse(
        Long examId,
        String examTitle,
        BigDecimal maximumMarks,
        int createdCount,
        int updatedCount,
        List<MarksResponse> records) {}
