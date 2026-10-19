/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openmessaging.benchmark.driver.queen;


import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.uri.Uri;

/**
 * One AsyncHttpClient (Netty, HTTP/1.1 keep-alive) for the whole driver. Responses are read by
 * {@link Body}, which copies the body parts once, straight into one array (reusable by a poller).
 * No automatic retries: a push that failed is reported to OMB as failed, never sent twice.
 */
final class QueenHttp implements AutoCloseable {
    final AsyncHttpClient client;
    private final String authorization;

    QueenHttp(QueenConfig cfg) {
        DefaultAsyncHttpClientConfig.Builder b =
                new DefaultAsyncHttpClientConfig.Builder()
                        .setThreadPoolName("queen-http")
                        .setKeepAlive(true)
                        .setMaxRequestRetry(0)
                        .setFollowRedirect(false)
                        .setCompressionEnforced(false)
                        .setDisableUrlEncodingForBoundRequests(true)
                        .setCookieStore(null)
                        .setTcpNoDelay(true)
                        .setValidateResponseHeaders(false)
                        .setConnectTimeout(cfg.connectTimeoutMs)
                        .setRequestTimeout(cfg.requestTimeoutMs)
                        .setReadTimeout(cfg.requestTimeoutMs)
                        .setPooledConnectionIdleTimeout(120_000)
                        .setHttpClientCodecMaxChunkSize(64 * 1024)
                        // LAZY: a body part is the Netty buffer itself, copied once by Body.
                        .setResponseBodyPartFactory(AsyncHttpClientConfig.ResponseBodyPartFactory.LAZY);
        if (cfg.ioThreads > 0) {
            b.setIoThreadsCount(cfg.ioThreads);
        }
        client = new DefaultAsyncHttpClient(b.build());
        authorization = cfg.token == null || cfg.token.isEmpty() ? null : "Bearer " + cfg.token.trim();
    }

    private RequestBuilder builder(String method, Uri uri, int timeoutMs) {
        RequestBuilder b = new RequestBuilder(method, true).setUri(uri).setRequestTimeout(timeoutMs);
        if (authorization != null) {
            b.setHeader(HttpHeaderNames.AUTHORIZATION, authorization);
        }
        return b;
    }

    Request get(Uri uri, int timeoutMs) {
        return builder("GET", uri, timeoutMs).build();
    }

    Request post(Uri uri, byte[] body, int len, String contentType, int timeoutMs) {
        return builder("POST", uri, timeoutMs)
                .setHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
                .setBody(ByteBuffer.wrap(body, 0, len))
                .build();
    }

    Request put(Uri uri, byte[] body, String contentType, int timeoutMs) {
        return builder("PUT", uri, timeoutMs)
                .setHeader(HttpHeaderNames.CONTENT_TYPE, contentType)
                .setBody(body)
                .build();
    }

    /* Runs req; the future completes with the whole answer (any status) or the transport error. */
    CompletableFuture<Body> call(Request req) {
        CompletableFuture<Body> f = new CompletableFuture<>();
        execute(
                req,
                new Body(
                        null,
                        (r, err) -> {
                            if (err != null) {
                                f.completeExceptionally(err);
                            } else {
                                f.complete(r);
                            }
                        }));
        return f;
    }

    /* Runs req with handler h; a request that cannot even start reaches h as an error. */
    void execute(Request req, Body h) {
        try {
            client.executeRequest(req, h);
        } catch (Throwable t) {
            h.onThrowable(t);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    /** What a finished request reports: the answer (status, body) or err. Called exactly once. */
    interface Done {
        void done(Body answer, Throwable err);
    }

    /** Reads one answer into buf (reused when large enough), then reports it to done. */
    static final class Body implements AsyncHandler<Body> {
        int status;
        byte[] buf;
        int len;
        private final Done done;
        private final AtomicBoolean reported = new AtomicBoolean();

        Body(byte[] reuse, Done done) {
            this.buf = reuse;
            this.done = done;
        }

        @Override
        public State onStatusReceived(HttpResponseStatus s) {
            status = s.getStatusCode();
            return State.CONTINUE;
        }

        @Override
        public State onHeadersReceived(HttpHeaders headers) {
            String cl = headers.get(HttpHeaderNames.CONTENT_LENGTH);
            if (cl != null) {
                try {
                    int n = Integer.parseInt(cl.trim());
                    if (n > 0 && (buf == null || buf.length < n)) {
                        buf = new byte[n];
                    }
                } catch (NumberFormatException ignored) {
                    // grow as the parts come
                }
            }
            return State.CONTINUE;
        }

        @Override
        public State onBodyPartReceived(HttpResponseBodyPart part) {
            ByteBuffer bb = part.getBodyByteBuffer();
            int n = bb.remaining();
            if (n > 0) {
                if (buf == null) {
                    buf = new byte[Math.max(n, 512)];
                } else if (len + n > buf.length) {
                    buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + n));
                }
                bb.get(buf, len, n);
                len += n;
            }
            return State.CONTINUE;
        }

        @Override
        public void onThrowable(Throwable t) {
            if (done != null && reported.compareAndSet(false, true)) {
                done.done(this, t);
            }
        }

        @Override
        public Body onCompleted() {
            if (done != null && reported.compareAndSet(false, true)) {
                try {
                    done.done(this, null);
                } catch (Throwable t) {
                    // the callback's own failure must not come back as a transport error
                    QueenBenchmarkDriver.warn("callback", "a completion callback failed", t);
                }
            }
            return this;
        }
    }
}
