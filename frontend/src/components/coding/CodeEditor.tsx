import Editor, { type OnMount } from "@monaco-editor/react";

/**
 * The ONLY file in this codebase that imports monaco. Every other component depends
 * on this component's props, not on `@monaco-editor/react` directly, so a future swap
 * of editor library only touches this one file.
 *
 * `@monaco-editor/react` is used with its default loader, which fetches the monaco
 * runtime from a CDN (jsdelivr) at runtime rather than being bundled by Vite — that is
 * the library's standard zero-config setup and needs no changes to `vite.config.ts`
 * (which this build does not own). It requires the browser to reach that CDN; see the
 * build report for what that implies for fully offline dev.
 *
 * `monaco-editor` is installed locally alongside `@monaco-editor/react` only so
 * TypeScript can resolve the package's own type re-exports during `tsc -b`; it is not
 * imported directly here.
 */

interface CodeEditorProps {
  /** Monaco language id, e.g. "java" or "cpp" (see LanguageResponse.monacoLanguageId). */
  language: string;
  value: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  height?: string | number;
  /** Shown while the monaco runtime is still loading from the CDN. */
  loadingLabel?: string;
}

export function CodeEditor({
  language,
  value,
  onChange,
  readOnly = false,
  height = 420,
  loadingLabel = "Loading editor…",
}: CodeEditorProps) {
  const handleMount: OnMount = (editor) => {
    editor.updateOptions({ tabSize: 4, insertSpaces: true });
  };

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <Editor
        height={height}
        language={language}
        value={value}
        theme="vs-dark"
        onChange={(next) => onChange?.(next ?? "")}
        onMount={handleMount}
        loading={<div className="flex h-full items-center justify-center text-sm text-muted-foreground">{loadingLabel}</div>}
        options={{
          readOnly,
          minimap: { enabled: false },
          fontSize: 13,
          scrollBeyondLastLine: false,
          automaticLayout: true,
          wordWrap: "on",
        }}
      />
    </div>
  );
}

export default CodeEditor;
