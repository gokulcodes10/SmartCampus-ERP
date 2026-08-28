package smartcampus.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * The §44 server-side pagination envelope. Every list endpoint in the application
 * returns this exact shape — {@code content}, {@code page}, {@code size},
 * {@code totalElements}, {@code totalPages} — so the frontend can render a consistent
 * pager regardless of which resource it is paging through.
 *
 * <p>Filtering, sorting and paging all happen in the database query (Spring Data
 * {@code Specification} + {@code Pageable}), never by fetching everything and slicing
 * it in memory — that would defeat the point of a page size and does not scale.
 *
 * <p>Three factories cover every call shape used across modules:
 *
 * <ul>
 *   <li>{@link #of(Page)} / {@link #from(Page)} — the {@link Page} already holds the
 *       response DTO (the two names are aliases of each other; both are kept because
 *       different modules were written against each spelling).
 *   <li>{@link #of(Page, Function)} — the {@link Page} holds entities and {@code mapper}
 *       converts each one to its response DTO, so the caller does not need a separate
 *       {@code page.map(...)} step.
 * </ul>
 */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Wraps a {@link Page} whose content is already the desired response type. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** Alias of {@link #of(Page)}. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return of(page);
    }

    /** Wraps a {@link Page} of entities, mapping each one to its response DTO. */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
