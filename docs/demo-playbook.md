# Demo Playbook — Populating Data and Presenting SmartCampus ERP

For the author, before an evaluation. It covers what the system gives you for free, what you must create yourself, and the order to walk an evaluator through so the interesting parts land.

Read [`how-it-works.md`](how-it-works.md) first if you want the architecture behind any of this.

---

## 1. Why this document exists

The seeder does not populate everything, and the modules it leaves empty are some of the most impressive ones. If you demo straight from a fresh seed, an evaluator opens the resume builder, the placement applications list, the interview question bank and the study plans and finds all four empty. The features are built and working. They simply have no rows yet.

**Fill them before the demo, not during it.** Creating them live is a fine way to prove the system is real, but doing it for every module burns the whole session and leaves you retyping form fields while someone watches.

---

## 2. Starting position after a seed

Boot once with the seed switches and you get a realistic institution:

```bash
cd backend
SPRING_PROFILES_ACTIVE=seed SMARTCAMPUS_SEED_ENABLED=true ./mvnw spring-boot:run
```

Both switches are required. Either alone does nothing, and the seeder refuses outright if a production profile is active. That double opt-in is deliberate, since seed data is realistic fake data that must never become something a real deployment depends on.

What arrives:

| Module | Rows | Demo-ready? |
|---|---|---|
| Departments, courses, subjects | 5, 5, 17 | Yes |
| Faculty, students | 5, 26 | Yes |
| Enrolments, faculty assignments | Populated | Yes |
| Exams, marks | 24, 72 | Yes |
| Attendance | 481 | Yes |
| Grade bands, performance bands | 7, 4 | Yes |
| Companies, jobs | 5, 7 | Yes |
| Coding problems, contests | 8, 2 | Yes |
| Announcements, notifications | 7, 140 | Yes |
| **Placement applications** | **0** | **No — create these** |
| **Coding submissions** | **0** | **No — create these** |
| **Interview question bank** | **0** | **No — create these** |
| **AI study plans** | **0** | **No — create these** |
| **Resumes** | thin | **Build at least one properly** |
| **Interviews** | thin | **Add a few** |
| **AI conversations** | thin | **Have one real conversation ready** |

Accounts, all documented in the README: `admin@smartcampus.local` / `Admin@Dev12345`, `faculty1` through `faculty4` / `Faculty@Dev12345`, `student1` through `student12` / `Student@Dev12345`, all at `@smartcampus.local`.

---

## 3. Populating the empty modules

Do all of this through the running application at `http://localhost:5175`. Populating through the UI has a second benefit: it proves every screen you are about to demo actually works, so you find a broken form the night before rather than in front of an evaluator.

### 3.1 Interview question bank — highest value, currently empty

Sign in as **admin**, go to `/admin/interview-questions`.

Create at least a dozen spread across the categories, because the category filter is one of the things an evaluator will click. Cover technical, HR, behavioural, coding, aptitude and company-specific, and vary the difficulty so the difficulty filter has something to do. Give every question a real answer and explanation. Empty answers make the whole bank look like a stub.

Then sign in as **student1** and go to `/student/interview-prep`. Bookmark two, mark three complete, and leave the rest untouched, so the progress tracking has a partial state to show rather than zero or a hundred percent.

Also generate two or three questions with AI from that page. Leaving genuinely AI-generated questions in the bank alongside hand-written ones is worth doing, because it lets you show the two side by side.

### 3.2 Resumes — build one properly, end to end

As **student1**, go to `/student/resumes`, then create one at `/student/resumes/new`.

Fill every section: education, projects, experience, certifications, skills and achievements. A resume with only a name proves nothing; a complete one demonstrates the repeatable-section editor, and it is what makes the PDF worth showing. Prefill pulls what it can from the student profile, so you are editing rather than typing from scratch.

Then do three things:

1. Save a second version with a different template. There are three genuinely different layouts, and switching between them is a strong visual moment.
2. Download the PDF and open it. Confirm it looks right before the demo, not during.
3. Keep one resume selected and ready to attach to a job application in the next step.

### 3.3 Placement applications — currently zero

As **student1**, go to `/student/jobs`. Open a drive at `/student/jobs/:id` and apply, attaching the resume from the previous step.

Now the important half. **Find a drive this student is not eligible for and open it.** The refusal with a specific reason is the single best thing in the placement module, and it needs a real ineligible pairing to demonstrate. If every seeded student happens to be eligible for everything, sign in as admin, edit a drive's minimum CGPA upward at `/admin/jobs`, and you will have one.

Apply as two or three different students to two or three different drives, so the admin applicant list at `/admin/jobs/:id/applicants` has enough rows to shortlist within. Move at least one applicant through a status transition, so the placement analytics at `/admin/placement/analytics` shows movement rather than a flat zero.

### 3.4 Coding submissions — currently zero

As a student, go to `/coding`, open a problem, write a solution in the editor and submit.

Be clear-eyed about what this shows here. Code execution is unavailable on this machine, so the submission records with an honest failure explaining that the execution service could not be reached. **This is worth demonstrating rather than hiding.** It shows the submission pipeline, the history at `/coding/submissions` and the refusal to fabricate a verdict. Say plainly that execution needs a hosted endpoint, and that the module was built against the real execution API.

Register for a contest at `/coding/contests` so the participant list and both leaderboards have a real entry.

### 3.5 AI conversations and study plans

As **student1**, go to `/student/ai` and have one genuine conversation. Ask something that forces the assistant to use the student's record, such as which subjects to prioritise and why. Rename it, so the rename and history features have visible state.

