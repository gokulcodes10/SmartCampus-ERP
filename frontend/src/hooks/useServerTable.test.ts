import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useServerTable } from "@/hooks/useServerTable";
import type { Page } from "@/types/academic";

interface Row {
  id: number;
  name: string;
}

function pageOf(items: Row[], page = 0, size = 10): Page<Row> {
  return { content: items, page, size, totalElements: items.length, totalPages: 1 };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useServerTable", () => {
  it("fetches page 0 on mount with the given filters and no search term", async () => {
    const fetchPage = vi.fn().mockResolvedValue(pageOf([{ id: 1, name: "Ada" }]));

    const { result } = renderHook(() =>
      useServerTable<Row, { departmentId?: number }>(fetchPage, { departmentId: 5 }),
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(fetchPage).toHaveBeenCalledWith({
      page: 0,
      size: 10,
      sort: undefined,
      search: undefined,
      departmentId: 5,
    });
    expect(result.current.data?.content).toEqual([{ id: 1, name: "Ada" }]);
  });

  it("refetches with the new page when setPage is called", async () => {
    const fetchPage = vi.fn().mockResolvedValue(pageOf([]));
    const { result } = renderHook(() => useServerTable<Row, Record<string, never>>(fetchPage, {}));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    fetchPage.mockClear();

    act(() => result.current.setPage(2));

    await waitFor(() => expect(fetchPage).toHaveBeenCalledWith(expect.objectContaining({ page: 2 })));
  });

  it("debounces search and resets to page 0 when a new search term settles", async () => {
    const fetchPage = vi.fn().mockResolvedValue(pageOf([]));
    const { result } = renderHook(() => useServerTable<Row, Record<string, never>>(fetchPage, {}));
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    act(() => result.current.setPage(3));
    await waitFor(() => expect(fetchPage).toHaveBeenCalledWith(expect.objectContaining({ page: 3 })));
    fetchPage.mockClear();

    act(() => result.current.setSearch("ada"));

    // Immediately after setSearch, the debounce has not yet elapsed — no
    // fetch with the new term should have fired yet.
    expect(fetchPage).not.toHaveBeenCalledWith(expect.objectContaining({ search: "ada" }));

    await waitFor(
      () => expect(fetchPage).toHaveBeenCalledWith(expect.objectContaining({ search: "ada", page: 0 })),
      { timeout: 1000 },
    );
  });

  it("surfaces a failed fetch as `error` rather than throwing", async () => {
    const fetchPage = vi.fn().mockRejectedValue(new Error("network down"));
    const { result } = renderHook(() => useServerTable<Row, Record<string, never>>(fetchPage, {}));

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.error).toBeTruthy();
    expect(result.current.data).toBeNull();
  });

  it("refresh() re-issues the same request without changing page or search", async () => {
    const fetchPage = vi.fn().mockResolvedValue(pageOf([]));
    const { result } = renderHook(() => useServerTable<Row, Record<string, never>>(fetchPage, {}));
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    const callsBefore = fetchPage.mock.calls.length;

    act(() => result.current.refresh());

    await waitFor(() => expect(fetchPage.mock.calls.length).toBe(callsBefore + 1));
    expect(result.current.page).toBe(0);
  });
});
