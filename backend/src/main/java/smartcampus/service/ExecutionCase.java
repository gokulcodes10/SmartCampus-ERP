package smartcampus.service;

/**
 * One input/expected-output pair to run through {@link CodeExecutionService}.
 *
 * @param stdin          fed to the program's standard input; never null in practice
 *                       (a test case's input may be the empty string, never null)
 * @param expectedOutput when non-null, Judge0 itself compares actual output to this and
 *                       reports status 3 (Accepted) vs 4 (Wrong Answer); when null (the
 *                       playground "Run" path, which has nothing to judge against) Judge0
 *                       reports status 3 for any successful exit regardless of output
 */
public record ExecutionCase(String stdin, String expectedOutput) {}
