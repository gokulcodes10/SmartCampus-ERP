/**
 * Types for the Phase 4 (Academic Operations) API: teaching-scope discovery,
 * attendance, exams, marks and grade bands. These mirror
 * `backend/src/main/resources/db/migration/V4__academic_operations.sql` and the
 * Phase 4 entity/DTO contract handed down for this wave (see PROJECT_PLAN.md §3 and
 * the build-agent context brief / phase contract).
 *
 * Reuses `Page<T>` and `ListParams` from `@/types/academic` — do not redeclare them
 * here. Every backend `BigDecimal` serializes as a JSON number; every `LocalDate` as
 * `"YYYY-MM-DD"`; every `LocalDateTime` as an ISO string.
 */

export type AttendanceStatus = "PRESENT" | "ABSENT" | "LATE" | "ON_DUTY" | "CANCELLED";
export type ExamType = "INTERNAL_1" | "INTERNAL_2" | "INTERNAL_3" | "ASSIGNMENT" | "QUIZ" | "MODEL" | "SEMESTER";
export type ExamStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED";

// ---------------------------------------------------------------------------------
// Teaching scope discovery
// ---------------------------------------------------------------------------------

export interface TeachingClassResponse {
  assignmentId: number;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  subjectCredits: number;
  courseId: number;
  courseCode: string;
  courseName: string;
  academicYear: string;
  semester: number;
  section: string;
  enrolledStudentCount: number;
}

// ---------------------------------------------------------------------------------
// Attendance
// ---------------------------------------------------------------------------------

export interface AttendanceMarkEntry {
  studentId: number;
  status: AttendanceStatus;
  remarks?: string | null;
}

export interface AttendanceBulkRequest {
  subjectId: number;
  academicYear: string;
  semester: number;
  section: string;
  date: string;
  period: number;
  entries: AttendanceMarkEntry[];
}

export interface AttendanceResponse {
  id: number;
  studentId: number;
  studentRegisterNumber: string | null;
  studentName: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  academicYear: string;
  semester: number;
  section: string;
  date: string;
  period: number;
  status: AttendanceStatus;
  remarks: string | null;
  markedByFacultyId: number | null;
  markedByFacultyName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AttendanceBulkResponse {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  academicYear: string;
  semester: number;
  section: string;
  date: string;
  period: number;
  createdCount: number;
  updatedCount: number;
  records: AttendanceResponse[];
}

export interface AttendanceUpdateRequest {
  status: AttendanceStatus;
  remarks?: string | null;
}

export interface AttendanceRosterEntry {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  attendanceId: number | null;
  status: AttendanceStatus | null;
  remarks: string | null;
}

export interface AttendanceRosterResponse {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  academicYear: string;
  semester: number;
  section: string;
  date: string;
  period: number;
  alreadyMarked: boolean;
  entries: AttendanceRosterEntry[];
}

export interface AttendanceSubjectSummary {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  credits: number;
  academicYear: string;
  semester: number;
  totalRecords: number;
  heldClasses: number;
  attendedClasses: number;
  cancelledClasses: number;
  attendancePercentage: number | null;
  lowAttendance: boolean;
}

export interface AttendanceSummaryResponse {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  academicYear: string | null;
  semester: number | null;
  totalRecords: number;
  heldClasses: number;
  attendedClasses: number;
  cancelledClasses: number;
  overallPercentage: number | null;
  minimumPercentage: number;
  lowAttendance: boolean;
  subjects: AttendanceSubjectSummary[];
}

export interface AttendanceClassSummaryEntry {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  totalRecords: number;
  heldClasses: number;
  attendedClasses: number;
  cancelledClasses: number;
  attendancePercentage: number | null;
  lowAttendance: boolean;
}

export interface AttendanceClassSummaryResponse {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  academicYear: string;
  semester: number;
  section: string;
  minimumPercentage: number;
  studentCount: number;
  lowAttendanceCount: number;
  entries: AttendanceClassSummaryEntry[];
}

// ---------------------------------------------------------------------------------
// Exams
// ---------------------------------------------------------------------------------

export interface ExamRequest {
  subjectId: number;
  title: string;
  examType: ExamType;
  academicYear: string;
  semester: number;
  section: string;
  examDate: string;
  maximumMarks: number;
}

export interface ExamUpdateRequest {
  title: string;
  examType: ExamType;
  examDate: string;
  maximumMarks: number;
  status: ExamStatus;
}

export interface ExamResponse {
  id: number;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  subjectCredits: number;
  facultyId: number | null;
  facultyName: string | null;
  title: string;
  examType: ExamType;
  academicYear: string;
  semester: number;
  section: string;
  examDate: string;
  maximumMarks: number;
  status: ExamStatus;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------------
// Marks
// ---------------------------------------------------------------------------------

export interface MarksEntry {
  studentId: number;
  marksObtained: number;
  remarks?: string | null;
}

export interface MarksBulkRequest {
  examId: number;
  entries: MarksEntry[];
}

export interface MarksUpdateRequest {
  marksObtained: number;
  remarks?: string | null;
}

export interface MarksResponse {
  id: number;
  examId: number;
  examTitle: string;
  examType: ExamType;
  examDate: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  subjectCredits: number;
  academicYear: string;
  semester: number;
  section: string;
  studentId: number;
  studentRegisterNumber: string | null;
  studentName: string;
  marksObtained: number;
  maximumMarks: number;
  percentage: number | null;
  grade: string | null;
  gradePoint: number | null;
  remarks: string | null;
  enteredByFacultyId: number | null;
  enteredByFacultyName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MarksBulkResponse {
  examId: number;
  examTitle: string;
  maximumMarks: number;
  createdCount: number;
  updatedCount: number;
  records: MarksResponse[];
}

export interface MarksEntrySheetEntry {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  marksId: number | null;
  marksObtained: number | null;
  remarks: string | null;
}

export interface MarksEntrySheetResponse {
  examId: number;
  examTitle: string;
  examType: ExamType;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  academicYear: string;
  semester: number;
  section: string;
  examDate: string;
  maximumMarks: number;
  studentCount: number;
  enteredCount: number;
  entries: MarksEntrySheetEntry[];
}

export interface SubjectGradeSummary {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  credits: number;
  academicYear: string;
  semester: number;
  examCount: number;
  totalObtained: number;
  totalMaximum: number;
  percentage: number | null;
  grade: string | null;
  gradePoint: number | null;
  passed: boolean | null;
}

export interface SemesterGradeSummary {
  academicYear: string;
  semester: number;
  subjectCount: number;
  gradedCredits: number;
  gpa: number | null;
  subjects: SubjectGradeSummary[];
}

export interface AcademicResultResponse {
  studentId: number;
  registerNumber: string | null;
  studentName: string;
  totalGradedCredits: number;
  cgpa: number | null;
  semesters: SemesterGradeSummary[];
}

// ---------------------------------------------------------------------------------
// Grade bands
// ---------------------------------------------------------------------------------

export interface GradeBandRequest {
  grade: string;
  minPercentage: number;
  maxPercentage: number;
  gradePoint: number;
  passGrade: boolean;
  description?: string | null;
}

export interface GradeBandResponse {
  id: number;
  grade: string;
  minPercentage: number;
  maxPercentage: number;
  gradePoint: number;
  passGrade: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}
