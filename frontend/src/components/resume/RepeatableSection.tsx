import type { ReactNode } from "react";
import { ArrowDownIcon, ArrowUpIcon, PlusIcon, Trash2Icon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { SectionCard } from "@/components/resume/SectionCard";

/**
 * Shared add / remove / move-up / move-down chrome for one of the six repeatable
 * resume sections. THE ARRAY ORDER IS THE ORDER — the backend derives `display_order`
 * from each item's index in the array it receives, so this is the only place order is
 * ever changed, and nothing here (or above it) ever attaches a `displayOrder` field to
 * an item.
 */
export function RepeatableSection<T>({
  title,
  description,
  items,
  onChange,
  newItem,
  renderItem,
  itemLabel,
  addLabel = "Add",
  emptyText = "Nothing added yet.",
  disabled = false,
}: {
  title: string;
  description?: string;
  items: T[];
  onChange: (items: T[]) => void;
  newItem: () => T;
  renderItem: (item: T, index: number, update: (patch: Partial<T>) => void) => ReactNode;
  /** Short label for row N, e.g. "Education #2" — shown in the row header. */
  itemLabel: string;
  addLabel?: string;
  emptyText?: string;
  disabled?: boolean;
}) {
  function addRow() {
    onChange([...items, newItem()]);
  }

  function removeRow(index: number) {
    onChange(items.filter((_, i) => i !== index));
  }

  function moveRow(index: number, direction: -1 | 1) {
    const target = index + direction;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  function updateRow(index: number, patch: Partial<T>) {
    onChange(items.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  }

  return (
    <SectionCard
      title={title}
      description={description}
      action={
        !disabled && (
          <Button type="button" variant="outline" size="sm" onClick={addRow}>
            <PlusIcon />
            {addLabel}
          </Button>
        )
      }
    >
      {items.length === 0 && <p className="text-sm text-muted-foreground">{emptyText}</p>}
      <div className="space-y-3">
        {items.map((item, index) => (
          <div key={index} className="rounded-lg border border-border p-3">
            <div className="mb-2 flex items-center justify-between gap-2">
              <span className="text-xs font-medium text-muted-foreground">
                {itemLabel} #{index + 1}
              </span>
              {!disabled && (
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    disabled={index === 0}
                    onClick={() => moveRow(index, -1)}
                    aria-label="Move up"
                  >
                    <ArrowUpIcon />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    disabled={index === items.length - 1}
                    onClick={() => moveRow(index, 1)}
                    aria-label="Move down"
                  >
                    <ArrowDownIcon />
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => removeRow(index)}
                    aria-label="Remove"
                  >
                    <Trash2Icon className="text-destructive" />
                  </Button>
                </div>
              )}
            </div>
            {renderItem(item, index, (patch) => updateRow(index, patch))}
          </div>
        ))}
      </div>
    </SectionCard>
  );
}
