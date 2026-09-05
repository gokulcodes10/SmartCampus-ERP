import { MemoryRouter } from "react-router-dom";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Mock } from "vitest";

import FacultyAnnouncementsPage from "@/pages/faculty/FacultyAnnouncementsPage";
import * as announcementService from "@/services/announcementService";
import * as facultyService from "@/services/facultyService";
import type { FacultyResponse } from "@/types/academic";
import type { AnnouncementPage } from "@/types/realtime";

vi.mock("@/services/announcementService", () => ({
  listManaged: vi.fn(),
  createAnnouncement: vi.fn(),
  updateAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn(),
}));
vi.mock("@/services/facultyService", () => ({ getMyFacultyProfile: vi.fn() }));

const profile: FacultyResponse = {
  id: 9,
  userId: 100,
  email: "faculty1@smartcampus.local",
  fullName: "Dr. Ramesh Iyer",
  employeeCode: "FAC-CSE-001",
  departmentId: 11,
  departmentName: "Computer Science",
  designation: "Assistant Professor",
  status: "ACTIVE",
  createdAt: "2026-08-31T19:25:20",
  updatedAt: "2026-08-31T19:25:20",
};

const emptyPage: AnnouncementPage = { content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  vi.clearAllMocks();
  (facultyService.getMyFacultyProfile as Mock).mockResolvedValue(profile);
  (announcementService.listManaged as Mock).mockResolvedValue(emptyPage);
  (announcementService.createAnnouncement as Mock).mockResolvedValue({});
});

function renderPage() {
  return render(
    <MemoryRouter>
      <FacultyAnnouncementsPage />
    </MemoryRouter>,
  );
}

describe("FacultyAnnouncementsPage", () => {
  it("names the caller's own department as the audience", async () => {
    renderPage();

    expect(
      await screen.findByText(/Announcements you have published to the Computer Science department\./i),
    ).toBeInTheDocument();
  });

  it("sends audience DEPARTMENT and omits departmentId entirely when publishing", async () => {
    // The server fills in the caller's own department, the only value it would accept.
    // Sending a departmentId from the client — even the right one — is not this page's
    // decision to make, and sending a wrong one is a 403.
    renderPage();
    await screen.findByRole("button", { name: /new announcement/i });

    await userEvent.click(screen.getByRole("button", { name: /new announcement/i }));
    await userEvent.type(screen.getByLabelText(/title/i), "Lab rescheduled");
    await userEvent.type(screen.getByLabelText(/body/i), "Thursday's lab moves to Friday.");
    await userEvent.click(screen.getByRole("button", { name: /^publish$/i }));

    await waitFor(() => expect(announcementService.createAnnouncement).toHaveBeenCalledTimes(1));
    const payload = (announcementService.createAnnouncement as Mock).mock.calls[0][0];
    expect(payload.audience).toBe("DEPARTMENT");
    expect(payload).not.toHaveProperty("departmentId");
    expect(payload.title).toBe("Lab rescheduled");
  });

  it("offers no audience or department selector — only the priority one", async () => {
    // Rendering choices the server answers with 403 would be a control that does nothing.
    // Counting the comboboxes is the assertion that actually bites: an audience or
    // department Select added later would push this above one and fail here.
    renderPage();
    await screen.findByRole("button", { name: /new announcement/i });

    await userEvent.click(screen.getByRole("button", { name: /new announcement/i }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getAllByRole("combobox")).toHaveLength(1);
    expect(within(dialog).getByText(/Computer Science/)).toBeInTheDocument();
    expect(
      within(dialog).getByText(/Faculty announcements always go to your own department\./i),
    ).toBeInTheDocument();
  });

  it("refuses to publish an empty announcement and does not call the API", async () => {
    renderPage();
    await screen.findByRole("button", { name: /new announcement/i });

    await userEvent.click(screen.getByRole("button", { name: /new announcement/i }));
    await userEvent.click(screen.getByRole("button", { name: /^publish$/i }));

    expect(await screen.findByText("Title is required.")).toBeInTheDocument();
    expect(screen.getByText("Body is required.")).toBeInTheDocument();
    expect(announcementService.createAnnouncement).not.toHaveBeenCalled();
  });

  it("requests the managed list without any client-supplied ownership flag", async () => {
    // Scoping a faculty caller to their own rows is the server's decision, made in
    // AnnouncementService.manage. A "mine" flag here would imply it were the client's.
    renderPage();

    await waitFor(() => expect(announcementService.listManaged).toHaveBeenCalled());
    const params = (announcementService.listManaged as Mock).mock.calls[0][0];
    expect(params).not.toHaveProperty("mine");
    expect(params).not.toHaveProperty("createdById");
  });

  it("blocks composing, with an explanation, when the account has no faculty profile", async () => {
    (facultyService.getMyFacultyProfile as Mock).mockRejectedValue(new Error("no profile"));
    renderPage();

    await waitFor(() =>
      expect(screen.getByRole("button", { name: /new announcement/i })).toBeDisabled(),
    );
    expect(screen.getByText(/You can still read and manage announcements you have already published\./i))
      .toBeInTheDocument();
  });
});
