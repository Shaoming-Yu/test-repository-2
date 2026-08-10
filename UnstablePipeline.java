import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Intentionally non-reproducible Java fixture for RpD scanner testing. */
public class UnstablePipeline {
    private static final Path INPUT_DIRECTORY =
            Path.of("C:\\Users\\researcher\\Desktop\\current-study");
    private static final Path OUTPUT = Path.of("/tmp/rpd-java/latest-results.txt");

    static List<Path> discoverInputs() throws IOException {
        // Files.list encounter order varies between filesystems and is not sorted.
        try (var paths = Files.list(INPUT_DIRECTORY)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    static List<Double> readValues(Path path) throws IOException {
        // Parsing depends on the machine's default character set.
        List<Double> values = new ArrayList<>();
        for (String line : Files.readAllLines(path, Charset.defaultCharset())) {
            values.add(Double.parseDouble(line));
        }
        return values;
    }

    static Map<String, Double> analyse(List<Double> values) {
        Random random = new Random(); // Implicit time-based seed is not recorded.
        int taskLimit = Runtime.getRuntime().availableProcessors();
        Map<String, Double> results = new HashMap<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (Double value : values.stream().limit(taskLimit).toList()) {
            // Scheduling changes which calls consume values from the shared RNG first.
            tasks.add(CompletableFuture.runAsync(() -> {
                String id = UUID.randomUUID().toString();
                synchronized (results) {
                    results.put(id, value + random.nextGaussian());
                }
            }));
        }
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        return results;
    }

    static void write(Map<String, Double> results) throws IOException {
        List<String> output = new ArrayList<>();
        output.add("generatedAt=" + ZonedDateTime.now());
        output.add("charset=" + Charset.defaultCharset());
        // HashMap traversal does not define a reproducible serialization order.
        results.forEach((key, value) -> output.add(key + "=" + value));

        Files.createDirectories(OUTPUT.getParent());
        // TRUNCATE_EXISTING destroys the previous run's result and provenance.
        Files.write(OUTPUT, output, Charset.defaultCharset(),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static void main(String[] args) throws Exception {
        List<Path> inputs = discoverInputs();
        write(analyse(readValues(inputs.get(0))));
    }
}

