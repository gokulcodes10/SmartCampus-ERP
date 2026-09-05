package smartcampus.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Buffers a request body into memory once, up front, so it can be read more than
 * once: {@link AuthRateLimitFilter} needs to peek at the JSON body to extract an
 * {@code email} for its rate-limit key, but the request body's underlying stream can
 * only be consumed once — a plain {@code request.getInputStream()} read in the filter
 * would leave nothing for Spring MVC's {@code @RequestBody} deserialization
 * downstream to read, silently breaking every login/register/password-reset call.
 *
 * <p>Unlike {@code org.springframework.web.util.ContentCachingRequestWrapper} (which
 * only caches bytes AS something downstream reads them, so a filter that already
 * fully drained the stream leaves nothing left to cache), this wrapper reads the
 * entire body into a byte array in its constructor and then serves every subsequent
 * {@link #getInputStream()} / {@link #getReader()} call from a fresh view over that
 * same array — genuinely re-readable, not just observed once.
 *
 * <p>Only used on the three low-volume auth endpoints {@link AuthRateLimitFilter}
 * guards; buffering the full body in memory is not something this filter does for
 * every request in the application.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        private CachedBodyServletInputStream(byte[] body) {
            this.buffer = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async body reads are not used on these endpoints.");
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return buffer.read(b, off, len);
        }
    }
}
