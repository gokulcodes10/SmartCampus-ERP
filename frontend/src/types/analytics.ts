/**
 * Types for the Phase 5 (Analytics) API: performance bands, the analytics
 * dashboards for student/faculty/admin, and every projection DTO they're built
 * from. These mirror `backend/src/main/resources/db/migration/V5__analytics.sql`
 * and the Phase 5 entity/DTO contract handed down for this wave (see
 * PROJECT_PLAN.md §3 and the build-agent context brief / phase contract).
 *
 * Every backend `BigDecimal` serializes as a JSON number, or `null` when there is
 * no denominator / no data (G6-style rule, generalized across the whole phase) —
 * render that as an explicit empty state, never as 0. Every `Long`/`int` count is
 * a plain `number`. Every `LocalDate` is `"YYYY-MM-DD"`; every `LocalDateTime` an
 * ISO string.
 *
 * `attendance` and `academics` on `AnalyticsStudentResponse` reuse the EXISTING
 * `AttendanceSummaryResponse` / `AcademicResultResponse` types from
 * `@/types/academicOps` verbatim — re-exported here, not redeclared.
 */

export type {
  AttendanceSummaryResponse,
  AcademicResultResponse,
} from "@/types/academicOps";
import type { AttendanceSummaryResponse, AcademicResultResponse } from "@/types/academicOps";

export type PerformanceCategory = "EXCELLENT" | "GOOD" | "AVERAGE" | "AT_RISK";

// ---------------------------------------------------------------------------------
// Performance bands
// ---------------------------------------------------------------------------------

