#!/usr/bin/env python3
"""
Verify that the test counts quoted in the documentation match a real run.

Why this exists
---------------
The same test counts are stated in four documents. They drift: the README
advertised "299+ green" long after the backend suite reached 379, and claimed
"75/75 green across 15 files" for a frontend suite that was already at 81
across 16. Both survived several rounds of editing because nothing checked
them. This does.

What it checks
--------------
Each claim below is a (file, pattern, fields) triple. The pattern's capture
groups are compared against the measured values:

    backend_tests   total tests in backend/target/surefire-reports/*.xml
    frontend_tests  numTotalTests from vitest's JSON reporter
    frontend_files  number of test files vitest actually ran

A claim whose pattern matches nothing is itself a failure, because it means
the prose was reworded and the check silently stopped covering it.

Deliberately NOT checked: the per-phase counts in PROJECT_PLAN.md. Those are
point-in-time records of what was true at that phase's checkpoint ("28/28
green" at Phase 3), not present-tense claims, and rewriting them would
falsify the history they exist to preserve.

Usage
-----
    python3 scripts/check-doc-figures.py                  # run both suites, verify
    python3 scripts/check-doc-figures.py --reuse-backend   # reuse existing surefire reports
    python3 scripts/check-doc-figures.py --update          # rewrite docs to the measured values

Exits 0 when every claim matches, 1 otherwise.
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# (relative path, regex, field name per capture group)
CLAIMS = [
    ("README.md", r"backend test suite is \*\*(\d+)/\d+ green\*\*", ["backend_tests"]),
    ("README.md", r"frontend suite is \*\*(\d+)/\d+ green across (\d+) files\*\*",
     ["frontend_tests", "frontend_files"]),
    ("README.md", r"\./mvnw test\s+#\s*(\d+) green", ["backend_tests"]),
    ("README.md", r"npm test\s+#\s*(\d+) green", ["frontend_tests"]),
    ("README.md", r"The suite is (\d+) green across (\d+) files", ["frontend_tests", "frontend_files"]),
    ("docs/how-it-works.md", r"(\d+) backend tests and (\d+) frontend tests",
     ["backend_tests", "frontend_tests"]),
    ("docs/demo-playbook.md", r"The test suites, (\d+) backend and (\d+) frontend",
     ["backend_tests", "frontend_tests"]),
]


def run(command: list[str], cwd: str) -> int:
    print(f"  $ {' '.join(command)}")
    return subprocess.run(command, cwd=cwd).returncode


def measure_backend(reuse: bool) -> int:
    backend = os.path.join(ROOT, "backend")
    reports = os.path.join(backend, "target", "surefire-reports", "*.xml")
    if not (reuse and glob.glob(reports)):
        mvnw = os.path.join(backend, "mvnw")
        if run([mvnw, "-B", "-q", "test"], backend) != 0:
            sys.exit("Backend suite failed. Fix the tests before checking the documentation.")
    files = glob.glob(reports)
    if not files:
        sys.exit("No surefire reports found — cannot determine the backend test count.")
    total = failed = 0
    for path in files:
        root = ET.parse(path).getroot()
        total += int(root.get("tests", 0))
        failed += int(root.get("failures", 0)) + int(root.get("errors", 0))
    if failed:
        sys.exit(f"Backend suite reports {failed} failure(s). Fix them before checking the documentation.")
    return total


def measure_frontend() -> tuple[int, int]:
    frontend = os.path.join(ROOT, "frontend")
    handle, report = tempfile.mkstemp(suffix=".json")
    os.close(handle)
    try:
        # The suite runs in a couple of seconds, so it is always run fresh.
        run(["npx", "vitest", "run", "--reporter=json", f"--outputFile={report}"], frontend)
        with open(report) as stream:
            data = json.load(stream)
    finally:
        os.unlink(report)
    if not data.get("success"):
        sys.exit("Frontend suite failed. Fix the tests before checking the documentation.")
    # numTotalTestSuites counts describe/suite blocks (38), not files — the
    # documented "N files" figure is the number of test files vitest ran.
    return int(data["numTotalTests"]), len(data.get("testResults") or [])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--reuse-backend", action="store_true",
                        help="Reuse existing surefire reports instead of re-running the backend suite.")
    parser.add_argument("--update", action="store_true",
                        help="Rewrite the documented figures to the measured values.")
    args = parser.parse_args()

    print("Measuring the backend suite…")
    backend_tests = measure_backend(args.reuse_backend)
    print("Measuring the frontend suite…")
    frontend_tests, frontend_files = measure_frontend()

    measured = {
        "backend_tests": backend_tests,
        "frontend_tests": frontend_tests,
        "frontend_files": frontend_files,
    }
    print(f"\nMeasured: backend {backend_tests} tests, "
          f"frontend {frontend_tests} tests across {frontend_files} files\n")

    problems: list[str] = []
    for relative, pattern, fields in CLAIMS:
        path = os.path.join(ROOT, relative)
        try:
            text = open(path, encoding="utf-8").read()
        except FileNotFoundError:
            problems.append(f"{relative}: file not found")
            continue

        match = re.search(pattern, text)
        if not match:
            problems.append(
                f"{relative}: no text matched /{pattern}/ — the wording changed, "
                "so this claim is no longer being checked. Update CLAIMS.")
            continue

        for index, field in enumerate(fields, start=1):
            stated, expected = int(match.group(index)), measured[field]
            if stated == expected:
                print(f"  ok   {relative}: {field} = {stated}")
                continue
            if args.update:
                start, end = match.span(index)
                text = text[:start] + str(expected) + text[end:]
                open(path, "w", encoding="utf-8").write(text)
                print(f"  set  {relative}: {field} {stated} -> {expected}")
                match = re.search(pattern, text)  # spans shift after a rewrite
            else:
                problems.append(
                    f"{relative}: says {field} is {stated}, measured {expected}")

    if problems:
        print("\nDocumented figures do not match the suites:\n")
        for problem in problems:
            print(f"  - {problem}")
        print("\nRe-run with --update to correct them, then review the diff.")
        return 1

    print("\nAll documented test figures match the suites.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
