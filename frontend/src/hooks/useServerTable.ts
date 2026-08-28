import { useCallback, useEffect, useRef, useState } from "react";

import type { Page } from "@/types/academic";
import { extractErrorMessage } from "@/utils/apiError";

/**
 * Drives one admin table against a real server-side paginated/filtered/sorted list
 * endpoint (§44). Nothing here ever fetches "everything and filters in the browser" —
 * every page/search/filter change re-issues the API call with the new params.
 *
 * `filters` is any extra, resource-specific query params (e.g. `{ departmentId }`);
 * changing it (by identity of its serialized form) resets to page 0 and refetches, same
 * as a search change. Debounces `search` by 350ms so typing doesn't fire a request per
 * keystroke.
 */
export function useServerTable<T, F extends Record<string, unknown>>(
  fetchPage: (params: { page: number; size: number; sort?: string; search?: string } & F) => Promise<Page<T>>,
  filters: F,
  options: { pageSize?: number; sort?: string } = {},
) {
  const size = options.pageSize ?? 10;
  const sort = options.sort;

  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [data, setData] = useState<Page<T> | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const filtersKey = JSON.stringify(filters);
  const isFirstRun = useRef(true);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 350);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    if (isFirstRun.current) {
      isFirstRun.current = false;
      return;
    }
    setPage(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedSearch, filtersKey]);

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    fetchPage({ page, size, sort, search: debouncedSearch || undefined, ...filters })
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(extractErrorMessage(err, "Failed to load data."));
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, size, sort, debouncedSearch, filtersKey, reloadToken]);

  const refresh = useCallback(() => setReloadToken((t) => t + 1), []);

  return { data, isLoading, error, page, setPage, search, setSearch, size, refresh };
}
