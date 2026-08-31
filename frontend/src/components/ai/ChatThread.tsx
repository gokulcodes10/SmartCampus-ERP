import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { AIMessageResponse } from "@/types/ai";

interface ChatThreadProps {
  messages: AIMessageResponse[];
  /** True while a turn is in flight — shows a lightweight "thinking" indicator. */
  pending?: boolean;
}

/**
 * Renders one conversation's turns. SYSTEM messages carry the grounding context sent to
 * the model — they are never shown inline, only inside the "Context sent to the model"
 * disclosure, so the thread itself reads as a normal chat between the student and the
 * assistant. An assistant message always shows the model that produced it (and, when
 * present, the token counts) so the answer is visibly real, not fabricated.
 */
export function ChatThread({ messages, pending = false }: ChatThreadProps) {
  const systemMessages = messages.filter((m) => m.role === "SYSTEM");
  const latestSystem = systemMessages.length > 0 ? systemMessages[systemMessages.length - 1] : null;
  const visible = messages.filter((m) => m.role !== "SYSTEM");

  return (
    <div className="flex flex-col gap-3">
      {latestSystem && (
        <details className="rounded-lg border border-border bg-muted/40 p-2.5 text-sm">
          <summary className="cursor-pointer select-none text-xs font-medium text-muted-foreground">
            Context sent to the model
          </summary>
          <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs text-foreground">
            {latestSystem.content}
          </pre>
        </details>
      )}

      {visible.length === 0 && !pending && (
        <p className="py-8 text-center text-sm text-muted-foreground">
          No messages yet. Ask the assistant something below.
        </p>
      )}

      {visible.map((message) => (
        <div
          key={message.id}
          className={cn("flex flex-col gap-1", message.role === "USER" ? "items-end" : "items-start")}
        >
          <div
            className={cn(
              "max-w-[85%] rounded-xl px-3 py-2 text-sm whitespace-pre-wrap break-words",
              message.role === "USER"
                ? "bg-primary text-primary-foreground"
                : "bg-muted text-foreground ring-1 ring-foreground/10",
            )}
          >
            {message.content}
          </div>
          {message.role === "ASSISTANT" && (
            <div className="flex flex-wrap items-center gap-1.5 px-1 text-xs text-muted-foreground">
              <Badge variant="outline">{message.model ?? "unknown model"}</Badge>
              {message.totalTokens !== null && (
                <span>
                  {message.promptTokens ?? "—"} prompt + {message.completionTokens ?? "—"} completion ={" "}
                  {message.totalTokens} tokens
                </span>
              )}
              {message.latencyMs !== null && <span>· {message.latencyMs} ms</span>}
            </div>
          )}
        </div>
      ))}

      {pending && (
        <div className="flex items-start">
          <div className="max-w-[85%] rounded-xl bg-muted px-3 py-2 text-sm text-muted-foreground ring-1 ring-foreground/10">
            Thinking…
          </div>
        </div>
      )}
    </div>
  );
}
