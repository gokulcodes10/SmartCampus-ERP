import { useCallback, useEffect, useRef, useState } from "react";
import { RotateCwIcon } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { fetchResumePdf } from "@/services/resumeService";
import { extractErrorMessage } from "@/utils/apiError";

/**
 * Renders the REAL generated PDF, not an HTML lookalike — a second, HTML-based
 * renderer that could disagree with the actual PDF is exactly the kind of near-fake
 * this project forbids (§69). There is no id until the resume has been saved at least
 * once, so `resumeId === null` shows a placeholder instead of attempting a request.
 *
 * `reloadToken` lets the parent force a refetch after a save even when `resumeId`
 * itself hasn't changed (e.g. saving an already-persisted resume again).
 */
export function ResumePdfPreview({
  resumeId,
  reloadToken = 0,
}: {
  resumeId: number | null;
  reloadToken?: number;
}) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const urlRef = useRef<string | null>(null);

  const load = useCallback(() => {
    if (resumeId === null) return;
    setIsLoading(true);
    setError(null);
    fetchResumePdf(resumeId)
      .then((blob) => {
        const next = URL.createObjectURL(blob);
        if (urlRef.current) URL.revokeObjectURL(urlRef.current);
        urlRef.current = next;
        setObjectUrl(next);
      })
      .catch((err) => setError(extractErrorMessage(err, "Failed to load the PDF preview.")))
      .finally(() => setIsLoading(false));
  }, [resumeId]);

  useEffect(() => {
    if (resumeId === null) {
      if (urlRef.current) {
        URL.revokeObjectURL(urlRef.current);
        urlRef.current = null;
      }
      setObjectUrl(null);
      return;
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resumeId, reloadToken]);

  // Revoke whatever's outstanding on unmount so the tab doesn't leak a blob.
  useEffect(() => {
    return () => {
      if (urlRef.current) URL.revokeObjectURL(urlRef.current);
    };
  }, []);

  if (resumeId === null) {
    return (
      <div className="flex h-64 items-center justify-center rounded-lg border border-dashed border-border text-sm text-muted-foreground">
        Save this resume to preview it as a PDF.
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-muted-foreground">Preview</span>
        <Button type="button" variant="outline" size="sm" onClick={load} disabled={isLoading}>
          <RotateCwIcon className={isLoading ? "animate-spin" : undefined} />
          {isLoading ? "Loading…" : "Refresh preview"}
        </Button>
      </div>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {objectUrl && (
        <iframe className="h-[800px] w-full rounded-lg border border-border" src={objectUrl} title="Resume preview" />
      )}
      {!objectUrl && isLoading && (
        <div className="flex h-64 items-center justify-center rounded-lg border border-dashed border-border text-sm text-muted-foreground">
          Rendering preview…
        </div>
      )}
    </div>
  );
}
