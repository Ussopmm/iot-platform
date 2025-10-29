package io.ussopmm.eventcollectorservice.entity;

import lombok.*;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;


@Table("device_events_by_device")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEventEntity {
    @PrimaryKeyClass
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Key {
        @PrimaryKeyColumn(name = "device_id", type = PrimaryKeyType.PARTITIONED)
        private String deviceId;

        @PrimaryKeyColumn(name = "timestamp", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
        private long timestamp;

        @PrimaryKeyColumn(name = "event_id", type = PrimaryKeyType.CLUSTERED)
        private String eventId;

    }
    @PrimaryKey
    private Key key;
    private String type;
    private String payload;
}
