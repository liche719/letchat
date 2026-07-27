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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChatHistoryHttpBenchmark {

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        ExecutorService httpExecutor = Executors.newFixedThreadPool(options.concurrency * 2);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(httpExecutor)
                .build();
        HttpRequest request = buildRequest(options);

        for (int index = 0; index < options.warmup; index++) {
            execute(client, request);
        }

        ExecutorService workers = Executors.newFixedThreadPool(options.concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(options.concurrency);
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        AtomicLong responseBytes = new AtomicLong();
        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());

        long startedAt = System.nanoTime();
        for (int worker = 0; worker < options.concurrency; worker++) {
            workers.submit(() -> {
                try {
                    start.await();
                    int current;
                    while ((current = sequence.getAndIncrement()) < options.requests) {
                        long requestStartedAt = System.nanoTime();
                        try {
                            byte[] body = execute(client, request);
                            if (containsSuccessfulCode(body)) {
                                success.incrementAndGet();
                                responseBytes.addAndGet(body.length);
                            } else {
                                failure.incrementAndGet();
                            }
                        } catch (Exception exception) {
                            failure.incrementAndGet();
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
            throw new IllegalStateException("Benchmark timed out after " + options.timeoutSeconds + " seconds");
        }
        double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;

        workers.shutdownNow();
        httpExecutor.shutdownNow();
        List<Double> sortedLatencies = new ArrayList<>(latencies);
        Collections.sort(sortedLatencies);
        double averageLatency = sortedLatencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double qps = success.get() / elapsedSeconds;
        double averageResponseKilobytes = success.get() == 0
                ? 0
                : responseBytes.get() / 1024.0 / success.get();

        System.out.printf(Locale.ROOT,
                "{\n" +
                        "  \"baseUrl\": \"%s\",\n" +
                        "  \"requests\": %d,\n" +
                        "  \"concurrency\": %d,\n" +
                        "  \"success\": %d,\n" +
                        "  \"failure\": %d,\n" +
                        "  \"elapsedSeconds\": %.3f,\n" +
                        "  \"qps\": %.2f,\n" +
                        "  \"averageMs\": %.2f,\n" +
                        "  \"p50Ms\": %.2f,\n" +
                        "  \"p95Ms\": %.2f,\n" +
                        "  \"maxMs\": %.2f,\n" +
                        "  \"averageResponseKilobytes\": %.2f\n" +
                        "}%n",
                options.baseUrl,
                options.requests,
                options.concurrency,
                success.get(),
                failure.get(),
                elapsedSeconds,
                qps,
                averageLatency,
                percentile(sortedLatencies, 50),
                percentile(sortedLatencies, 95),
                sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(sortedLatencies.size() - 1),
                averageResponseKilobytes);

        if (failure.get() > 0) {
            System.exit(1);
        }
    }

    private static HttpRequest buildRequest(Options options) {
        String body = "contactId=" + encode(options.contactId) + "&pageNo=1";
        return HttpRequest.newBuilder()
                .uri(URI.create(options.baseUrl + "/api/chat/loadChatMessage"))
                .timeout(Duration.ofSeconds(options.timeoutSeconds))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("token", options.token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static byte[] execute(HttpClient client, HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unexpected HTTP status " + response.statusCode());
        }
        return response.body();
    }

    private static boolean containsSuccessfulCode(byte[] body) {
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

    private record Options(
            String baseUrl,
            String token,
            String contactId,
            int requests,
            int concurrency,
            int warmup,
            int timeoutSeconds
    ) {
        private static Options parse(String[] arguments) {
            String baseUrl = null;
            String token = null;
            String contactId = "U10000100002";
            int requests = 40;
            int concurrency = 8;
            int warmup = 3;
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
                    case "--warmup" -> warmup = Integer.parseInt(value);
                    case "--timeout-seconds" -> timeoutSeconds = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("Unknown option " + name);
                }
            }

            if (baseUrl == null || token == null) {
                throw new IllegalArgumentException("--base-url and --token are required");
            }
            if (requests < 1 || concurrency < 1 || warmup < 0 || timeoutSeconds < 1) {
                throw new IllegalArgumentException("Benchmark options must be positive");
            }
            return new Options(baseUrl, token, contactId, requests, concurrency, warmup, timeoutSeconds);
        }
    }
}
