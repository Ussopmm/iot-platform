package io.ussopmm.device_collector_service.listener;

import com.nashkod.avro.Device;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.shardingsphere.infra.exception.postgresql.exception.PostgreSQLException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceListener {

    @Value("${spring.kafka.dlt-topic.name}")
    private String dltTopic;
    private final DeviceService deviceService;
    private final KafkaTemplate<String, Device> kafkaTemplate;
    private final ShardMetrics metrics;

    @Value("${app.retry.maxAttempts:5}") private int maxAttempts;
    @Value("${app.retry.minBackoffS:2000}") private long minBackoffS;
    @Value("${app.retry.maxBackoffS:32000}") private long maxBackoffS;

    @KafkaListener(
            topics = "${spring.kafka.topic.name}",
            groupId = "${spring.kafka.consumer.group-id}",
            batch = "true",
            containerFactory = "kafkaConsumerContainerFactory"
    )
    public void listen(List<ConsumerRecord<String, Device>> devices, Acknowledgment ack) {
        log.info("Devices received: {} messages", devices.size());
        var tracer = GlobalOpenTelemetry.getTracer(DeviceListener.class.getName());
        var span = tracer.spanBuilder("deviceListener.listen").startSpan();
        try (var _1 = span.makeCurrent()) {
            deviceService.save(devices, metrics)
                    .retryWhen(
                            Retry.backoff(maxAttempts, Duration.ofSeconds(minBackoffS))
                                    .maxBackoff(Duration.ofSeconds(maxBackoffS))
                                    .jitter(0.5)
                                    .doBeforeRetry(sig -> {
                                        log.warn("Retry #{}, cause={}",
                                                sig.totalRetries() + 1,
                                                sig.failure().toString());
                                    })
                    )
                    .doOnSuccess(v -> {
                        span.setAttribute("saved.count", v);
                        log.info("Devices [amount of {}] saved & acked successfully", v);
                    })
                    .then(Mono.fromRunnable(ack::acknowledge))
                    .doOnError(ex -> {
                        span.recordException(ex);
                        span.setStatus(StatusCode.ERROR, ex.getMessage() == null ? "" : ex.getMessage());
                        log.error("Save failed; will send to DLT", ex);
                    })
                    .onErrorResume(ex -> sendBatchToDLT(devices, ex)
                                .then(Mono.fromRunnable(ack::acknowledge)))
                    .doFinally(s -> {
                        log.info("Listener processing completed with signal: {}", s);
                        span.end();
                    })
                    .subscribe();
        } catch (Exception e) {
            log.error("Fatal error in listener", e);
            throw e;
        }
    }


    private Mono<Void> sendBatchToDLT(List<ConsumerRecord<String, Device>> records, Throwable ex) {
        return Flux.fromIterable(records)
                .flatMap(rec -> {
                    String devId = rec.value().getDeviceId() != null ? rec.value().getDeviceId() : null;
                    int shardIdx = devId == null ? -1 : Math.floorMod(devId.hashCode(), 2);
                    try (var _1 = MDC.putCloseable("topic", rec.topic());
                         var _2 = MDC.putCloseable("partition", String.valueOf(rec.partition()));
                         var _3 = MDC.putCloseable("offset", String.valueOf(rec.offset()));
                         var _4 = MDC.putCloseable("deviceId", rec.value().getDeviceId());
                         var _5 = MDC.putCloseable("shard", String.valueOf(shardIdx))) {

                        log.error("Batch save of Devices failed: {}", ex.toString(), ex);
                        // сохраняем key и payload без изменений
                        ProducerRecord<String, Device> dlt =
                                new ProducerRecord<>(dltTopic, rec.partition(), rec.key(), rec.value());
                        spanCompletion(rec, ex, shardIdx);
                        metrics.incError(shardIdx == -1 ? "unknown" : Integer.toString(shardIdx), causeCode(ex));
                        // переносим все оригинальные заголовки
                        rec.headers().forEach(h -> dlt.headers().add(h));
                        // диагностические заголовки
                        dlt.headers().add("kafka_dlt-exception-fqcn", ex.getClass().getName().getBytes(UTF_8));
                        dlt.headers().add("kafka_dlt-exception-message",
                                safeMsg(ex).getBytes(UTF_8));
                        dlt.headers().add("kafka_dlt-original-topic", rec.topic().getBytes(UTF_8));
                        dlt.headers().add("kafka_dlt-original-partition",
                                Integer.toString(rec.partition()).getBytes(UTF_8));
                        dlt.headers().add("kafka_dlt-original-offset",
                                Long.toString(rec.offset()).getBytes(UTF_8));
                        dlt.headers().add("kafka_dlt-original-timestamp",
                                Long.toString(rec.timestamp()).getBytes(UTF_8));

                        // отправляем именно ProducerRecord, чтобы ушли заголовки
                        return Mono.fromFuture(kafkaTemplate.send(dlt).toCompletableFuture());

                    }
                }, 8)
                .then();
    }

    private String causeCode(Throwable ex) {
        Throwable e = ex;
        while (e.getCause() != null && e != e.getCause()) {
            e = e.getCause();
        }

        if (e instanceof SQLTimeoutException
                || e instanceof java.net.SocketTimeoutException)         return "DB_TIMEOUT";

        if (e instanceof TransientDataAccessResourceException
                || e instanceof java.net.ConnectException)               return "DB_TRANSIENT";

        if (e instanceof PostgreSQLException ps
                && "40001".equals(ps.getSQLState()))                 return "DB_SERIALIZATION"; // serialization failure

        if (e instanceof org.springframework.dao.DuplicateKeyException
                || e instanceof IllegalArgumentException)               return "VALIDATION";

        return "OTHER";
    }

    private void spanCompletion(ConsumerRecord<String, Device> rec, Throwable ex, int shard) {
        var span = Span.current();
        span.setAttribute("kafka.topic", rec.topic());
        span.setAttribute("kafka.partition", rec.partition());
        span.setAttribute("kafka.offset", rec.offset());
        span.setAttribute("device.id", rec.value().getDeviceId());
        span.setAttribute("db.shard", shard);
        span.recordException(ex);
        span.setStatus(StatusCode.ERROR, ex.getMessage() == null ? "" : ex.getMessage());
        span.setAttribute("error.message", ex.getMessage());
    }

    private String safeMsg(Throwable ex) {
        String m = ex.getMessage();
        return m == null ? "" : (m.length() > 1000 ? m.substring(0, 1000) : m);
    }
}
