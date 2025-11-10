package io.ussopmm.device_collector_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(schema = "device", name = "devices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceEntity {

    @Id
    @Column(name = "device_id", nullable = false, unique = true, length = 500)
    private String deviceId;

    @Column(name = "device_type", nullable = false, length = 255)
    private String deviceType;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(length = 1000)
    private String meta;
}
