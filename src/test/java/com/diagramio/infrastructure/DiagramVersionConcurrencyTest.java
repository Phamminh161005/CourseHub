package com.diagramio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.version.DiagramVersion;
import com.diagramio.domain.version.DiagramVersionService;
import com.diagramio.domain.version.RestoreResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Nhiều luồng gọi lưu/khôi phục cùng lúc trên PostgreSQL thật: các yêu cầu phải xếp hàng
 * (khóa dòng sơ đồ) và đều thành công, số thứ tự phiên bản tăng tuyến tính 1..n.
 * Bỏ qua khi máy không có Docker.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DiagramVersionConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    /** Người dùng mẫu được seed trong V2. */
    private static final UUID SEED_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Caller OWNER = Caller.user(SEED_USER);
    private static final String DATA_A = "{\"nodes\": [{\"id\": \"A\"}], \"edges\": []}";
    private static final String DATA_B = "{\"nodes\": [{\"id\": \"B\"}], \"edges\": []}";

    @Autowired
    private DiagramVersionService versionService;

    @Autowired
    private DiagramRepository diagramRepository;

    @Test
    void concurrentSavesQueueUpAndAllGetLinearNumbers() throws Exception {
        UUID diagramId = newDiagram(DATA_A).getId();

        List<DiagramVersion> saved = runConcurrently(20,
                i -> () -> versionService.createVersion(diagramId, "save " + i, OWNER));

        List<Integer> numbers = saved.stream().map(DiagramVersion::versionNumber).sorted().toList();
        assertThat(numbers).isEqualTo(IntStream.rangeClosed(1, 20).boxed().toList());
    }

    @Test
    void concurrentRestoresQueueUpAndEachBacksUpThePreviousState() throws Exception {
        Diagram diagram = newDiagram(DATA_A);
        DiagramVersion snapshotA = versionService.createVersion(diagram.getId(), "A", OWNER);
        diagram.setData(DATA_B);
        int versionBefore = diagramRepository.save(diagram).getVersion();

        List<RestoreResult> results = runConcurrently(10,
                i -> () -> versionService.restore(diagram.getId(), snapshotA.id(), OWNER));

        List<DiagramVersion> backups = results.stream().map(RestoreResult::backup)
                .sorted((a, b) -> Integer.compare(a.versionNumber(), b.versionNumber()))
                .toList();
        // Lần khôi phục đầu tiên sao lưu B; các lần sau sao lưu A (trạng thái lần trước để lại)
        assertThat(backups.get(0).data()).contains("\"B\"");
        assertThat(backups.subList(1, backups.size())).allSatisfy(b -> assertThat(b.data()).contains("\"A\""));
        assertThat(backups).extracting(DiagramVersion::versionNumber)
                .isEqualTo(IntStream.rangeClosed(2, 11).boxed().toList());

        Diagram restored = diagramRepository.findById(diagram.getId()).orElseThrow();
        assertThat(restored.getVersion()).isEqualTo(versionBefore + 10);
        assertThat(restored.getData()).contains("\"A\"");
    }

    private Diagram newDiagram(String data) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return diagramRepository.save(new Diagram(null, "race", "ERD", data, 1, false, SEED_USER, SEED_USER, now, now));
    }

    /** Chạy n tác vụ cùng một thời điểm và trả về kết quả; ném lỗi nếu có tác vụ thất bại. */
    private static <T> List<T> runConcurrently(int n, java.util.function.IntFunction<Callable<T>> task)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Callable<T> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return work.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
