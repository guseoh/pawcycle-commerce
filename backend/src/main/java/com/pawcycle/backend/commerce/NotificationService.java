package com.pawcycle.backend.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Persists only in-app notifications; the event key makes transaction replay harmless. */
@Service
public class NotificationService {
	private final JdbcTemplate jdbc;
	public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public void create(long memberId, String type, String referenceType, long referenceId) {
		jdbc.update("INSERT INTO notifications(member_id,type,reference_type,reference_id,created_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id", memberId,type,referenceType,referenceId,Timestamp.from(Instant.now()));
	}
	public List<Map<String,Object>> list(long memberId) { return jdbc.queryForList("SELECT id AS notificationId,type,reference_type AS referenceType,reference_id AS referenceId,read_at AS readAt,created_at AS createdAt FROM notifications WHERE member_id=? ORDER BY id DESC",memberId); }
	public void read(long memberId,long id) { if (jdbc.update("UPDATE notifications SET read_at=COALESCE(read_at,?) WHERE id=? AND member_id=?",Timestamp.from(Instant.now()),id,memberId)!=1) throw new CommerceException(404,"NOTIFICATION_NOT_FOUND","요청한 리소스를 찾을 수 없습니다."); }
	public void readAll(long memberId) { jdbc.update("UPDATE notifications SET read_at=? WHERE member_id=? AND read_at IS NULL",Timestamp.from(Instant.now()),memberId); }
}