export interface PerformanceBandResponse {
  id: number;
  category: PerformanceCategory;
  displayOrder: number;
  minMarksPercentage: number;
  minAttendancePercentage: number;
  minGpa: number | null;
  colorHex: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PerformanceClassificationResponse {
  category: PerformanceCategory | null;
  colorHex: string | null;
  description: string | null;
  marksPercentage: number | null;
  attendancePercentage: number | null;
  gpa: number | null;
  reason: string;
}

// ---------------------------------------------------------------------------------
// Trend / distribution points
// ---------------------------------------------------------------------------------

export interface AttendanceTrendPoint {
  period: string;
  periodStart: string;
  heldClasses: number;
  attendedClasses: number;
  attendancePercentage: number | null;
}

export interface MarksTrendPoint {
  period: string;
  periodStart: string;
  examCount: number;
  totalObtained: number | null;
  totalMaximum: number | null;
  marksPercentage: number | null;
}

export interface SemesterGpaPoint {
  academicYear: string;
  semester: number;
  subjectCount: number;
  gradedCredits: number;
  gpa: number | null;
}

export interface GradeDistributionSlice {
  grade: string;
  gradePoint: number;
  minPercentage: number;
  maxPercentage: number;
  count: number;
}

export interface ClassificationSlice {
  category: PerformanceCategory;
  colorHex: string;
  description: string | null;
  studentCount: number;
  shareOfCohort: number | null;
}

export interface SubjectAveragePoint {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  credits: number | null;
  studentCount: number | null;
  heldClasses: number | null;
  attendedClasses: number | null;
  attendancePercentage: number | null;
  examCount: number | null;
  totalObtained: number | null;
  totalMaximum: number | null;
  marksPercentage: number | null;
}

export interface ExamAveragePoint {
  examId: number;
  title: string;
  examType: string;
  examDate: string;
  maximumMarks: number;
  marksEnteredCount: number;
  totalObtained: number | null;
  averageObtained: number | null;
  averagePercentage: number | null;
  highestObtained: number | null;
  lowestObtained: number | null;
}

// ---------------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------------

export interface SubjectPerformanceRow {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  credits: number | null;
  academicYear: string;
  semester: number;
  heldClasses: number | null;
  attendedClasses: number | null;
  attendancePercentage: number | null;
  examCount: number | null;
  totalObtained: number | null;
  totalMaximum: number | null;
  marksPercentage: number | null;
  grade: string | null;
  gradePoint: number | null;
  passed: boolean | null;
  classification: PerformanceCategory | null;
  classificationColorHex: string | null;
}

export interface CohortStudentRow {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  departmentId: number | null;
  departmentName: string | null;
  courseId: number | null;
  courseName: string | null;
  heldClasses: number | null;
  attendedClasses: number | null;
  attendancePercentage: number | null;
  examCount: number | null;
  totalObtained: number | null;
  totalMaximum: number | null;
  marksPercentage: number | null;
  gradedCredits: number;
  gpa: number | null;
  classification: PerformanceCategory | null;
  classificationColorHex: string | null;
}

export interface DepartmentPerformanceRow {
  departmentId: number | null;
  departmentCode: string | null;
  departmentName: string | null;
  studentCount: number;
  attendancePercentage: number | null;
  marksPercentage: number | null;
  averageGpa: number | null;
}

export interface SemesterPerformancePoint {
  academicYear: string;
  semester: number;
  studentCount: number;
  attendancePercentage: number | null;
  marksPercentage: number | null;
  averageGpa: number | null;
}

// ---------------------------------------------------------------------------------
// Filters
// ---------------------------------------------------------------------------------

export interface FilterCourseOption {
  id: number;
  code: string;
  name: string;
}

export interface FilterSubjectOption {
  id: number;
  code: string;
  name: string;
  courseId: number;
  semester: number;
}

export interface AnalyticsFilterOptionsResponse {
  courses: FilterCourseOption[];
  subjects: FilterSubjectOption[];
  academicYears: string[];
  semesters: number[];
  sections: string[];
}

// ---------------------------------------------------------------------------------
// Top-level dashboard responses
// ---------------------------------------------------------------------------------

export interface AnalyticsStudentResponse {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  departmentId: number | null;
  departmentName: string | null;
  courseId: number | null;
  courseName: string | null;
  currentSemester: number | null;
  section: string | null;
  academicYear: string | null;
  semester: number | null;
  trendMonths: number;
  attendance: AttendanceSummaryResponse;
  academics: AcademicResultResponse;
  marksPercentage: number | null;
  attendancePercentage: number | null;
  gpa: number | null;
  cgpa: number | null;
  classification: PerformanceClassificationResponse;
  attendanceTrend: AttendanceTrendPoint[];
  marksTrend: MarksTrendPoint[];
  gpaTrend: SemesterGpaPoint[];
  gradeDistribution: GradeDistributionSlice[];
  subjects: SubjectPerformanceRow[];
}

export interface AnalyticsClassResponse {
  courseId: number | null;
  courseCode: string | null;
  courseName: string | null;
  subjectId: number | null;
  subjectCode: string | null;
  subjectName: string | null;
  academicYear: string | null;
  semester: number | null;
  section: string | null;
  trendMonths: number;
  studentCount: number;
  classifiedCount: number;
  unclassifiedCount: number;
  heldClasses: number | null;
  attendedClasses: number | null;
  cancelledClasses: number | null;
  attendancePercentage: number | null;
  examCount: number | null;
  totalObtained: number | null;
  totalMaximum: number | null;
  marksPercentage: number | null;
  averageGpa: number | null;
  students: CohortStudentRow[];
  subjectAverages: SubjectAveragePoint[];
  examAverages: ExamAveragePoint[];
  attendanceTrend: AttendanceTrendPoint[];
  marksTrend: MarksTrendPoint[];
  classificationDistribution: ClassificationSlice[];
  gradeDistribution: GradeDistributionSlice[];
}

export interface AnalyticsAdminResponse {
  departmentId: number | null;
  departmentName: string | null;
  courseId: number | null;
  courseName: string | null;
  academicYear: string | null;
  semester: number | null;
  section: string | null;
  trendMonths: number;
  totalStudents: number;
  activeStudents: number;
  pendingStudents: number;
  totalFaculty: number;
  activeFaculty: number;
  totalDepartments: number;
  totalCourses: number;
  totalSubjects: number;
  totalExams: number;
  studentCount: number;
  classifiedCount: number;
  unclassifiedCount: number;
  heldClasses: number | null;
  attendedClasses: number | null;
  cancelledClasses: number | null;
  attendancePercentage: number | null;
  examCount: number | null;
  totalObtained: number | null;
  totalMaximum: number | null;
  marksPercentage: number | null;
  averageGpa: number | null;
  departments: DepartmentPerformanceRow[];
  semesters: SemesterPerformancePoint[];
  subjectAverages: SubjectAveragePoint[];
  attendanceTrend: AttendanceTrendPoint[];
  marksTrend: MarksTrendPoint[];
  classificationDistribution: ClassificationSlice[];
  gradeDistribution: GradeDistributionSlice[];
  atRiskStudents: CohortStudentRow[];
}
