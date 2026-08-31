import { BrowserRouter, Route, Routes } from "react-router-dom";

import { AuthProvider } from "@/context/AuthContext";
import { NotificationProvider } from "@/context/NotificationContext";
import { AdminSectionLayout } from "@/layouts/AdminSectionLayout";
import { AuthLayout } from "@/layouts/AuthLayout";
import { DashboardLayout } from "@/layouts/DashboardLayout";
import ForgotPasswordPage from "@/pages/auth/ForgotPasswordPage";
import LoginPage from "@/pages/auth/LoginPage";
import RegisterPage from "@/pages/auth/RegisterPage";
import ResetPasswordPage from "@/pages/auth/ResetPasswordPage";
import VerifyOtpPage from "@/pages/auth/VerifyOtpPage";
import AdminAnnouncementsPage from "@/pages/admin/AdminAnnouncementsPage";
import AdminCodingContestsPage from "@/pages/admin/AdminCodingContestsPage";
import AdminCodingProblemsPage from "@/pages/admin/AdminCodingProblemsPage";
import AdminInterviewQuestionsPage from "@/pages/admin/AdminInterviewQuestionsPage";
import AdminInterviewsPage from "@/pages/admin/AdminInterviewsPage";
import CompaniesPage from "@/pages/admin/CompaniesPage";
import CoursesPage from "@/pages/admin/CoursesPage";
import DepartmentsPage from "@/pages/admin/DepartmentsPage";
import FacultyPage from "@/pages/admin/FacultyPage";
import GradeBandsPage from "@/pages/admin/GradeBandsPage";
import JobApplicantsPage from "@/pages/admin/JobApplicantsPage";
import JobsPage from "@/pages/admin/JobsPage";
import PlacementAnalyticsPage from "@/pages/admin/PlacementAnalyticsPage";
import StudentsPage from "@/pages/admin/StudentsPage";
import SubjectsPage from "@/pages/admin/SubjectsPage";
import ContestDetailPage from "@/pages/coding/ContestDetailPage";
import ContestListPage from "@/pages/coding/ContestListPage";
import GlobalLeaderboardPage from "@/pages/coding/GlobalLeaderboardPage";
import ProblemDetailPage from "@/pages/coding/ProblemDetailPage";
import ProblemListPage from "@/pages/coding/ProblemListPage";
import SubmissionsPage from "@/pages/coding/SubmissionsPage";
import AdminDashboardPage from "@/pages/dashboard/AdminDashboardPage";
import FacultyDashboardPage from "@/pages/dashboard/FacultyDashboardPage";
import StudentDashboardPage from "@/pages/dashboard/StudentDashboardPage";
import FacultyAnalyticsPage from "@/pages/faculty/FacultyAnalyticsPage";
import FacultyAttendancePage from "@/pages/faculty/FacultyAttendancePage";
import FacultyExamsPage from "@/pages/faculty/FacultyExamsPage";
import FacultyMarksPage from "@/pages/faculty/FacultyMarksPage";
import NotFoundPage from "@/pages/NotFoundPage";
import NotificationsPage from "@/pages/NotificationsPage";
import AnnouncementsPage from "@/pages/AnnouncementsPage";
import AdminAnalyticsPage from "@/pages/admin/AdminAnalyticsPage";
import PerformanceBandsPage from "@/pages/admin/PerformanceBandsPage";
import StudentAIAssistantPage from "@/pages/student/StudentAIAssistantPage";
import StudentAnalyticsPage from "@/pages/student/StudentAnalyticsPage";
import StudentApplicationsPage from "@/pages/student/StudentApplicationsPage";
import StudentAttendancePage from "@/pages/student/StudentAttendancePage";
import StudentInterviewPrepPage from "@/pages/student/StudentInterviewPrepPage";
import StudentInterviewsPage from "@/pages/student/StudentInterviewsPage";
import StudentJobDetailPage from "@/pages/student/StudentJobDetailPage";
import StudentJobsPage from "@/pages/student/StudentJobsPage";
import StudentMarksPage from "@/pages/student/StudentMarksPage";
import StudentResumeEditorPage from "@/pages/student/StudentResumeEditorPage";
import StudentResumesPage from "@/pages/student/StudentResumesPage";
import StudentStudyPlansPage from "@/pages/student/StudentStudyPlansPage";
import { ProtectedRoute } from "@/routes/ProtectedRoute";
import { RoleRoute } from "@/routes/RoleRoute";
import { RootRedirect } from "@/routes/RootRedirect";

