/**
 * Raw Spring WebSocket transport for the notification centre (Phase 11, Real-Time).
 *
 * <p>Deliberately NOT STOMP: there is no client-supplied destination anywhere in this
 * protocol, so "a user cannot subscribe to another user's notification stream" is true
 * by construction rather than by a check that could later be forgotten. A caller
 * authenticates once at handshake time via a JWT carried in the {@code token} query
 * parameter ({@link smartcampus.realtime.JwtHandshakeInterceptor}); every subsequent
 * push for that connection is addressed by the userId resolved at handshake time and
 * held only in server-side session attributes and {@link
 * smartcampus.realtime.NotificationSocketRegistry} - never by anything the client
 * sends afterwards.
 *
 * <p>Delivery is best-effort on top of a durable guarantee: the notification row
 * written by {@code smartcampus.service.NotificationService} is the source of truth,
 * and a live push through {@link smartcampus.realtime.NotificationPushService} is
 * purely a low-latency convenience for a client that happens to be connected. A dead
 * or absent socket never affects whether the notification was recorded.
 */
package smartcampus.realtime;
