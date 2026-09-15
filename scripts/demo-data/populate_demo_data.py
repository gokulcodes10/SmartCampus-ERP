#!/usr/bin/env python3
"""
Populate the development database with a coherent demo dataset.

Purpose
-------
A freshly registered account lands as PENDING with no course, enrolments,
attendance or marks, so every screen renders its (honest) empty state. This
script activates the named accounts into a real cohort and generates the
academic, placement, interview and coding history behind them, so the whole
application can be demonstrated end to end.

This complements `smartcampus.seed.DevDataSeeder`, which establishes the
baseline institution at boot. This script layers depth on top of it and can
target specific accounts; it does not replace it.

Safety
------
* **Idempotent.** Every insert is guarded (`INSERT IGNORE`, or `NOT EXISTS`
  where the table has no suitable unique key — `coding_submissions` has none).
  Re-running does not duplicate rows.
* **Deterministic.** The RNG is seeded, so a re-run reproduces the same figures.
* **Additive.** Nothing is deleted. Existing rows are left alone; the only
  UPDATE is the activation of the accounts listed in `TARGET_ACCOUNTS`.
* **Development only.** It writes directly to the database, bypassing service
  validation. Never point it at production.

Encoding
--------
The client charset is pinned to utf8mb4. Without it the MySQL client
interprets UTF-8 as latin1 and double-encodes non-ASCII text, which turns an
em dash into "â€”" in every generated title. `--repair-encoding` fixes rows
already damaged that way.

Usage
-----
    python3 scripts/demo-data/populate_demo_data.py
    python3 scripts/demo-data/populate_demo_data.py --repair-encoding
    MYSQL_CONTAINER=my-mysql python3 scripts/demo-data/populate_demo_data.py

Requires the dockerised MySQL from docker-compose to be running.
"""

import argparse
import datetime
import os
import random
import subprocess
import sys

RNG_SEED = 20260915

# Accounts to activate and populate. Email -> register number.
TARGET_ACCOUNTS = {
    "gokul.agenticai@gmail.com": "CSE2024101",
    "gokul@test.in": "CSE2024102",
}

ACADEMIC_YEAR, SEMESTER, SECTION = "2025-2026", 3, "A"
DEPARTMENT_CODE, COURSE_CODE = "CSE", "CSE-BTECH"
FACULTY_EMAIL = "faculty1@smartcampus.local"

# Subjects added to the semester-3 CSE catalogue so per-subject charts have
# real spread. (code, name, credits)
EXTRA_SUBJECTS = [
    ("CSE303", "Discrete Mathematics", 4),
    ("CSE304", "Computer Organisation and Architecture", 3),
    ("CSE305", "Design and Analysis of Algorithms", 4),
]

# A plausible weekly timetable: subject code -> (weekdays, period).
TIMETABLE = {
    "CSE301": ([0, 2, 4], 1),
    "CSE302": ([0, 3], 2),
    "CSE303": ([1, 4], 3),
    "CSE304": ([2, 3], 4),
    "CSE305": ([1, 4], 5),
}

TERM_START = datetime.date(2026, 4, 1)
TERM_END = datetime.date(2026, 9, 15)

# (exam_type, title, max_marks, date, status)
EXAM_PLAN = [
    ("INTERNAL_1", "Internal Assessment 1", 50, "2026-05-20", "COMPLETED"),
    ("INTERNAL_2", "Internal Assessment 2", 50, "2026-07-15", "COMPLETED"),
    ("QUIZ", "Unit Quiz", 20, "2026-08-05", "COMPLETED"),
    ("MODEL", "Model Examination", 100, "2026-08-25", "COMPLETED"),
    ("SEMESTER", "Semester Examination", 100, "2026-10-12", "SCHEDULED"),
]

