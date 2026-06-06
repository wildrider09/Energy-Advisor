package com.amazon.energyadvisor.store;

import com.amazon.energyadvisor.device.DeviceEvent;
import com.amazon.energyadvisor.model.ThermostatEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class EventStore {

    private final JdbcTemplate jdbc;

    public EventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void init() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS events (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                device_id VARCHAR(64),
                device_type VARCHAR(32),
                event_type VARCHAR(32),
                event_value VARCHAR(256),
                numeric_value DOUBLE,
                ts TIMESTAMP
            )
        """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_device_ts ON events(device_id, ts)");
    }

    public void save(ThermostatEvent event) {
        saveEvent(event);
    }

    /** Generic save for any DeviceEvent implementation. */
    public void saveEvent(DeviceEvent event) {
        jdbc.update("INSERT INTO events (device_id, device_type, event_type, event_value, numeric_value, ts) VALUES (?,?,?,?,?,?)",
                event.getDeviceId(),
                event.getDeviceType(),
                event.getEventTypeName(),
                event.getValue(),
                event.getNumericValue(),
                Timestamp.from(event.getTimestamp()));
    }

    public void saveAll(List<ThermostatEvent> events) {
        events.forEach(this::save);
    }

    /** Generic saveAll for any DeviceEvent list. */
    public void saveAllEvents(List<? extends DeviceEvent> events) {
        events.forEach(this::saveEvent);
    }

    public List<Map<String, Object>> getRecentEvents(String deviceId, int limit) {
        return jdbc.queryForList(
                "SELECT event_type, event_value, numeric_value, ts FROM events WHERE device_id = ? ORDER BY ts DESC LIMIT ?",
                deviceId, limit);
    }

    public List<Map<String, Object>> getEventsSince(String deviceId, Instant since) {
        return jdbc.queryForList(
                "SELECT event_type, event_value, numeric_value, ts FROM events WHERE device_id = ? AND ts >= ? ORDER BY ts ASC",
                deviceId, Timestamp.from(since));
    }

    public List<Map<String, Object>> getEventsByType(String deviceId, String eventType, int limit) {
        return jdbc.queryForList(
                "SELECT event_value, numeric_value, ts FROM events WHERE device_id = ? AND event_type = ? ORDER BY ts DESC LIMIT ?",
                deviceId, eventType, limit);
    }

    public int countEventsByType(String deviceId, String eventType, Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE device_id = ? AND event_type = ? AND event_value = 'RUNNING' AND ts >= ?",
                Integer.class, deviceId, eventType, Timestamp.from(since));
        return count != null ? count : 0;
    }

    public void clear() {
        jdbc.execute("DELETE FROM events");
    }
}
