import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ChatSendHttpBenchmark {

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        ExecutorService httpExecutor = Executors.newFixedThreadPool(options.concurrency * 2);
        ExecutorService workers = Executors.newFixedThreadPool(options.concurrency);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(httpExecutor)
                .build();
        AtomicInteger requestIndex = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        AtomicReference<String> firstFailure = new AtomicReference<>();
        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(options.concurrency);

        long startedAt = System.nanoTime();
        for (int worker = 0; worker < options.concurrency; worker++) {
            workers.submit(() -> {
                try {
                    start.await();
                    int current;
                    while ((current = requestIndex.getAndIncrement()) < options.requests) {
                        long requestStartedAt = System.nanoTime();
                        try {
                            HttpResponse<byte[]> response = client.send(
                                    request(options, current), HttpResponse.BodyHandlers.ofByteArray());
                            if (response.statusCode() == 200 && isSuccessful(response.body())) {
                                success.incrementAndGet();
                            } else {
                                failure.incrementAndGet();
                                firstFailure.compareAndSet(
                                        null,
                                        "HTTP " + response.statusCode() + ": "
                                                + new String(response.body(), StandardCharsets.UTF_8));
                            }
                        } catch (Exception exception) {
                            failure.incrementAndGet();
                            firstFailure.compareAndSet(null, exception.toString());
                        } finally {
                            latencies.add((System.nanoTime() - requestStartedAt) / 1_000_000.0);
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        if (!done.await(options.timeoutSeconds, TimeUnit.SECONDS)) {
            workers.shutdownNow();
            httpExecutor.shutdownNow();
            throw new IllegalStateException("Benchmark timed out after " + options.timeoutSeconds + " seconds");
        }
        double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        workers.shutdownNow();
        httpExecutor.shutdownNow();
        List<Double> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);

        System.out.printf(Locale.ROOT,
                "{\n" +
                        "  \"requests\": %d,\n" +
                        "  \"concurrency\": %d,\n" +
                        "  \"success\": %d,\n" +
                        "  \"failure\": %d,\n" +
                        "  \"elapsedSeconds\": %.3f,\n" +
                        "  \"qps\": %.2f,\n" +
                        "  \"p50Ms\": %.2f,\n" +
                        "  \"p95Ms\": %.2f,\n" +
                        "  \"p99Ms\": %.2f,\n" +
                        "  \"firstFailure\": %s\n" +
                        "}%n",
                options.requests,
                options.concurrency,
                success.get(),
                failure.get(),
                elapsedSeconds,
                success.get() / elapsedSeconds,
                percentile(sortedLatencies, 50),
                percentile(sortedLatencies, 95),
                percentile(sortedLatencies, 99),
                firstFailure.get() == null ? "null" : '"' + escapeJson(firstFailure.get()) + '"');

        if (failure.get() > 0) {
            System.exit(1);
        }
    }

    private static HttpRequest request(Options options, int index) {
        String clientMessageId = "benchmark-" + index + '-' + UUID.randomUUID();
        String body = "contactId=" + encode(options.contactId)
                + "&messageContent=" + encode("benchmark-message-" + index)
                + "&messageType=2"
                + "&clientMessageId=" + encode(clientMessageId);
        return HttpRequest.newBuilder()
                .uri(URI.create(options.baseUrl + "/api/chat/sendMessage"))
                .timeout(Duration.ofSeconds(options.timeoutSeconds))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("token", options.token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static boolean isSuccessful(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        return json.contains("\"code\":200") || json.contains("\"code\": 200");
    }

    private static double percentile(List<Double> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Options(String baseUrl, String token, String contactId, int requests, int concurrency,
                           int timeoutSeconds) {

        private static Options parse(String[] arguments) {
            String baseUrl = null;
            String token = null;
            String contactId = "U10000100002";
            int requests = 100;
            int concurrency = 8;
            int timeoutSeconds = 120;

            for (int index = 0; index < arguments.length; index += 2) {
                if (index + 1 >= arguments.length) {
                    throw new IllegalArgumentException("Missing value for " + arguments[index]);
                }
                String name = arguments[index];
                String value = arguments[index + 1];
                switch (name) {
                    case "--base-url" -> baseUrl = value.replaceAll("/+$", "");
                    case "--token" -> token = value;
                    case "--contact-id" -> contactId = value;
                    case "--requests" -> requests = Integer.parseInt(value);
                    case "--concurrency" -> concurrency = Integer.parseInt(value);
                    case "--timeout-seconds" -> timeoutSeconds = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("Unknown option " + name);
                }
            }
            if (baseUrl == null || token == null || requests < 1 || concurrency < 1 || timeoutSeconds < 1) {
                throw new IllegalArgumentException("--base-url and --token are required; numeric options must be positive");
            }
            return new Options(baseUrl, token, contactId, requests, concurrency, timeoutSeconds);
        }
    }
}