# Ability profiles, so the performance bands actually populate rather than every
# student landing in one. email -> (present_rate, marks_mean, marks_sd, coding_skill)
PROFILES = {
    "student1@smartcampus.local": (0.95, 88, 5, 0.80),
    "student2@smartcampus.local": (0.86, 74, 7, 0.55),
    "student3@smartcampus.local": (0.76, 57, 9, 0.38),
    "gokul.agenticai@gmail.com": (0.93, 85, 6, 0.72),
    "gokul@test.in": (0.90, 78, 7, 0.60),
}

DOUBLE_ENCODED_MARKER = "C3A2E282AC"  # UTF-8 bytes of "â€" — the tell-tale of double encoding


def container() -> str:
    name = os.environ.get("MYSQL_CONTAINER")
    if name:
        return name
    found = subprocess.run(
        ["docker", "ps", "-qf", "name=mysql"], capture_output=True, text=True
    ).stdout.split()
    if not found:
        sys.exit("No running MySQL container found. Start docker-compose, or set MYSQL_CONTAINER.")
    return found[0]


def sql(query: str) -> list[list[str]]:
    """Run a query and return tab-split rows (including the header row)."""
    result = subprocess.run(
        [
            "docker", "exec", "-i", container(),
            "mysql", "-usmartcampus", "-psmartcampus",
            "--default-character-set=utf8mb4",  # never drop this: see Encoding above
            "smartcampus", "-B",
        ],
        input=query, capture_output=True, text=True,
    )
    errors = "\n".join(l for l in result.stderr.splitlines() if "Warning" not in l)
    if errors:
        sys.exit(f"SQL error:\n{errors}")
    return [line.split("\t") for line in result.stdout.strip().splitlines()]


def scalar(query: str) -> str | None:
    rows = sql(query)
    return rows[1][0] if len(rows) > 1 and rows[1][0] != "NULL" else None


def quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def repair_encoding() -> None:
    """Undo double-encoded UTF-8 in text this script may previously have written."""
    repair = "CONVERT(CAST(CONVERT({col} USING latin1) AS BINARY) USING utf8mb4)"
    targets = [
        ("exams", "title"),
        ("interviews", "title"),
        ("interviews", "feedback"),
        ("resumes", "title"),
        ("resumes", "summary"),
        ("notifications", "title"),
        ("notifications", "message"),
        ("placement_applications", "cover_note"),
        ("placement_applications", "decision_note"),
        ("resume_projects", "description"),
    ]
    total = 0
    for table, col in targets:
        guard = f"HEX(COALESCE({col},'')) LIKE '%{DOUBLE_ENCODED_MARKER}%'"
        before = int(scalar(f"SELECT COUNT(*) FROM {table} WHERE {guard};") or 0)
        if before:
            sql(f"UPDATE {table} SET {col} = {repair.format(col=col)} WHERE {guard};")
            total += before
    print(f"  repaired {total} double-encoded column value(s)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repair-encoding", action="store_true",
                        help="Only repair double-encoded text, then exit.")
    args = parser.parse_args()

    if args.repair_encoding:
        print("Repairing text encoding…")
        repair_encoding()
        return

    random.seed(RNG_SEED)

    dept_id = scalar(f"SELECT id FROM departments WHERE code={quote(DEPARTMENT_CODE)};")
    course_id = scalar(f"SELECT id FROM courses WHERE code={quote(COURSE_CODE)};")
    faculty_id = scalar(
        f"SELECT f.id FROM faculty f JOIN users u ON u.id=f.user_id WHERE u.email={quote(FACULTY_EMAIL)};")
    admin_id = scalar("SELECT id FROM users WHERE email='admin@smartcampus.local';")
    if not all([dept_id, course_id, faculty_id]):
        sys.exit("Baseline institution missing. Run the backend once with the seed profile first.")

    # ---- 1. extra subjects + faculty assignments -----------------------
    for code, name, credits in EXTRA_SUBJECTS:
        sql(f"""INSERT INTO subjects (code,name,course_id,semester,credits,created_at,updated_at)
                SELECT * FROM (SELECT {quote(code)} a,{quote(name)} b,{course_id} c,{SEMESTER} d,{credits} e,NOW() f,NOW() g) t
                WHERE NOT EXISTS (SELECT 1 FROM subjects WHERE code={quote(code)});""")
    sql(f"""INSERT INTO faculty_subject_assignments (subject_id,faculty_id,academic_year,semester,section,created_at,updated_at)
            SELECT s.id,{faculty_id},{quote(ACADEMIC_YEAR)},{SEMESTER},{quote(SECTION)},NOW(),NOW()
            FROM subjects s WHERE s.course_id={course_id} AND s.semester={SEMESTER}
              AND NOT EXISTS (SELECT 1 FROM faculty_subject_assignments a WHERE a.subject_id=s.id
                AND a.faculty_id={faculty_id} AND a.academic_year={quote(ACADEMIC_YEAR)}
                AND a.semester={SEMESTER} AND a.section={quote(SECTION)});""")
    print(f"subjects in {COURSE_CODE} semester {SEMESTER}: "
          f"{scalar(f'SELECT COUNT(*) FROM subjects WHERE course_id={course_id} AND semester={SEMESTER};')}")

    # ---- 2. activate the target accounts -------------------------------
    for email, register in TARGET_ACCOUNTS.items():
        if not scalar(f"SELECT id FROM users WHERE email={quote(email)};"):
            print(f"  skipping {email} — no such account")
            continue
        sql(f"""UPDATE students s JOIN users u ON u.id=s.user_id
                SET s.status='ACTIVE', s.department_id={dept_id}, s.course_id={course_id},
                    s.current_semester={SEMESTER}, s.section={quote(SECTION)},
                    s.admission_year=2024, s.register_number={quote(register)}, s.updated_at=NOW()
                WHERE u.email={quote(email)};""")
        print(f"  activated {email} as {register}")

    # ---- 3. enrol the cohort -------------------------------------------
    sql(f"""INSERT IGNORE INTO enrollments (student_id,subject_id,academic_year,semester,section,status,created_at,updated_at)
            SELECT st.id,sb.id,{quote(ACADEMIC_YEAR)},{SEMESTER},{quote(SECTION)},'ACTIVE',NOW(),NOW()
            FROM students st CROSS JOIN subjects sb
            WHERE st.course_id={course_id} AND st.status='ACTIVE'
              AND sb.course_id={course_id} AND sb.semester={SEMESTER};""")

    subjects = {r[1]: int(r[0]) for r in sql(
        f"SELECT id,code FROM subjects WHERE course_id={course_id} AND semester={SEMESTER};")[1:]}
    students = {r[1]: int(r[0]) for r in sql(
        "SELECT s.id,u.email FROM students s JOIN users u ON u.id=s.user_id "
        f"WHERE s.course_id={course_id} AND s.status='ACTIVE';")[1:]}
    profiled = {e: p for e, p in PROFILES.items() if e in students}
    print(f"cohort: {len(students)} active students, {len(subjects)} subjects")

    # ---- 4. exams -------------------------------------------------------
    for code, subject_id in subjects.items():
        for etype, title, maximum, date, status in EXAM_PLAN:
            label = quote(f"{code} — {title}")
            sql(f"""INSERT INTO exams (subject_id,faculty_id,title,exam_type,academic_year,semester,section,
                                       exam_date,maximum_marks,status,created_at,updated_at)
                    SELECT * FROM (SELECT {subject_id} a,{faculty_id} b,{label} c,{quote(etype)} d,
                      {quote(ACADEMIC_YEAR)} e,{SEMESTER} f,{quote(SECTION)} g,{quote(date)} h,
                      {maximum} i,{quote(status)} j,NOW() k,NOW() l) t
                    WHERE NOT EXISTS (SELECT 1 FROM exams WHERE subject_id={subject_id}
                      AND exam_type={quote(etype)} AND academic_year={quote(ACADEMIC_YEAR)}
                      AND semester={SEMESTER} AND section={quote(SECTION)});""")

    exams = [(int(r[0]), int(r[1]), float(r[2]), r[3]) for r in sql(
        f"""SELECT id,subject_id,maximum_marks,status FROM exams
            WHERE academic_year={quote(ACADEMIC_YEAR)} AND semester={SEMESTER}
              AND section={quote(SECTION)} AND subject_id IN ({','.join(map(str, subjects.values()))});""")[1:]]

    # ---- 5. marks (completed exams only) --------------------------------
    values = []
    for exam_id, subject_id, maximum, status in exams:
        if status != "COMPLETED":
            continue
        for email, (_rate, mean, sd, _skill) in profiled.items():
            # A per-student-per-subject offset, so nobody is uniformly strong.
            offset = random.Random(f"{email}-{subject_id}").uniform(-9, 9)
            pct = max(32.0, min(99.0, random.gauss(mean + offset, sd)))
            values.append(f"({exam_id},{students[email]},{round(pct / 100 * maximum, 2)},{faculty_id},NOW(),NOW())")
    if values:
        sql("INSERT IGNORE INTO marks (exam_id,student_id,marks_obtained,entered_by_faculty_id,created_at,updated_at) "
            "VALUES " + ",".join(values) + ";")
    print(f"marks: {scalar('SELECT COUNT(*) FROM marks;')} rows")

    # ---- 6. attendance --------------------------------------------------
    rows, day = [], TERM_START
    while day <= TERM_END:
        for code, subject_id in subjects.items():
            weekdays, period = TIMETABLE.get(code, ([0, 2], 1))
            if day.weekday() not in weekdays:
                continue
            # ~3% of sessions are cancelled for the whole class (never held).
            cancelled = random.Random(f"cancel-{subject_id}-{day}").random() < 0.03
            for email, (rate, _m, _s, _k) in profiled.items():
                if cancelled:
                    status = "CANCELLED"
                elif random.random() < rate:
                    status = "PRESENT"
                else:
                    roll = random.random()
                    status = "ABSENT" if roll < 0.70 else ("LATE" if roll < 0.90 else "ON_DUTY")
                rows.append(f"({students[email]},{subject_id},{quote(ACADEMIC_YEAR)},{SEMESTER},"
                            f"{quote(SECTION)},'{day}',{period},'{status}',{faculty_id},NOW(),NOW())")
        day += datetime.timedelta(days=1)
    for i in range(0, len(rows), 500):
        sql("INSERT IGNORE INTO attendance (student_id,subject_id,academic_year,semester,section,"
            "attendance_date,period,status,marked_by_faculty_id,created_at,updated_at) VALUES "
            + ",".join(rows[i:i + 500]) + ";")
    print(f"attendance: {scalar('SELECT COUNT(*) FROM attendance;')} rows")

    # ---- 7. coding submissions -----------------------------------------
    # coding_submissions has no unique key, so guard on "this student has no
    # submission for this problem yet" rather than relying on INSERT IGNORE.
    problems = [int(r[0]) for r in sql("SELECT id FROM coding_problems ORDER BY id;")[1:]]
    sources = {
        "JAVA": "public class Main { public static void main(String[] a){ /* solution */ } }",
        "CPP": "#include <bits/stdc++.h>\nint main(){ /* solution */ }",
    }
    failures = ["WRONG_ANSWER", "TIME_LIMIT_EXCEEDED", "RUNTIME_ERROR", "COMPILATION_ERROR"]
    added = 0
    for email, (_r, _m, _s, skill) in profiled.items():
        student_id = students[email]
        existing = {int(r[0]) for r in sql(
            f"SELECT DISTINCT problem_id FROM coding_submissions WHERE student_id={student_id};")[1:]}
        batch = []
        for problem_id in problems:
            if problem_id in existing:
                continue
            for _ in range(random.randint(1, 3)):
                language = random.choice(["JAVA", "CPP"])
                if random.random() < skill:
                    status, passed, failed_at = "ACCEPTED", 5, "NULL"
                    error = "NULL"
                else:
                    status = random.choice(failures)
                    passed = 0 if status == "COMPILATION_ERROR" else random.randint(0, 4)
                    failed_at = "NULL" if status == "COMPILATION_ERROR" else str(passed + 1)
                    error = "NULL" if status == "COMPILATION_ERROR" else quote(
                        f"Execution failed on test case {passed + 1}")
                score = 100 if status == "ACCEPTED" else int(passed / 5 * 100)
                when = datetime.datetime(2026, 7, 1) + datetime.timedelta(
                    days=random.randint(0, 70), hours=random.randint(8, 21), minutes=random.randint(0, 59))
                batch.append(f"({problem_id},{student_id},NULL,'{language}',{quote(sources[language])},"
                             f"'{status}',{passed},5,{score},100,{random.randint(12,480)},"
                             f"{random.randint(9000,48000)},{failed_at},NULL,{error},'{when}','{when}','{when}')")
        if batch:
            sql("INSERT INTO coding_submissions (problem_id,student_id,contest_id,language,source_code,status,"
                "passed_test_cases,total_test_cases,score,max_score,execution_time_ms,memory_kb,"
                "failed_test_case_ordinal,compile_output,error_message,judged_at,created_at,updated_at) "
                "VALUES " + ",".join(batch) + ";")
            added += len(batch)
    print(f"coding submissions: {scalar('SELECT COUNT(*) FROM coding_submissions;')} rows ({added} new)")

    contests = [int(r[0]) for r in sql("SELECT id FROM coding_contests WHERE status='PUBLISHED';")[1:]]
    for contest_id in contests:  # unique key on (contest_id, student_id) makes IGNORE sufficient
        values = []
        for email in profiled:
            solved = random.randint(0, 2)
            values.append(f"({contest_id},{students[email]},'2026-08-01 09:00:00',{solved*100},"
                          f"{solved},{random.randint(0,900)},NULL,NOW(),NOW())")
        if values:
            sql("INSERT IGNORE INTO contest_participants (contest_id,student_id,registered_at,total_score,"
                "problems_solved,penalty_seconds,last_accepted_at,created_at,updated_at) VALUES "
                + ",".join(values) + ";")

    # ---- 8. placements, interviews, resumes, notifications -------------
    populate_placement_and_profile(students, admin_id)

    print("\nDone. Summary per populated account:")
    for row in sql(f"""SELECT u.email,
        ROUND(100*SUM(a.status IN ('PRESENT','LATE','ON_DUTY'))/NULLIF(SUM(a.status<>'CANCELLED'),0),2) attendance,
        (SELECT COUNT(*) FROM placement_applications p WHERE p.student_id=s.id) applications,
        (SELECT COUNT(*) FROM interviews i WHERE i.student_id=s.id) interviews,
        (SELECT COUNT(*) FROM coding_submissions c WHERE c.student_id=s.id) submissions,
        (SELECT COUNT(*) FROM notifications n WHERE n.user_id=u.id AND n.read_at IS NULL) unread
        FROM students s JOIN users u ON u.id=s.user_id LEFT JOIN attendance a ON a.student_id=s.id
        WHERE u.email IN ({','.join(quote(e) for e in TARGET_ACCOUNTS)})
        GROUP BY u.email,s.id;"""):
        print("  " + " | ".join(row))