Then go to `/student/study-plans` and generate one, then edit it. The plan being student-editable rather than dictated is a deliberate design point worth showing.

**Mind the rate limit.** Five requests per minute and a hundred per day, enforced server-side. Do not burn your allowance rehearsing right before the demo, and do not plan to generate five things live in quick succession.

### 3.6 Interviews

As **student1** at `/student/interviews`, schedule two or three across different dates. Then attempt to schedule one that overlaps an existing slot. It is refused, and that refusal is the Phase 10 checkpoint. Have the overlapping attempt ready to perform live, because it is quick and it demonstrates a real invariant.

Admin can view and manage the same at `/admin/interviews`.

### 3.7 One announcement, live

Leave this one for the demo itself. See section 5.

---

## 4. What to emphasise — the genuinely novel parts

Nearly every student ERP has departments, courses and attendance. These are the parts that are not ordinary, ranked by how well they land:

**1. The AI assistant is grounded in the student's real record.** It is not a chatbot bolted on. Before answering, the system assembles the student's actual marks, attendance, weak subjects and upcoming exams, reusing the very same services that render the marks and attendance pages, so the assistant's numbers cannot drift from what the student sees on screen. Demonstrate it by opening the marks page, noting a specific figure, then asking the assistant a question whose answer must contain that figure.

**2. Faculty permissions are assignment-scoped, not role-scoped.** Most projects check "is this user a teacher." This one checks whether this teacher teaches this exact subject, in this section, this semester, this academic year. The entity that makes it possible did not exist in the original specification and had to be designed. Demonstrate it by having a faculty member attempt to act on a class they do not teach.

**3. Eligibility that explains itself.** An ineligible student is told which rule they fail, not merely blocked.

**4. Configuration instead of hard-coding.** Grade bands, grade points, performance thresholds and the attendance minimum are administrator-editable rows. Editing a grade band at `/admin/grade-bands` changes grades that were already computed. That is a strong live demonstration and takes about twenty seconds.

**5. Honest failure everywhere.** No fabricated verdicts, no defaulted classifications, no placeholder dashboard numbers. A student with no marks yet shows an empty value rather than a zero, because zero is a claim and empty is the truth.

**6. Real-time that genuinely arrives.** An announcement lands in an already-open notification centre with no refresh.

**7. Security enforced at the server.** Cross-student reads return `404` rather than `403`, so an id cannot be probed for existence.

---

## 5. A demonstration running order

Roughly thirty minutes, arranged so each step sets up the next.

**Open with the student.** Sign in as `student1`. Dashboard, attendance, marks, analytics. Point out one specific number and say it is a database aggregation, then offer to prove it later. This establishes the ordinary competence of the system quickly.

**Then the AI assistant, while that number is still on screen.** Ask a question whose answer must contain it. This is your strongest moment and it works best early, while the evaluator still suspects the numbers might be decorative.

**Resume and placement, as one story.** Show the completed resume, download the PDF, apply to a drive with it attached, then open the drive the student is not eligible for and read the refusal aloud. One narrative, four modules.

**Coding, honestly.** Playground, a problem, a submission, the honest execution failure, the contest and leaderboard. Explain the constraint in one sentence and move on. Do not apologise for it at length; it reads worse than the limitation itself.

**Switch to faculty.** Show that faculty1 sees only their own classes. Mark a roster, enter marks, then attempt something outside their assignment and let it be refused. Finish at `/faculty/announcements` and point out what is missing from the form: no audience selector and no department picker, because a faculty member's only legal target is their own department. A control the server would refuse is a control worth not building.

**Switch to admin, and finish live.** Edit a grade band and show a grade change. Then, with a student session still open in another window, post an announcement and let it appear in that student's notification centre without a refresh. Ending on the live push is the right closing beat.

**Keep in reserve, for a sceptical evaluator.** A terminal with MySQL open to recompute a dashboard number from raw rows. The test suites, 379 backend and 75 frontend. Swagger at `http://localhost:8080/swagger-ui.html` for authenticated live API calls. An altered JWT returning `401`. A student token on an admin route returning `403`.

---

## 6. Before you start, a checklist

- [ ] MySQL and Mailpit containers up and healthy
- [ ] Backend responding on 8080, frontend on 5175
- [ ] Every module in section 3 populated
- [ ] The resume PDF opened and checked
- [ ] An ineligible student and drive pairing confirmed to exist
- [ ] AI rate limit not already spent on rehearsal
- [ ] Two browser windows ready, one student and one admin, for the live announcement
- [ ] You can state the Judge0 limitation in one sentence without hedging

---

## 7. Do not do these

**Do demo faculty announcements, but from the faculty screen, not the admin one.** Faculty compose at `/faculty/announcements`, which shows only their own announcements and targets only their own department. Publishing there while a student of that department has a session open is the same live-notification moment as the admin one, from the other side of the permission boundary.

**Do not claim code execution works.** It does not, on this machine, for a specific and defensible reason. Say so plainly.

**Do not claim the responsive pass is verified.** The responsive code is there and you can resize the window to show it behaving. Nobody has formally verified it at every breakpoint with a real browser, and the project's own documentation says so. Resize the window, show it works, and do not upgrade that into a verification claim.

**Do not reseed on the morning of the demo.** Everything in section 3 is data you created by hand and the seeder will not recreate it.

**Do not delete data mid-demo to show a delete works.** If you must, delete something you created for that purpose, not a row another part of the walkthrough depends on.
