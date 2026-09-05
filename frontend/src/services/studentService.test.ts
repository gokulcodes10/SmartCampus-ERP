import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import api from "@/services/api";
import { listStudents } from "@/services/studentService";
import type { Page, StudentResponse } from "@/types/academic";

vi.mock("@/services/api", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
});

const envelope: Page<StudentResponse> = {
  content: [
    {
      id: 1,
      userId: 10,
      email: "student@example.com",
      fullName: "Ada Lovelace",
      registerNumber: "CS2026001",
      departmentId: 1,
      departmentName: "Computer Science",
      courseId: 1,
      courseName: "B.Tech CS",
      currentSemester: 3,
      section: "A",
      admissionYear: 2024,
      status: "ACTIVE",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    },
  ],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
};

describe("studentService.listStudents", () => {
  it("GETs /api/students and translates the shared `search` param to the backend's `q`", async () => {
    (api.get as Mock).mockResolvedValue({ data: envelope });

    await listStudents({ page: 0, size: 10, search: "Ada", status: "ACTIVE" });

    expect(api.get).toHaveBeenCalledWith("/api/students", {
      params: { page: 0, size: 10, status: "ACTIVE", q: "Ada" },
    });
  });

  it("correctly parses the §44 pagination envelope", async () => {
    (api.get as Mock).mockResolvedValue({ data: envelope });

    const result = await listStudents({ page: 0, size: 10 });

    expect(result).toEqual(envelope);
    expect(result.content).toHaveLength(1);
    expect(result.totalElements).toBe(1);
    expect(result.totalPages).toBe(1);
  });

  it("omits `sort` — the backend does not accept it for this endpoint", async () => {
    (api.get as Mock).mockResolvedValue({ data: envelope });

    await listStudents({ page: 0, size: 10, sort: "fullName,asc" });

    const [, config] = (api.get as Mock).mock.calls[0];
    expect(config.params).not.toHaveProperty("sort");
  });
});
