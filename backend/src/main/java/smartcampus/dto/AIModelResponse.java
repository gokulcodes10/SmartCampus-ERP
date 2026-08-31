package smartcampus.dto;

/** One model reported live by the provider's {@code GET /models} endpoint. Assembled by the service. */
public record AIModelResponse(String id, String ownedBy, Integer contextWindow) {}
