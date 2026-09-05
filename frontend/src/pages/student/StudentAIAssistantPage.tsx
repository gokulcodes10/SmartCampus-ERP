import { useEffect, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import { AxiosError } from "axios";
import { PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { ConfirmDialog } from "@/components/admin/ConfirmDialog";
import { PaginationBar } from "@/components/admin/PaginationBar";
import { AcademicContextPanel } from "@/components/ai/AcademicContextPanel";
import { ChatThread } from "@/components/ai/ChatThread";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useServerTable } from "@/hooks/useServerTable";
import * as aiService from "@/services/aiService";
import type {
  AIConversationDetailResponse,
  AIConversationResponse,
  AIDifficulty,
  AIFeature,
  AIStatusResponse,
} from "@/types/ai";
import { extractErrorMessage } from "@/utils/apiError";

const ALL = "__ALL__";

const FEATURE_LABELS: Record<AIFeature, string> = {
  CHAT: "Chat",
  STUDY_PLAN: "Study plan",
  TOPIC_EXPLANATION: "Explain",
  PRACTICE_QUESTIONS: "Practice questions",
  MCQ: "MCQs",
  REVISION_SCHEDULE: "Revision schedule",
};

const DIFFICULTIES: AIDifficulty[] = ["EASY", "MEDIUM", "HARD"];

const TEXTAREA_CLASS =
  "w-full resize-none rounded-lg border border-input bg-transparent px-2.5 py-1.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30";

type QuickActionKind = "explain" | "practice" | "mcq" | null;

interface QuickActionForm {
  topic: string;
  focus: string;
  subjectId: string;
  count: string;
  difficulty: AIDifficulty | "";
}

function emptyQuickActionForm(): QuickActionForm {
  return { topic: "", focus: "", subjectId: "", count: "", difficulty: "" };
}

function isAxiosStatus(err: unknown, status: number): boolean {
  return err instanceof AxiosError && err.response?.status === status;
}

/**
 * The student AI assistant: a conversation sidebar, the open thread, a composer, and
 * quick actions (Explain / Practice questions / MCQs) that each open their own
 * conversation. Every answer is grounded in the student's real academic record —
 * `AcademicContextPanel` shows that record, and each conversation's "Context sent to
 * the model" disclosure (inside `ChatThread`) shows the exact SYSTEM prompt used.
 */
export default function StudentAIAssistantPage() {
  const [status, setStatus] = useState<AIStatusResponse | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);

  function loadStatus() {
    aiService
      .getStatus()
      .then(setStatus)
      .catch((err) => setStatusError(extractErrorMessage(err, "Failed to load the assistant's status.")));
  }

  useEffect(() => {
    loadStatus();
  }, []);

  const [featureFilter, setFeatureFilter] = useState<string>(ALL);
  const {
    data: conversations,
    isLoading: conversationsLoading,
    error: conversationsError,
    setPage,
    search,
    setSearch,
    refresh: refreshConversations,
  } = useServerTable<AIConversationResponse, { feature?: AIFeature }>(
    (params) => aiService.listConversations({ page: params.page, size: params.size, feature: params.feature, q: params.search }),
    { feature: featureFilter === ALL ? undefined : (featureFilter as AIFeature) },
    { pageSize: 15 },
  );

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AIConversationDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // Reset detail state during render whenever `selectedId` changes, rather than as
  // the first statements inside the effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes.
  const [fetchedForSelectedId, setFetchedForSelectedId] = useState<number | null | "unset">("unset");
  if (selectedId !== fetchedForSelectedId) {
    setFetchedForSelectedId(selectedId);
    if (selectedId === null) {
      setDetail(null);
    } else {
      setDetailLoading(true);
      setDetailError(null);
    }
  }

  useEffect(() => {
    if (selectedId === null) return;
    let cancelled = false;
    aiService
      .getConversation(selectedId)
      .then((data) => {
        if (!cancelled) setDetail(data);
      })
      .catch((err) => {
        if (!cancelled) setDetailError(extractErrorMessage(err, "Failed to load this conversation."));
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  const [composerText, setComposerText] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  function handleAiError(err: unknown, fallback: string) {
    if (isAxiosStatus(err, 429)) {
      setSendError(extractErrorMessage(err, "Rate limit exceeded."));
      loadStatus();
    } else if (isAxiosStatus(err, 503)) {
      setSendError(extractErrorMessage(err, "The assistant is not configured or the provider could not be reached."));
    } else {
      setSendError(extractErrorMessage(err, fallback));
    }
  }

  async function handleSend() {
    const message = composerText.trim();
    if (!message || sending) return;
    setSending(true);
    setSendError(null);
    try {
      if (selectedId === null) {
        const created = await aiService.createConversation({ message });
        setComposerText("");
        setSelectedId(created.conversation.id);
        setDetail(created);
        refreshConversations();
      } else {
        await aiService.sendMessage(selectedId, { message });
        setComposerText("");
        const refreshed = await aiService.getConversation(selectedId);
        setDetail(refreshed);
        refreshConversations();
      }
    } catch (err) {
      handleAiError(err, "Failed to send your message.");
    } finally {
      setSending(false);
    }
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void handleSend();
    }
  }

  // Rename (inline) ---------------------------------------------------------------
  const [renamingId, setRenamingId] = useState<number | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renameSaving, setRenameSaving] = useState(false);
  const [renameError, setRenameError] = useState<string | null>(null);

  function startRename(conversation: AIConversationResponse) {
    setRenamingId(conversation.id);
    setRenameValue(conversation.title);
    setRenameError(null);
  }

  async function saveRename() {
    if (renamingId === null) return;
    const title = renameValue.trim();
    if (!title) {
      setRenameError("Title is required.");
      return;
    }
    setRenameSaving(true);
    setRenameError(null);
    try {
      const updated = await aiService.renameConversation(renamingId, { title });
      if (detail && detail.conversation.id === renamingId) {
        setDetail({ ...detail, conversation: updated });
      }
      setRenamingId(null);
      refreshConversations();
    } catch (err) {
      setRenameError(extractErrorMessage(err, "Failed to rename this conversation."));
    } finally {
      setRenameSaving(false);
    }
  }

  // Delete --------------------------------------------------------------------------
  const [deleteTarget, setDeleteTarget] = useState<AIConversationResponse | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await aiService.deleteConversation(deleteTarget.id);
      if (selectedId === deleteTarget.id) {
        setSelectedId(null);
      }
      setDeleteTarget(null);
      refreshConversations();
    } catch (err) {
      setDeleteError(extractErrorMessage(err, "Failed to delete this conversation."));
    } finally {
      setDeleting(false);
    }
  }

  // Quick actions ---------------------------------------------------------------
  const [quickAction, setQuickAction] = useState<QuickActionKind>(null);
  const [quickForm, setQuickForm] = useState<QuickActionForm>(emptyQuickActionForm());
  const [quickError, setQuickError] = useState<string | null>(null);
  const [quickSubmitting, setQuickSubmitting] = useState(false);

  function openQuickAction(kind: Exclude<QuickActionKind, null>) {
    setQuickAction(kind);
    setQuickForm(emptyQuickActionForm());
    setQuickError(null);
  }

  async function handleQuickActionSubmit(event: FormEvent) {
    event.preventDefault();
    if (!quickAction) return;
    const topic = quickForm.topic.trim();
    if (!topic) {
      setQuickError("Topic is required.");
      return;
    }
    const subjectId = quickForm.subjectId.trim() ? Number(quickForm.subjectId.trim()) : undefined;
    setQuickSubmitting(true);
    setQuickError(null);
    try {
      let turn;
      if (quickAction === "explain") {
        turn = await aiService.explain({
          topic,
          focus: quickForm.focus.trim() ? quickForm.focus.trim() : undefined,
          subjectId,
        });
      } else {
        const count = quickForm.count.trim() ? Number(quickForm.count.trim()) : undefined;
        const difficulty = quickForm.difficulty || undefined;
        turn =
          quickAction === "practice"
            ? await aiService.practiceQuestions({ topic, subjectId, count, difficulty })
            : await aiService.mcqs({ topic, subjectId, count, difficulty });
      }
      const refreshed = await aiService.getConversation(turn.conversationId);
      setDetail(refreshed);
      setSelectedId(turn.conversationId);
      refreshConversations();
      setQuickAction(null);
    } catch (err) {
      if (isAxiosStatus(err, 429)) {
        setQuickError(extractErrorMessage(err, "Rate limit exceeded."));
        loadStatus();
      } else if (isAxiosStatus(err, 503)) {
        setQuickError(extractErrorMessage(err, "The assistant is not configured or the provider could not be reached."));
      } else {
        setQuickError(extractErrorMessage(err, "Failed to generate a response."));
      }
    } finally {
      setQuickSubmitting(false);
    }
  }

  const composerDisabled = status !== null && !status.configured;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">AI assistant</h1>
        <p className="text-muted-foreground">
          Chat, get explanations, practice questions and MCQs — grounded in your real marks, attendance
          and upcoming exams.
        </p>
      </div>

      {statusError && (
        <Alert variant="destructive">
          <AlertDescription>{statusError}</AlertDescription>
        </Alert>
      )}
      {status && !status.configured && (
        <Alert variant="destructive">
          <AlertDescription>
            The AI assistant is not configured on this server, so the composer and quick actions below are
            disabled until it is.
          </AlertDescription>
        </Alert>
      )}
      {status && status.configured && (
        <p className="text-xs text-muted-foreground">
          Model: {status.model ?? "resolved on first use"} · {status.remainingMinute}/{status.rateLimitPerMinute}{" "}
          requests left this minute · {status.remainingDay}/{status.rateLimitPerDay} left today
        </p>
      )}

      <AcademicContextPanel />

      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" disabled={composerDisabled} onClick={() => openQuickAction("explain")}>
          Explain a topic
        </Button>
        <Button type="button" variant="outline" disabled={composerDisabled} onClick={() => openQuickAction("practice")}>
          Practice questions
        </Button>
        <Button type="button" variant="outline" disabled={composerDisabled} onClick={() => openQuickAction("mcq")}>
          MCQs
        </Button>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[320px_1fr]">
        <Card className="flex flex-col">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Conversations</CardTitle>
              <Button type="button" variant="ghost" size="icon" onClick={() => setSelectedId(null)}>
                <PlusIcon />
                <span className="sr-only">New conversation</span>
              </Button>
            </div>
            <div className="space-y-2 pt-2">
              <Input placeholder="Search titles…" value={search} onChange={(e) => setSearch(e.target.value)} />
              <Select value={featureFilter} onValueChange={(value) => value && setFeatureFilter(value)}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="All features" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>All features</SelectItem>
                  {(Object.keys(FEATURE_LABELS) as AIFeature[]).map((feature) => (
                    <SelectItem key={feature} value={feature}>
                      {FEATURE_LABELS[feature]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </CardHeader>
          <CardContent className="space-y-1 px-2">
            {conversationsError && (
              <Alert variant="destructive">
                <AlertDescription>{conversationsError}</AlertDescription>
              </Alert>
            )}
            {conversationsLoading && <p className="py-6 text-center text-sm text-muted-foreground">Loading…</p>}
            {!conversationsLoading && conversations?.content.length === 0 && (
              <p className="py-6 text-center text-sm text-muted-foreground">
                No conversations yet — start one below.
              </p>
            )}
            {!conversationsLoading &&
              conversations?.content.map((conversation) => (
                <div
                  key={conversation.id}
                  className={`rounded-lg border p-2 text-sm ${
                    selectedId === conversation.id ? "border-primary bg-muted" : "border-transparent hover:bg-muted"
                  }`}
                >
                  {renamingId === conversation.id ? (
                    <div className="space-y-1">
                      {renameError && <p className="text-xs text-destructive">{renameError}</p>}
                      <Input
                        autoFocus
                        value={renameValue}
                        maxLength={150}
                        onChange={(e) => setRenameValue(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && void saveRename()}
                      />
                      <div className="flex gap-1">
                        <Button type="button" size="xs" disabled={renameSaving} onClick={() => void saveRename()}>
                          {renameSaving ? "Saving…" : "Save"}
                        </Button>
                        <Button type="button" size="xs" variant="outline" onClick={() => setRenamingId(null)}>
                          Cancel
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <button
                        type="button"
                        className="block w-full text-left"
                        onClick={() => setSelectedId(conversation.id)}
                      >
                        <span className="font-medium">{conversation.title}</span>
                        <span className="mt-0.5 flex flex-wrap items-center gap-1 text-xs text-muted-foreground">
                          <Badge variant="outline">{FEATURE_LABELS[conversation.feature]}</Badge>
                          {conversation.messageCount} msg{conversation.messageCount === 1 ? "" : "s"}
                        </span>
                      </button>
                      <div className="mt-1 flex justify-end gap-1">
                        <Button type="button" variant="ghost" size="icon-xs" onClick={() => startRename(conversation)}>
                          <PencilIcon />
                          <span className="sr-only">Rename</span>
                        </Button>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-xs"
                          onClick={() => {
                            setDeleteError(null);
                            setDeleteTarget(conversation);
                          }}
                        >
                          <Trash2Icon />
                          <span className="sr-only">Delete</span>
                        </Button>
                      </div>
                    </>
                  )}
                </div>
              ))}
            {conversations && (
              <PaginationBar
                page={conversations.page}
                size={conversations.size}
                totalElements={conversations.totalElements}
                totalPages={conversations.totalPages}
                onPageChange={setPage}
              />
            )}
          </CardContent>
        </Card>

        <Card className="flex flex-col">
          <CardHeader>
            <CardTitle>{detail ? detail.conversation.title : "New conversation"}</CardTitle>
            <CardDescription>
              {detail
                ? `${FEATURE_LABELS[detail.conversation.feature]} · started ${new Date(detail.conversation.createdAt).toLocaleString()}`
                : "Send a message to start a new conversation."}
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-1 flex-col gap-3">
            {detailError && (
              <Alert variant="destructive">
                <AlertDescription>{detailError}</AlertDescription>
              </Alert>
            )}
            {detailLoading && <p className="py-8 text-center text-muted-foreground">Loading…</p>}
            {!detailLoading && <ChatThread messages={detail?.messages ?? []} pending={sending} />}

            <div className="mt-auto space-y-2 border-t border-border pt-3">
              {sendError && (
                <Alert variant="destructive">
                  <AlertDescription>{sendError}</AlertDescription>
                </Alert>
              )}
              {composerDisabled && (
                <p className="text-xs text-muted-foreground">
                  The assistant is not configured — sending is disabled.
                </p>
              )}
              <textarea
                className={TEXTAREA_CLASS}
                rows={3}
                maxLength={4000}
                value={composerText}
                disabled={composerDisabled || sending}
                onChange={(e) => setComposerText(e.target.value)}
                onKeyDown={handleComposerKeyDown}
                placeholder="Ask about a subject, request a study tip, or continue this conversation… (Enter to send, Shift+Enter for a new line)"
              />
              <div className="flex justify-end">
                <Button
                  type="button"
                  disabled={composerDisabled || sending || !composerText.trim()}
                  onClick={() => void handleSend()}
                >
                  {sending ? "Sending…" : "Send"}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <Dialog open={quickAction !== null} onOpenChange={(open) => !open && setQuickAction(null)}>
        <DialogContent>
          <form onSubmit={handleQuickActionSubmit}>
            <DialogHeader>
              <DialogTitle>
                {quickAction === "explain" && "Explain a topic"}
                {quickAction === "practice" && "Practice questions"}
                {quickAction === "mcq" && "MCQs"}
              </DialogTitle>
              <DialogDescription>Opens its own conversation, grounded in your academic record.</DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              {quickError && (
                <Alert variant="destructive">
                  <AlertDescription>{quickError}</AlertDescription>
                </Alert>
              )}
              <div className="space-y-1.5">
                <Label htmlFor="qa-topic">Topic</Label>
                <Input
                  id="qa-topic"
                  value={quickForm.topic}
                  maxLength={200}
                  onChange={(e) => setQuickForm((f) => ({ ...f, topic: e.target.value }))}
                  placeholder="e.g. Binary search trees"
                />
              </div>
              {quickAction === "explain" && (
                <div className="space-y-1.5">
                  <Label htmlFor="qa-focus">Focus</Label>
                  <Input
                    id="qa-focus"
                    value={quickForm.focus}
                    maxLength={500}
                    onChange={(e) => setQuickForm((f) => ({ ...f, focus: e.target.value }))}
                    placeholder="Optional — what part to focus on"
                  />
                </div>
              )}
              <div className="space-y-1.5">
                <Label htmlFor="qa-subject">Subject ID</Label>
                <Input
                  id="qa-subject"
                  type="number"
                  min={1}
                  value={quickForm.subjectId}
                  onChange={(e) => setQuickForm((f) => ({ ...f, subjectId: e.target.value }))}
                  placeholder="Optional"
                />
              </div>
              {(quickAction === "practice" || quickAction === "mcq") && (
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <Label htmlFor="qa-count">Count</Label>
                    <Input
                      id="qa-count"
                      type="number"
                      min={1}
                      max={20}
                      value={quickForm.count}
                      onChange={(e) => setQuickForm((f) => ({ ...f, count: e.target.value }))}
                      placeholder="Optional"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <Label htmlFor="qa-difficulty">Difficulty</Label>
                    <Select
                      value={quickForm.difficulty || ALL}
                      onValueChange={(value) =>
                        setQuickForm((f) => ({ ...f, difficulty: value === ALL ? "" : (value as AIDifficulty) }))
                      }
                    >
                      <SelectTrigger id="qa-difficulty" className="w-full">
                        <SelectValue placeholder="Any" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={ALL}>Any</SelectItem>
                        {DIFFICULTIES.map((d) => (
                          <SelectItem key={d} value={d}>
                            {d}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              )}
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setQuickAction(null)}>
                Cancel
              </Button>
              <Button type="submit" disabled={quickSubmitting}>
                {quickSubmitting ? "Generating…" : "Generate"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete this conversation?"
        description={
          deleteError ? (
            <span className="text-destructive">{deleteError}</span>
          ) : (
            <>This permanently deletes "{deleteTarget?.title}" and every message in it.</>
          )
        }
        confirmLabel="Delete"
        destructive
        isConfirming={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
