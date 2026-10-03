package com.persiki84.battlecraft.client.lyrics;

import com.persiki84.battlecraft.BattleCraftMod;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// WHY: таймаут запроса в JDK кончается на заголовках, и оборванная посреди тела связь вешала бы
// WHY: единственный поток лирики навсегда: тело закрывается по общему дедлайну и режется по пределу
final class LyricsHttp {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(4L);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6L);
    private static final long BODY_DEADLINE_SECONDS = 10L;
    private static final long BODY_LIMIT = 4L * 1024L * 1024L;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final String agent;

    LyricsHttp(String agent) {
        this.agent = agent;
    }

    record Reply(int status, InputStream body) {}

    Reply get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", agent).header("Accept", "application/json").GET().build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        InputStream body = response.body();
        CompletableFuture.delayedExecutor(BODY_DEADLINE_SECONDS, TimeUnit.SECONDS).execute(() -> close(body));
        return new Reply(response.statusCode(), new Bounded(body));
    }

    private static void close(InputStream body) {
        try {
            body.close();
        } catch (IOException error) {
            BattleCraftMod.LOGGER.debug("lyrics response not closed: {}", error.toString());
        }
    }

    // WHY: на пробел, закодированный плюсом, LRCLIB стабильно отвечает 503 «server is busy», а тот же
    // WHY: запрос с %20 отдаёт 200: URLEncoder кодирует форму, а не путь запроса
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class Bounded extends FilterInputStream {
        private long left = BODY_LIMIT;

        private Bounded(InputStream source) {
            super(source);
        }

        @Override
        public int read() throws IOException {
            if (left <= 0L) throw new IOException("lyrics response larger than " + BODY_LIMIT);
            int value = super.read();
            if (value >= 0) left--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (left <= 0L) throw new IOException("lyrics response larger than " + BODY_LIMIT);
            int read = super.read(buffer, offset, (int) Math.min(length, left));
            if (read > 0) left -= read;
            return read;
        }
    }
}
