import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";

import { Button } from "@/components/ui/button";

interface PaginationBarProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/** Prev/next pager for a §44 page envelope. `page` is 0-indexed. */
export function PaginationBar({ page, size, totalElements, totalPages, onPageChange }: PaginationBarProps) {
  if (totalElements === 0) return null;

  const start = page * size + 1;
  const end = Math.min(totalElements, (page + 1) * size);

  return (
    <div className="flex flex-col items-center justify-between gap-3 border-t border-border px-2 py-3 sm:flex-row">
      <p className="text-xs text-muted-foreground">
        Showing <span className="font-medium text-foreground">{start}</span>–
        <span className="font-medium text-foreground">{end}</span> of{" "}
        <span className="font-medium text-foreground">{totalElements}</span>
      </p>
      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="default"
          disabled={page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeftIcon />
          Previous
        </Button>
        <span className="text-xs text-muted-foreground">
          Page {page + 1} of {Math.max(totalPages, 1)}
        </span>
        <Button
          type="button"
          variant="outline"
          size="default"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next
          <ChevronRightIcon />
        </Button>
      </div>
    </div>
  );
}
