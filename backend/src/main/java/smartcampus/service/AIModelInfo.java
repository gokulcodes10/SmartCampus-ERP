package smartcampus.service;

/**
 * One entry from the provider's {@code GET /models} listing. {@code contextWindow} is
 * {@code null} when the provider does not report it for that model.
 */
public record AIModelInfo(String id, String ownedBy, Integer contextWindow) {}
