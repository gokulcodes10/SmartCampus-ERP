import type { RealtimeEnvelope } from "@/types/realtime";

/**
 * Raw-WebSocket client for `/ws/notifications` (Phase 11 contract §6/§10). NOT
 * STOMP, NOT SockJS — there is no message broker and no client-supplied destination;
 * the server binds the caller's identity from the JWT at handshake and pushes only
 * their own rows, so "subscribe to another user's stream" is impossible by
 * construction. The only client -> server frame is the literal string `"ping"`.
 *
 * `NotificationContext` owns the lifecycle (one socket per authenticated user; closed
 * on logout, on unmount, and replaced whenever the user changes) — this module only
 * knows how to open one connection and keep it alive.
 */

const PING_INTERVAL_MS = 30_000;
const BASE_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;
const AUTH_DEAD_FAILURE_COUNT = 3;
const AUTH_DEAD_WINDOW_MS = 30_000;

export type SocketConnectionState = "connecting" | "open" | "closed";

export interface NotificationSocketHandlers {
  onEnvelope: (envelope: RealtimeEnvelope) => void;
  onStateChange: (state: SocketConnectionState) => void;
}

/**
 * `wsBase` = `VITE_API_BASE_URL` (defaulting to the local backend), scheme rewritten
 * http -> ws, https -> wss. Mirrors how `services/api.ts` resolves its base URL —
 * duplicated rather than imported because `api.ts` is integrator-owned and exports an
 * axios instance, not a base URL string.
 */
function resolveSocketUrl(token: string): string {
  const httpBase = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8080";
  const wsBase = httpBase.replace(/^http/, "ws");
  return `${wsBase}/ws/notifications?token=${encodeURIComponent(token)}`;
}

/**
 * One connection, with reconnect-with-backoff and a "stop hammering a dead
 * credential" cutoff. A handshake rejected for a bad/expired token fails instantly
 * (error, then close, before `onopen` ever fires, with no readable status) — that
 * pattern repeating `AUTH_DEAD_FAILURE_COUNT` times inside `AUTH_DEAD_WINDOW_MS` is
 * treated as "auth is dead"; retrying stops and AuthContext's existing 401 handling
 * (triggered by the next authenticated REST call) takes over instead.
 */
export class NotificationSocket {
  private readonly token: string;
  private readonly handlers: NotificationSocketHandlers;
  private ws: WebSocket | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private attempt = 0;
  /** Timestamps of consecutive closes that happened before the socket ever opened. */
  private preOpenFailures: number[] = [];
  private everOpenedThisAttempt = false;
  private stoppedForDeadAuth = false;
  private manuallyClosed = false;

  constructor(token: string, handlers: NotificationSocketHandlers) {
    this.token = token;
    this.handlers = handlers;
  }

  connect(): void {
    if (this.manuallyClosed || this.stoppedForDeadAuth) return;

    this.handlers.onStateChange("connecting");
    this.everOpenedThisAttempt = false;

    let socket: WebSocket;
    try {
      socket = new WebSocket(resolveSocketUrl(this.token));
    } catch {
      this.handleFailedAttempt();
      return;
    }
    this.ws = socket;

    socket.onopen = () => {
      this.everOpenedThisAttempt = true;
      this.attempt = 0;
      this.preOpenFailures = [];
      this.handlers.onStateChange("open");
      this.startPing();
    };

    socket.onmessage = (event) => {
      if (typeof event.data !== "string") return;
      try {
        const envelope = JSON.parse(event.data) as RealtimeEnvelope;
        this.handlers.onEnvelope(envelope);
      } catch {
        // Not a frame we understand — ignore rather than crash the socket.
      }
    };

    socket.onclose = () => {
      this.stopPing();
      this.ws = null;
      this.handlers.onStateChange("closed");
      if (!this.manuallyClosed) {
        this.handleFailedAttempt();
      }
    };

    // onclose always follows onerror for a browser WebSocket; nothing extra to do here.
    socket.onerror = () => {};
  }

  /** Client -> server: the literal string "ping" and nothing else. */
  private sendPing(): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send("ping");
    }
  }

  private startPing(): void {
    this.stopPing();
    this.pingTimer = setInterval(() => this.sendPing(), PING_INTERVAL_MS);
  }

  private stopPing(): void {
    if (this.pingTimer !== null) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }

  private handleFailedAttempt(): void {
    if (!this.everOpenedThisAttempt) {
      const now = Date.now();
      this.preOpenFailures = this.preOpenFailures.filter((t) => now - t < AUTH_DEAD_WINDOW_MS);
      this.preOpenFailures.push(now);
      if (this.preOpenFailures.length >= AUTH_DEAD_FAILURE_COUNT) {
        this.stoppedForDeadAuth = true;
        return;
      }
    }
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    if (this.manuallyClosed || this.stoppedForDeadAuth) return;
    const backoff = Math.min(BASE_BACKOFF_MS * 2 ** this.attempt, MAX_BACKOFF_MS);
    const jitter = backoff * 0.2 * Math.random();
    this.attempt += 1;
    this.reconnectTimer = setTimeout(() => this.connect(), backoff + jitter);
  }

  /** Close on logout, on unmount, or when replacing this socket for a different user. */
  close(): void {
    this.manuallyClosed = true;
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stopPing();
    const socket = this.ws;
    this.ws = null;
    if (socket) {
      socket.onopen = null;
      socket.onmessage = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.close();
    }
  }
}
