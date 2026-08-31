package smartcampus.service;

import java.util.List;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.exception.CodeExecutionUnavailableException;

/**
 * The §28/§70 abstraction over an external code-execution backend. The application
 * server never executes student-submitted code itself; every implementation of this
 * interface delegates to a sandboxed judge (currently Judge0, see
 * {@link Judge0Service}) reachable at a configured URL.
 */
public interface CodeExecutionService {

    /**
     * Runs {@code sourceCode} against every case in {@code cases}, in order.
     *
     * <p>CONTRACT: the returned list has EXACTLY the same size as {@code cases} and its
     * elements are in the same order (result {@code i} answers {@code cases.get(i)}).
     * There is no partial result: if a real, judge-produced verdict cannot be obtained
     * for every case, this method throws {@link CodeExecutionUnavailableException} and
     * returns nothing. Implementations must never pad, invent, or default an entry.
     *
     * @param language        the language the source is written in
     * @param sourceCode      the program source
     * @param cases           the stdin/expected-output pairs to run, in the order results
     *                        must come back in
     * @param cpuTimeLimitMs  CPU time limit for each case, in milliseconds
     * @param memoryLimitKb   memory limit for each case, in kilobytes
     * @return one {@link ExecutionResult} per case, same size and order as {@code cases}
     * @throws CodeExecutionUnavailableException when a real verdict could not be
     *         obtained for every case (backend unreachable, non-2xx response,
     *         unparseable body, token/case count mismatch, or poll timeout)
     */
    List<ExecutionResult> executeBatch(
            ProgrammingLanguage language,
            String sourceCode,
            List<ExecutionCase> cases,
            int cpuTimeLimitMs,
            int memoryLimitKb);

    /**
     * Convenience for a single free-form run (the playground "Run" endpoint has no
     * expected output to judge against). Delegates to {@link #executeBatch} with a
     * single-element case list - there is no separate HTTP path for this.
     */
    ExecutionResult executeOnce(
            ProgrammingLanguage language,
            String sourceCode,
            String stdin,
            int cpuTimeLimitMs,
            int memoryLimitKb);

    /**
     * Whether this implementation has enough configuration to be attempted at all
     * (e.g. a non-blank base URL). Does not guarantee the backend is reachable.
     */
    boolean isConfigured();
}