def populate_placement_and_profile(students: dict[str, int], admin_id: str | None) -> None:
    """Applications, interviews, a resume and notifications for the target accounts."""
    emails = [e for e in TARGET_ACCOUNTS if e in students]
    if not emails:
        return
    admin = admin_id or "NULL"

    jobs = {r[1]: int(r[0]) for r in sql("SELECT id,title FROM jobs WHERE status='OPEN';")[1:]}
    statuses = ["APPLIED", "UNDER_REVIEW", "SHORTLISTED", "INTERVIEW_SCHEDULED", "SELECTED", "REJECTED"]
    for index, email in enumerate(emails):
        student_id = students[email]
        for offset, (title, job_id) in enumerate(jobs.items()):
            if (index + offset) % 2 and offset > 2:
                continue  # not every student applies to everything
            status = statuses[(index + offset) % len(statuses)]
            decided = status not in ("APPLIED",)
            sql(f"""INSERT INTO placement_applications (job_id,student_id,status,cover_note,
                      cgpa_at_application,percentage_at_application,applied_at,status_changed_at,
                      status_changed_by,created_at,updated_at)
                    SELECT * FROM (SELECT {job_id} a,{student_id} b,{quote(status)} c,
                      {quote('Interested in the ' + title + ' opening.')} d,8.20 e,82.00 f,
                      '2026-08-{(offset % 27) + 1:02d} 10:00:00' g,
                      {"'2026-09-02 12:00:00'" if decided else "NULL"} h,
                      {admin if decided else "NULL"} i,NOW() j,NOW() k) t
                    WHERE NOT EXISTS (SELECT 1 FROM placement_applications p
                      WHERE p.job_id={job_id} AND p.student_id={student_id});""")

        # Interviews: one ahead, one already sat.
        for title, itype, start, end, status, outcome, feedback in [
            (f"TechNova Solutions — Technical Round", "TECHNICAL",
             f"2026-09-{18 + index} 10:30:00", f"2026-09-{18 + index} 11:30:00", "SCHEDULED", "NULL", "NULL"),
            ("Aptitude Screen", "APTITUDE",
             "2026-09-02 09:00:00", "2026-09-02 10:00:00", "COMPLETED", quote("SELECTED"),
             quote("Cleared comfortably; strong on data structures.")),
        ]:
            sql(f"""INSERT INTO interviews (student_id,title,interview_type,company_name,round_name,mode,
                      meeting_link,interviewer_name,scheduled_start,scheduled_end,status,outcome,feedback,
                      created_by,created_at,updated_at)
                    SELECT * FROM (SELECT {student_id} a,{quote(title)} b,{quote(itype)} c,
                      'TechNova Solutions' d,'Round 1' e,'ONLINE' f,'https://meet.example.com/demo' g,
                      'Kavya Raman' h,'{start}' i,'{end}' j,{quote(status)} k,{outcome} l,{feedback} m,
                      {admin} n,NOW() o,NOW() p) t
                    WHERE NOT EXISTS (SELECT 1 FROM interviews i WHERE i.student_id={student_id}
                      AND i.title={quote(title)} AND i.scheduled_start='{start}');""")

        # A resume with skills, education and a project.
        resume_title = "Software Engineer — 2026"
        sql(f"""INSERT INTO resumes (student_id,title,template,full_name,email,phone,location,
                  github_url,summary,created_at,updated_at)
                SELECT * FROM (SELECT {student_id} a,{quote(resume_title)} b,'MODERN' c,'Gokul' d,
                  {quote(email)} e,'+91 90000 12345' f,'Chennai, India' g,
                  'https://github.com/gokulcodes10' h,
                  {quote('Computer Science undergraduate, comfortable across Java, SQL and React.')} i,
                  NOW() j,NOW() k) t
                WHERE NOT EXISTS (SELECT 1 FROM resumes r WHERE r.student_id={student_id}
                  AND r.title={quote(resume_title)});""")
        resume_id = scalar(f"SELECT id FROM resumes WHERE student_id={student_id} ORDER BY id LIMIT 1;")
        if resume_id:
            for order, (name, category, proficiency) in enumerate([
                ("Java", "TECHNICAL", "ADVANCED"), ("SQL", "TECHNICAL", "ADVANCED"),
                ("React", "TECHNICAL", "INTERMEDIATE"), ("Problem solving", "SOFT", "ADVANCED"),
            ], start=1):
                sql(f"""INSERT IGNORE INTO resume_skills (resume_id,name,category,proficiency,
                          display_order,created_at,updated_at)
                        VALUES ({resume_id},{quote(name)},{quote(category)},{quote(proficiency)},
                          {order},NOW(),NOW());""")
            sql(f"""INSERT INTO resume_educations (resume_id,institution,degree,field_of_study,
                      start_year,end_year,grade_value,grade_scale,display_order,created_at,updated_at)
                    SELECT * FROM (SELECT {resume_id} a,'SmartCampus Institute of Technology' b,'B.Tech' c,
                      'Computer Science and Engineering' d,2024 e,2028 f,8.20 g,'CGPA' h,1 i,NOW() j,NOW() k) t
                    WHERE NOT EXISTS (SELECT 1 FROM resume_educations WHERE resume_id={resume_id});""")
            sql(f"""INSERT INTO resume_projects (resume_id,name,description,tech_stack,
                      start_date,end_date,display_order,created_at,updated_at)
                    SELECT * FROM (SELECT {resume_id} a,'Campus Attendance Analyser' b,
                      {quote('Aggregates attendance per subject and flags students below the threshold.')} c,
                      'Java, Spring Boot, MySQL' d,'2026-02-01' e,'2026-05-20' f,1 g,NOW() h,NOW() i) t
                    WHERE NOT EXISTS (SELECT 1 FROM resume_projects WHERE resume_id={resume_id});""")

        # Notifications. reference_type is a constrained set, and an
        # ANNOUNCEMENT-typed row must also carry announcement_id.
        user_id = scalar(f"SELECT id FROM users WHERE email={quote(email)};")
        interview_ref = scalar(
            f"SELECT id FROM interviews WHERE student_id={student_id} AND status='SCHEDULED' "
            "ORDER BY scheduled_start LIMIT 1;")
        announcement_ref = scalar("SELECT id FROM announcements ORDER BY id DESC LIMIT 1;")
        if user_id and interview_ref:
            sql(f"""INSERT INTO notifications (user_id,type,title,message,priority,link,reference_type,
                      reference_id,announcement_id,dedupe_key,read_at,created_at,updated_at)
                    SELECT * FROM (SELECT {user_id} a,'INTERVIEW_UPDATE' b,'Interview scheduled' c,
                      {quote('TechNova Solutions — Technical Round is confirmed.')} d,'HIGH' e,
                      '/student/interviews' f,'INTERVIEW' g,{interview_ref} h,NULL i,
                      {quote('demo-iv-' + str(student_id))} j,NULL k,'2026-09-12 09:15:00' l,NOW() m) t
                    WHERE NOT EXISTS (SELECT 1 FROM notifications n
                      WHERE n.dedupe_key={quote('demo-iv-' + str(student_id))});""")
        if user_id and announcement_ref:
            sql(f"""INSERT INTO notifications (user_id,type,title,message,priority,link,reference_type,
                      reference_id,announcement_id,dedupe_key,read_at,created_at,updated_at)
                    SELECT * FROM (SELECT {user_id} a,'ANNOUNCEMENT' b,'New announcement' c,
                      'A new announcement has been published.' d,'NORMAL' e,'/announcements' f,
                      'ANNOUNCEMENT' g,{announcement_ref} h,{announcement_ref} i,
                      {quote('demo-ann-' + str(student_id))} j,NULL k,'2026-09-10 14:00:00' l,NOW() m) t
                    WHERE NOT EXISTS (SELECT 1 FROM notifications n
                      WHERE n.dedupe_key={quote('demo-ann-' + str(student_id))});""")


if __name__ == "__main__":
    main()