/**
 * The full route tree (Phase 2 auth + Phase 3 admin academic management).
 * Self-contained — owns its own `BrowserRouter` and `AuthProvider` — so
 * `App.tsx` (shared, integrator-owned) only needs to render `<AppRouter />`.
 */
export function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <NotificationProvider>
          <Routes>
            <Route element={<AuthLayout />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/verify-otp" element={<VerifyOtpPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
            </Route>

            <Route element={<ProtectedRoute />}>
              <Route element={<DashboardLayout />}>
                <Route path="/notifications" element={<NotificationsPage />} />
                <Route path="/announcements" element={<AnnouncementsPage />} />
                <Route element={<RoleRoute allowedRoles={["STUDENT"]} />}>
                  <Route path="/student" element={<StudentDashboardPage />} />
                  <Route path="/student/attendance" element={<StudentAttendancePage />} />
                  <Route path="/student/marks" element={<StudentMarksPage />} />
                  <Route path="/student/analytics" element={<StudentAnalyticsPage />} />
                  <Route path="/student/ai" element={<StudentAIAssistantPage />} />
                  <Route path="/student/study-plans" element={<StudentStudyPlansPage />} />
                  <Route path="/student/jobs" element={<StudentJobsPage />} />
                  <Route path="/student/jobs/:jobId" element={<StudentJobDetailPage />} />
                  <Route path="/student/applications" element={<StudentApplicationsPage />} />
                  <Route path="/student/resumes" element={<StudentResumesPage />} />
                  <Route path="/student/resumes/new" element={<StudentResumeEditorPage />} />
                  <Route path="/student/resumes/:id" element={<StudentResumeEditorPage />} />
                  <Route path="/student/interview-prep" element={<StudentInterviewPrepPage />} />
                  <Route path="/student/interviews" element={<StudentInterviewsPage />} />
                </Route>
                <Route element={<RoleRoute allowedRoles={["FACULTY"]} />}>
                  <Route path="/faculty" element={<FacultyDashboardPage />} />
                  <Route path="/faculty/attendance" element={<FacultyAttendancePage />} />
                  <Route path="/faculty/exams" element={<FacultyExamsPage />} />
                  <Route path="/faculty/marks" element={<FacultyMarksPage />} />
                  <Route path="/faculty/analytics" element={<FacultyAnalyticsPage />} />
                </Route>
                <Route element={<RoleRoute allowedRoles={["ADMIN"]} />}>
                  <Route element={<AdminSectionLayout />}>
                    <Route path="/admin" element={<AdminDashboardPage />} />
                    <Route path="/admin/departments" element={<DepartmentsPage />} />
                    <Route path="/admin/courses" element={<CoursesPage />} />
                    <Route path="/admin/subjects" element={<SubjectsPage />} />
                    <Route path="/admin/students" element={<StudentsPage />} />
                    <Route path="/admin/faculty" element={<FacultyPage />} />
                    <Route path="/admin/grade-bands" element={<GradeBandsPage />} />
                    <Route path="/admin/coding/problems" element={<AdminCodingProblemsPage />} />
                    <Route path="/admin/coding/contests" element={<AdminCodingContestsPage />} />
                    <Route path="/admin/analytics" element={<AdminAnalyticsPage />} />
                    <Route path="/admin/performance-bands" element={<PerformanceBandsPage />} />
                    <Route path="/admin/companies" element={<CompaniesPage />} />
                    <Route path="/admin/jobs" element={<JobsPage />} />
                    <Route path="/admin/jobs/:jobId/applicants" element={<JobApplicantsPage />} />
                    <Route path="/admin/placement/analytics" element={<PlacementAnalyticsPage />} />
                    <Route path="/admin/interview-questions" element={<AdminInterviewQuestionsPage />} />
                    <Route path="/admin/interviews" element={<AdminInterviewsPage />} />
                    <Route path="/admin/announcements" element={<AdminAnnouncementsPage />} />
                  </Route>
                </Route>

                <Route path="/coding" element={<ProblemListPage />} />
                <Route path="/coding/problems/:problemId" element={<ProblemDetailPage />} />
                <Route path="/coding/submissions" element={<SubmissionsPage />} />
                <Route path="/coding/contests" element={<ContestListPage />} />
                <Route path="/coding/contests/:contestId" element={<ContestDetailPage />} />
                <Route path="/coding/leaderboard" element={<GlobalLeaderboardPage />} />
              </Route>
            </Route>

            <Route path="/" element={<RootRedirect />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </NotificationProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default AppRouter;
