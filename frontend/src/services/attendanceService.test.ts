import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import api from "@/services/api";
import { listMine } from "@/services/attendanceService";
import type { Page } from "@/types/academic";
import type { AttendanceResponse } from "@/types/academicOps";

vi.mock("@/services/api", () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}));

beforeEach(() => {
  vi.clearAllMocks();
});

const envelope: Page<AttendanceResponse> = {
  content: [
    {
      id: 1,
      studentId: 5,
      studentRegisterNumber: "CS2026001",
      studentName: "Ada Lovelace",
      subjectId: 3,
      subjectCode: "CS301",
      subjectName: "Operating Systems",
      academicYear: "2025-26",
      semester: 3,
      section: "A",
      date: "2026-08-20",
      period: 1,
      status: "PRESENT",
      remarks: null,
      markedByFacultyId: 7,
      markedByFacultyName: "Dr. Smith",
      createdAt: "2026-08-20T09:00:00Z",
      updatedAt: "2026-08-20T09:00:00Z",
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe("attendanceService.listMine", () => {
  it("GETs /api/attendance/me with the given query params", async () => {
    (api.get as Mock).mockResolvedValue({ data: envelope });

    await listMine({ academicYear: "2025-26", semester: 3, subjectId: 3, page: 0, size: 20 });

    expect(api.get).toHaveBeenCalledWith("/api/attendance/me", {
      params: { academicYear: "2025-26", semester: 3, subjectId: 3, page: 0, size: 20 },
    });
  });

  it("correctly parses the §44 pagination envelope", async () => {
    (api.get as Mock).mockResolvedValue({ data: envelope });

    const result = await listMine();

    expect(result).toEqual(envelope);
    expect(result.content[0].status).toBe("PRESENT");
    expect(result.page).toBe(0);
    expect(result.totalPages).toBe(1);
  });

  it("defaults to an empty params object when none are given", async () => {
    (api.get as Mock).mockResolvedValue({ data: envelope });

    await listMine();

    expect(api.get).toHaveBeenCalledWith("/api/attendance/me", { params: {} });
  });
});
