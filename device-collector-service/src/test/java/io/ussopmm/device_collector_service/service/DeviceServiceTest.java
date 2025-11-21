package io.ussopmm.device_collector_service.service;

import com.nashkod.avro.Device;
import io.ussopmm.device_collector_service.entity.DeviceEntity;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.repository.DeviceRepositoryCustom;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {


    @Mock
    DeviceRepositoryCustom deviceRepository;

    @Mock
    ShardMetrics metrics;

    DeviceService service;

    @BeforeEach
    void setUp() {
        service = new DeviceService(deviceRepository);
    }

    @Test
    void save_shouldSplitIntoBatchesAndSumResults() {
        // given
        List<ConsumerRecord<String, Device>> records = makeRecords(120);

        // mock: возвращаем размер переданного батча как "кол-во upsert’ов"
        when(deviceRepository.upsertBatch(anyList(), any()))
                .thenAnswer(inv -> {
                    List<DeviceEntity> batch = inv.getArgument(0);
                    return (long) batch.size();
                });

        // when
        Mono<Long> result = service.save(records, metrics);

        // then
        StepVerifier.create(result)
                .expectNext(120L)
                .verifyComplete();

        // проверим, что батчи были именно 50/50/20
        ArgumentCaptor<List<DeviceEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(deviceRepository, times(3)).upsertBatch(captor.capture(), eq(metrics));

        List<List<DeviceEntity>> allBatches = captor.getAllValues();
        assertThat(allBatches).hasSize(3);
    }


    @Test
    void save_withEmptyInput_returnsZeroAndDoesNotCallRepo() {
        Mono<Long> result = service.save(Collections.emptyList(), metrics);

        StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete();

        verifyNoInteractions(deviceRepository);
    }

    @Test
    void save_withNonMultipleBatchSize_sumsAllBatches() {
        List<ConsumerRecord<String, Device>> records = makeRecords(77);

        when(deviceRepository.upsertBatch(anyList(), any()))
                .thenAnswer(inv -> (long) ((List<?>) inv.getArgument(0)).size());

        StepVerifier.create(service.save(records, metrics))
                .expectNext(77L)
                .verifyComplete();

        ArgumentCaptor<List<DeviceEntity>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(deviceRepository, times(2)).upsertBatch(captor.capture(), eq(metrics));
        assertThat(captor.getAllValues()).hasSize(2);
    }


    @Test
    void save_whenRepoFails_propagatesError() {
        List<ConsumerRecord<String, Device>> records = makeRecords(70); // 50 + 20

        when(deviceRepository.upsertBatch(anyList(), any()))
                .thenAnswer(new org.mockito.stubbing.Answer<Long>() {
                    int call = 0;
                    @Override
                    public Long answer(InvocationOnMock inv) {
                        call++;
                        if (call == 1) {
                            return (long) ((List<?>) inv.getArgument(0)).size(); // ок на первом батче
                        }
                        throw new RuntimeException("DB down");
                    }
                });

        StepVerifier.create(service.save(records, metrics))
                .expectErrorMatches(ex -> ex instanceof RuntimeException && ex.getMessage().contains("DB down"))
                .verify();

        // Вызовов было 2, второй упал
        verify(deviceRepository, times(2)).upsertBatch(anyList(), eq(metrics));
    }

    private List<ConsumerRecord<String, Device>> makeRecords(int n) {
        List<ConsumerRecord<String, Device>> list = new ArrayList<>(n);
        IntStream.range(0, n).forEach(i -> {
            Device d = device(
                    "dev-" + i,
                    i % 2 == 0 ? "TEST1" : "TEST2",
                    (long) i,
                    "meta-" + i
            );
            list.add(new ConsumerRecord<>("devices", 0, i, "key-" + i, d));
        });
        return list;
    }

    private Device device(String id, String type, Long createdAt, String meta) {
        Device d = new Device();
        d.setDeviceId(id);
        d.setDeviceType(type);
        d.setCreatedAt(createdAt);
        d.setMeta(meta);
        return d;
    }

}