package com.pawcycle.backend.subscription.v2;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** V2 uses JDBC deliberately: its multi-table aggregate and compare-and-set writes stay explicit. */
@Repository
class V2SubscriptionJdbcStore {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final Clock clock;

	V2SubscriptionJdbcStore(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	V2SubscriptionData.Pet findOwnedPet(long memberId, long petId) {
		return one("SELECT id,name,pet_type FROM pets WHERE id=? AND member_id=?", petId, memberId)
				.map(this::pet)
				.orElseThrow(() -> new V2ApiException(404, "PET_NOT_FOUND", "Pet을 찾을 수 없습니다."));
	}

	V2SubscriptionData.PlanVersion findPlanVersion(long versionId) {
		return one("SELECT p.id plan_id,p.name plan_name,p.current_plan_version_id,p.target_pet_type,p.on_sale,p.sale_starts_on,p.sale_ends_on,v.id version_id,v.package_price_krw,v.is_migration_only FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE v.id=?", versionId)
				.map(this::planVersion)
				.orElseThrow(() -> new V2ApiException(404, "PLAN_VERSION_NOT_FOUND", "PlanVersion을 찾을 수 없습니다."));
	}

	boolean deliveryCycleAllowed(long versionId, int cycle) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=? AND delivery_cycle_weeks=?", Integer.class, versionId, cycle) > 0;
	}

	boolean reserveCreation(long memberId, String key, String fingerprint) {
		try { jdbc.update("INSERT INTO subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint) VALUES (?,?,?)", memberId, key, fingerprint); return true; }
		catch (DuplicateKeyException ignored) { return false; }
	}

	V2SubscriptionData.StoredIdempotencyResult lockCreationResult(long memberId, String key) {
		return one("SELECT payload_fingerprint,response_status,response_body,location_header,etag_header FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key=? FOR UPDATE", memberId, key)
				.map(this::storedIdempotency)
				.orElseThrow();
	}

	void updateCreationResponse(long memberId, String key, long subscriptionId, V2SubscriptionOperationResult result, String bodyJson) {
		jdbc.update("UPDATE subscription_creation_idempotency_results SET subscription_id=?,response_status=?,response_body=?,location_header=?,etag_header=?,completed_at=COALESCE(completed_at,UTC_TIMESTAMP(6)) WHERE member_id=? AND idempotency_key=?", subscriptionId, result.status(), bodyJson, result.location(), result.etag(), memberId, key);
	}
	void updateStoredCreationBody(long memberId, String key, String bodyJson) { jdbc.update("UPDATE subscription_creation_idempotency_results SET response_body=? WHERE member_id=? AND idempotency_key=?", bodyJson, memberId, key); }

	long insertSubscription(long memberId, long versionId, int cycle, long petId, LocalDate created, LocalDate next) {
		jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,current_snapshot_id,legacy_api_visible,mvp2_managed) VALUES (?,?,?,?,?,?,?,'ACTIVE',0,NULL,false,true)", memberId, firstSku(versionId), 1, cycle, created, next, petId);
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	void setCurrentSnapshot(long subscriptionId, long snapshotId) { jdbc.update("UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshotId, subscriptionId); }
	void insertScheduled(long subscriptionId, LocalDate date) { jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) VALUES (?,?,'SCHEDULED',NULL)", subscriptionId, date); }
	long createSnapshot(long subscriptionId, long versionId, int cycle, long price) { return snapshot(subscriptionId, versionId, cycle, price); }

	V2SubscriptionData.Subscription lockOwnedSubscription(long memberId, long subscriptionId) {
		return one("SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true FOR UPDATE", subscriptionId, memberId)
				.map(this::subscription)
				.orElseThrow(() -> new V2ApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
	}

	V2SubscriptionData.Subscription findOwnedSubscription(long memberId, long subscriptionId) {
		return one("SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true", subscriptionId, memberId)
				.map(this::subscription)
				.orElseThrow(() -> new V2ApiException(404, "SUBSCRIPTION_NOT_FOUND", "Subscription을 찾을 수 없습니다."));
	}

	long insertPet(long memberId, String name, String petType) { jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,?,?)", memberId, name, petType); return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
	V2SubscriptionData.Page<V2SubscriptionData.Pet> findPets(long memberId, int page, int size) { long total = jdbc.queryForObject("SELECT COUNT(*) FROM pets WHERE member_id=?", Long.class, memberId); List<V2SubscriptionData.Pet> items = jdbc.query("SELECT id,name,pet_type FROM pets WHERE member_id=? ORDER BY id ASC LIMIT ? OFFSET ?", (rs,n) -> new V2SubscriptionData.Pet(rs.getLong("id"),rs.getString("name"),rs.getString("pet_type")), memberId,size,page*size); return new V2SubscriptionData.Page<>(page,size,total,items); }
	V2SubscriptionData.Page<V2SubscriptionData.PlanVersion> findSalePlanVersions(String petType, LocalDate today, int page, int size) { String where=" FROM subscription_plans p JOIN plan_versions v ON v.id=p.current_plan_version_id WHERE p.name IS NOT NULL AND p.target_pet_type=? AND p.on_sale=true AND v.is_migration_only=false AND (p.sale_starts_on IS NULL OR p.sale_starts_on<=?) AND (p.sale_ends_on IS NULL OR p.sale_ends_on>=?)"; long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,petType,today,today); List<V2SubscriptionData.PlanVersion> items=jdbc.query("SELECT p.id plan_id,p.name plan_name,p.current_plan_version_id,p.target_pet_type,p.on_sale,p.sale_starts_on,p.sale_ends_on,v.id version_id,v.package_price_krw,v.is_migration_only"+where+" ORDER BY p.id ASC,v.id ASC LIMIT ? OFFSET ?", (rs,n)->new V2SubscriptionData.PlanVersion(rs.getLong("plan_id"),rs.getString("plan_name"),(Long)rs.getObject("current_plan_version_id"),rs.getString("target_pet_type"),rs.getBoolean("on_sale"),rs.getDate("sale_starts_on")==null?null:rs.getDate("sale_starts_on").toLocalDate(),rs.getDate("sale_ends_on")==null?null:rs.getDate("sale_ends_on").toLocalDate(),rs.getLong("version_id"),rs.getLong("package_price_krw"),rs.getBoolean("is_migration_only")),petType,today,today,size,page*size); return new V2SubscriptionData.Page<>(page,size,total,items); }
	List<V2SubscriptionData.Item> findPlanItems(long versionId) { return jdbc.query("SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=? ORDER BY sku_id", (rs,n)->new V2SubscriptionData.Item(rs.getLong("sku_id"),rs.getInt("quantity")),versionId); }
	List<Integer> findDeliveryCycles(long versionId) { return jdbc.queryForList("SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=? ORDER BY delivery_cycle_weeks",Integer.class,versionId); }
	V2SubscriptionData.Snapshot findSnapshot(long snapshotId) { V2SubscriptionData.Snapshot base=one("SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM subscription_snapshots WHERE id=?",snapshotId).map(row->new V2SubscriptionData.Snapshot(longValue(row,"id"),longValue(row,"source_plan_version_id"),longValue(row,"package_total_krw"),intValue(row,"delivery_cycle_weeks"),List.of())).orElseThrow(); return new V2SubscriptionData.Snapshot(base.id(),base.planVersionId(),base.packagePriceKrw(),base.deliveryCycleWeeks(),findPlanItemsForSnapshot(snapshotId)); }
	private List<V2SubscriptionData.Item> findPlanItemsForSnapshot(long snapshotId) { return jdbc.query("SELECT sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id=? ORDER BY sku_id",(rs,n)->new V2SubscriptionData.Item(rs.getLong("sku_id"),rs.getInt("quantity")),snapshotId); }

	boolean reserveCommand(long memberId, long subscriptionId, String command, String key, String fingerprint) {
		try { jdbc.update("INSERT INTO subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint) VALUES (?,?,?,?,?)", memberId, subscriptionId, command, key, fingerprint); return true; }
		catch (DuplicateKeyException ignored) { return false; }
	}

	V2SubscriptionData.StoredIdempotencyResult lockCommandResult(long memberId, long subscriptionId, String command, String key) {
		return one("SELECT payload_fingerprint,response_status,response_body,location_header,etag_header FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=? FOR UPDATE", memberId, subscriptionId, command, key)
				.map(this::storedIdempotency).orElseThrow();
	}

	void updateCommandResponse(long memberId, long subscriptionId, String command, String key, V2SubscriptionOperationResult result, String bodyJson) {
		jdbc.update("UPDATE subscription_command_idempotency_results SET response_status=?,response_body=?,location_header=?,etag_header=?,completed_at=COALESCE(completed_at,UTC_TIMESTAMP(6)) WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?", result.status(), bodyJson, result.location(), result.etag(), memberId, subscriptionId, command, key);
	}
	void updateStoredCommandBody(long memberId, long subscriptionId, String command, String key, String bodyJson) { jdbc.update("UPDATE subscription_command_idempotency_results SET response_body=? WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?", bodyJson, memberId, subscriptionId, command, key); }

	V2SubscriptionData.Schedule lockNextScheduled(long subscriptionId) {
		return one("SELECT schedule.id,schedule.scheduled_date FROM subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND schedule.status='SCHEDULED' AND existing_order.id IS NULL ORDER BY schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE", subscriptionId)
				.map(row -> new V2SubscriptionData.Schedule(longValue(row,"id"), date(row.get("scheduled_date"))))
				.orElseThrow(() -> new V2ApiException(409, "SUBSCRIPTION_COMMAND_NOT_ALLOWED", "다음 Schedule이 없습니다."));
	}

	void replacePendingPlanChange(long subscriptionId, long snapshotId, long scheduleId) { jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?", subscriptionId); jdbc.update("INSERT INTO pending_plan_changes(subscription_id,snapshot_id,target_schedule_id) VALUES (?,?,?)", subscriptionId, snapshotId, scheduleId); }
	void setSubscriptionPet(long subscriptionId, long petId) { jdbc.update("UPDATE subscriptions SET pet_id=? WHERE id=?", petId, subscriptionId); }
	void markSkipped(long scheduleId) { jdbc.update("UPDATE subscription_schedules SET status='SKIPPED' WHERE id=?", scheduleId); }
	long insertScheduledAndReturnId(long subscriptionId, LocalDate date) { insertScheduled(subscriptionId, date); return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
	void retargetPendingPlanChange(long subscriptionId, long scheduleId) { jdbc.update("UPDATE pending_plan_changes SET target_schedule_id=? WHERE subscription_id=?", scheduleId, subscriptionId); }
	void setSubscriptionStatus(long subscriptionId, String status) { jdbc.update("UPDATE subscriptions SET status=? WHERE id=?", status, subscriptionId); }
	void setScheduleStatus(long scheduleId, String status) { jdbc.update("UPDATE subscription_schedules SET status=? WHERE id=?", status, scheduleId); }
	List<Long> heldScheduleIds(long subscriptionId) { return jdbc.queryForList("SELECT id FROM subscription_schedules WHERE subscription_id=? AND status='HELD' ORDER BY scheduled_date,id", Long.class, subscriptionId); }
	boolean scheduleDateTaken(long subscriptionId, LocalDate date, long excludedScheduleId) { return jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND scheduled_date=? AND id<>?", Integer.class, subscriptionId, date, excludedScheduleId) > 0; }
	void rescheduleHeld(long scheduleId, LocalDate date) { jdbc.update("UPDATE subscription_schedules SET scheduled_date=?,status='SCHEDULED' WHERE id=?", date, scheduleId); }
	void cancelUnorderedSchedules(long subscriptionId) { jdbc.update("UPDATE subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id SET schedule.status='CANCELED' WHERE schedule.subscription_id=? AND schedule.status IN ('SCHEDULED','HELD') AND existing_order.id IS NULL", subscriptionId); }
	void deletePendingPlanChange(long subscriptionId) { jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?", subscriptionId); }
	boolean incrementVersion(long subscriptionId, long expected) { return jdbc.update("UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?", subscriptionId, expected) == 1; }
	void insertCommandHistory(long subscriptionId, String command, long before, long after) { jdbc.update("INSERT INTO subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after) VALUES (?,?,UTC_TIMESTAMP(6),?,?)", subscriptionId, command, before, after); }

	private V2SubscriptionData.Pet pet(Map<String,Object> row) { return new V2SubscriptionData.Pet(longValue(row,"id"), (String) row.get("name"), (String) row.get("pet_type")); }
	private V2SubscriptionData.PlanVersion planVersion(Map<String,Object> row) { return new V2SubscriptionData.PlanVersion(longValue(row,"plan_id"), (String) row.get("plan_name"), row.get("current_plan_version_id") == null ? null : longValue(row,"current_plan_version_id"), (String) row.get("target_pet_type"), Boolean.TRUE.equals(row.get("on_sale")), date(row.get("sale_starts_on")), date(row.get("sale_ends_on")), longValue(row,"version_id"), longValue(row,"package_price_krw"), Boolean.TRUE.equals(row.get("is_migration_only"))); }
	private V2SubscriptionData.Subscription subscription(Map<String,Object> row) { return new V2SubscriptionData.Subscription(longValue(row,"id"), longValue(row,"member_id"), (String) row.get("status"), longValue(row,"version"), row.get("pet_id") == null ? null : longValue(row,"pet_id"), intValue(row,"delivery_cycle_weeks"), longValue(row,"current_snapshot_id")); }
	private V2SubscriptionData.StoredIdempotencyResult storedIdempotency(Map<String,Object> row) { return new V2SubscriptionData.StoredIdempotencyResult((String)row.get("payload_fingerprint"), intValue(row,"response_status"), (String)row.get("response_body"), (String)row.get("location_header"), (String)row.get("etag_header")); }
	private LocalDate date(Object value) { return value == null ? null : ((java.sql.Date) value).toLocalDate(); }

	V2SubscriptionData.Page<V2SubscriptionData.DetailProjection> findSubscriptionList(long memberId, int page, int size) { long total=jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE member_id=? AND mvp2_managed=true",Long.class,memberId); List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,version,next_order_date,pet_id,current_snapshot_id FROM subscriptions WHERE member_id=? AND mvp2_managed=true ORDER BY id DESC LIMIT ? OFFSET ?",memberId,size,page*size); return new V2SubscriptionData.Page<>(page,size,total,subscriptionSummaryDtos(memberId,rows).stream().map(V2SubscriptionData.DetailProjection::new).toList()); }

	List<Long> activeSubscriptionIds() {
		return jdbc.queryForList("SELECT id FROM subscriptions WHERE mvp2_managed=true AND status='ACTIVE' ORDER BY id", Long.class);
	}

	Optional<V2SubscriptionData.Subscription> lockActiveSubscription(long subscriptionId) { return one("SELECT id,member_id,status,version,pet_id,delivery_cycle_weeks,current_snapshot_id FROM subscriptions WHERE id=? AND mvp2_managed=true AND status='ACTIVE' FOR UPDATE", subscriptionId).map(this::subscription); }
	boolean hasUnprocessedDueSchedule(long subscriptionId, LocalDate today) { return !jdbc.queryForList("SELECT schedule.id FROM subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND schedule.status='SCHEDULED' AND schedule.scheduled_date<=? AND existing_order.id IS NULL ORDER BY schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE", subscriptionId, today).isEmpty(); }
	List<V2SubscriptionData.Schedule> futureSchedulesForUpdate(long subscriptionId, LocalDate today) { return jdbc.query("SELECT id,scheduled_date FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>? ORDER BY scheduled_date,id FOR UPDATE", (rs, rowNum) -> new V2SubscriptionData.Schedule(rs.getLong("id"), rs.getDate("scheduled_date").toLocalDate()), subscriptionId, today); }
	Optional<V2SubscriptionData.ProcessedSchedule> lastProcessedSchedule(long subscriptionId) { return one("SELECT orders.scheduled_date,snapshot.delivery_cycle_weeks FROM subscription_orders orders JOIN subscription_snapshots snapshot ON snapshot.id=orders.effective_snapshot_id WHERE orders.subscription_id=? ORDER BY orders.scheduled_date DESC,orders.id DESC LIMIT 1", subscriptionId).map(row -> new V2SubscriptionData.ProcessedSchedule(date(row.get("scheduled_date")), intValue(row,"delivery_cycle_weeks"))); }
	boolean scheduleExists(long subscriptionId, LocalDate date) { return jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND scheduled_date=?", Integer.class, subscriptionId, date) > 0; }

	V2SubscriptionData.DetailProjection detailProjection(long memberId,long id,int schedulePage,int scheduleSize,int commandPage,int commandSize) { return new V2SubscriptionData.DetailProjection(detail(memberId,id,schedulePage,scheduleSize,commandPage,commandSize)); }
	private Map<String,Object> detail(long memberId,long id,int schedulePage,int scheduleSize,int commandPage,int commandSize) { Page sp=page(schedulePage,scheduleSize), cp=page(commandPage,commandSize); Map<String,Object> sub=ownedSubscription(memberId,id); Map<String,Object> result=subscriptionSummaryDto(memberId,sub); Optional<Map<String,Object>> pending=one("SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",id); result.put("pendingSnapshot",pending.map(p -> snapshotDto(longValue(p,"snapshot_id"))).orElse(null)); long st=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=?",Long.class,id); List<Map<String,Object>> schedules=jdbc.queryForList("SELECT id,scheduled_date,status,effective_snapshot_id FROM subscription_schedules WHERE subscription_id=? ORDER BY scheduled_date DESC,id DESC LIMIT ? OFFSET ?",id,sp.size(),sp.offset()).stream().map(this::scheduleDto).toList(); result.put("schedules",pageResult(sp,st,schedules)); long ct=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=?",Long.class,id); List<Map<String,Object>> history=jdbc.query("SELECT command_type,occurred_at FROM subscription_command_history WHERE subscription_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",(rs,rowNum) -> Map.<String,Object>of("commandType",rs.getString("command_type"),"result","SUCCEEDED","occurredAt",rs.getTimestamp("occurred_at").toInstant().atZone(SEOUL).toOffsetDateTime().toString()),id,cp.size(),cp.offset()); result.put("commandHistory",pageResult(cp,ct,history)); return result; }
	private Map<String,Object> subscriptionSummaryDto(long memberId,Map<String,Object> sub) { Map<String,Object> result=new LinkedHashMap<>(); result.put("subscriptionId",longValue(sub,"id")); result.put("status",sub.get("status")); result.put("version",longValue(sub,"version")); result.put("pet",sub.get("pet_id")==null?null:petDto(ownedPet(memberId,longValue(sub,"pet_id")))); result.put("currentSnapshot",snapshotDto(longValue(sub,"current_snapshot_id"))); result.put("nextScheduledDate", "ACTIVE".equals(sub.get("status")) ? jdbc.query("SELECT schedule.scheduled_date FROM subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND schedule.status='SCHEDULED' AND schedule.scheduled_date>=? AND existing_order.id IS NULL ORDER BY schedule.scheduled_date,schedule.id LIMIT 1", rs -> rs.next()?rs.getDate(1).toLocalDate():null,longValue(sub,"id"),today()) : null); return result; }
	private List<Map<String,Object>> subscriptionSummaryDtos(long memberId,List<Map<String,Object>> rows) { List<Long> ids=ids(rows,"id",false); Map<Long,Map<String,Object>> pets=rowsById("SELECT id,name,pet_type FROM pets WHERE member_id=? AND id IN ",ids(rows,"pet_id",true),memberId); Map<Long,Map<String,Object>> snapshots=rowsById("SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM subscription_snapshots WHERE id IN ",ids(rows,"current_snapshot_id",false)); Map<Long,List<Map<String,Object>>> snapshotItems=groupedRows("SELECT snapshot_id,sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id IN ",ids(rows,"current_snapshot_id",false),"snapshot_id","sku_id"); Map<Long,LocalDate> schedules=nextSchedules(ids,today()); return rows.stream().map(sub -> subscriptionSummaryDto(memberId,sub,pets,snapshots,snapshotItems,schedules)).toList(); }
	private Map<String,Object> subscriptionSummaryDto(long memberId,Map<String,Object> sub,Map<Long,Map<String,Object>> pets,Map<Long,Map<String,Object>> snapshots,Map<Long,List<Map<String,Object>>> snapshotItems,Map<Long,LocalDate> schedules) { long id=longValue(sub,"id"), snapshotId=longValue(sub,"current_snapshot_id"); Map<String,Object> result=new LinkedHashMap<>(); result.put("subscriptionId",id); result.put("status",sub.get("status")); result.put("version",longValue(sub,"version")); if(sub.get("pet_id")==null) result.put("pet",null); else { Map<String,Object> pet=pets.get(longValue(sub,"pet_id")); if(pet==null) throw new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다."); result.put("pet",petDto(pet)); } Map<String,Object> snapshot=snapshots.get(snapshotId); if(snapshot==null) throw new IllegalStateException("Subscription snapshot을 찾을 수 없습니다."); result.put("currentSnapshot",snapshotDto(snapshot,snapshotItems.getOrDefault(snapshotId,List.of()))); result.put("nextScheduledDate","ACTIVE".equals(sub.get("status"))?schedules.get(id):null); return result; }
	private Map<String,Object> scheduleDto(Map<String,Object> row) { Map<String,Object> result=new LinkedHashMap<>(); result.put("scheduleId",longValue(row,"id")); result.put("scheduledDate",row.get("scheduled_date")); result.put("status",row.get("status")); result.put("effectiveSnapshotId",row.get("effective_snapshot_id")==null?null:longValue(row,"effective_snapshot_id")); return result; }
	private Map<String,Object> snapshotDto(long id) { Map<String,Object> s=one("SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM subscription_snapshots WHERE id=?",id).orElseThrow(); List<Map<String,Object>> items=jdbc.queryForList("SELECT sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id=? ORDER BY sku_id",id); return snapshotDto(s,items); }
	private Map<String,Object> snapshotDto(Map<String,Object> snapshot,List<Map<String,Object>> items) { return Map.of("planVersionId",longValue(snapshot,"source_plan_version_id"),"packagePriceKrw",longValue(snapshot,"package_total_krw"),"deliveryCycleWeeks",intValue(snapshot,"delivery_cycle_weeks"),"items",items.stream().map(i -> Map.<String,Object>of("skuId",longValue(i,"sku_id"),"quantity",intValue(i,"quantity"))).toList()); }
	private long snapshot(long subscriptionId,long versionId,int cycle,long price) { jdbc.update("INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,?,?)",subscriptionId,versionId,price,cycle); long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); jdbc.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT ?,sku_id,quantity FROM plan_items WHERE plan_version_id=?",id,versionId); return id; }
	private Map<String,Object> ownedPet(long member,long id) { return one("SELECT id,name,pet_type FROM pets WHERE id=? AND member_id=?",id,member).orElseThrow(() -> new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다.")); }
	private Map<String,Object> ownedSubscription(long member,long id) { return one("SELECT * FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true",id,member).orElseThrow(() -> new V2ApiException(404,"SUBSCRIPTION_NOT_FOUND","Subscription을 찾을 수 없습니다.")); }
	private Map<String,Object> lockedOwnedSubscription(long member,long id) { return one("SELECT * FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true FOR UPDATE",id,member).orElseThrow(() -> new V2ApiException(404,"SUBSCRIPTION_NOT_FOUND","Subscription을 찾을 수 없습니다.")); }
	private Map<String,Object> nextScheduled(long id) { return one("SELECT schedule.id,schedule.scheduled_date FROM subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE schedule.subscription_id=? AND schedule.status='SCHEDULED' AND existing_order.id IS NULL ORDER BY schedule.scheduled_date,schedule.id LIMIT 1 FOR UPDATE",id).orElseThrow(() -> new V2ApiException(409,"SUBSCRIPTION_COMMAND_NOT_ALLOWED","다음 Schedule이 없습니다.")); }
	private long firstSku(long versionId) { return jdbc.queryForObject("SELECT sku_id FROM plan_items WHERE plan_version_id=? ORDER BY sku_id LIMIT 1",Long.class,versionId); }
	private List<Map<String,Object>> planDtos(List<Map<String,Object>> rows) { List<Long> versionIds=ids(rows,"version_id",false); Map<Long,List<Map<String,Object>>> items=groupedRows("SELECT plan_version_id,sku_id,quantity FROM plan_items WHERE plan_version_id IN ",versionIds,"plan_version_id","sku_id"); Map<Long,List<Integer>> cycles=groupedIntegers("SELECT plan_version_id,delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id IN ",versionIds,"plan_version_id","delivery_cycle_weeks"); return rows.stream().map(v -> planDto(v,items.getOrDefault(longValue(v,"version_id"),List.of()),cycles.getOrDefault(longValue(v,"version_id"),List.of()))).toList(); }
	private Map<String,Object> planDto(Map<String,Object> v) { long id=longValue(v,"version_id"); List<Map<String,Object>> items=jdbc.queryForList("SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=? ORDER BY sku_id",id); List<Integer> cycles=jdbc.queryForList("SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=? ORDER BY delivery_cycle_weeks",Integer.class,id); return planDto(v,items,cycles); }
	private Map<String,Object> planDto(Map<String,Object> v,List<Map<String,Object>> itemRows,List<Integer> cycles) { long id=longValue(v,"version_id"); List<Map<String,Object>> items=itemRows.stream().map(i -> Map.<String,Object>of("skuId",longValue(i,"sku_id"),"quantity",intValue(i,"quantity"))).toList(); Map<String,Object> sale=new LinkedHashMap<>(); sale.put("onSale",v.get("on_sale")); sale.put("startsOn",v.get("sale_starts_on")); sale.put("endsOn",v.get("sale_ends_on")); Map<String,Object> result=new LinkedHashMap<>(); result.put("planId",longValue(v,"plan_id")); result.put("planName",v.get("plan_name")); result.put("targetPetType",v.get("target_pet_type")); result.put("planVersionId",id); result.put("packagePriceKrw",longValue(v,"package_price_krw")); result.put("items",items); result.put("allowedDeliveryCycleWeeks",cycles); result.put("sale",sale); return result; }
	private List<Long> ids(List<Map<String,Object>> rows,String column,boolean nullable) { return rows.stream().map(row -> row.get(column)).filter(value -> !nullable || value!=null).filter(java.util.Objects::nonNull).map(value -> ((Number)value).longValue()).distinct().toList(); }
	private Map<Long,Map<String,Object>> rowsById(String prefix,List<Long> ids,Object...leading) { if(ids.isEmpty()) return Map.of(); List<Object> args=new ArrayList<>(List.of(leading)); args.addAll(ids); Map<Long,Map<String,Object>> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList(prefix+placeholders(ids.size()),args.toArray())) result.put(longValue(row,"id"),row); return result; }
	private Map<Long,List<Map<String,Object>>> groupedRows(String prefix,List<Long> ids,String key,String order) { if(ids.isEmpty()) return Map.of(); Map<Long,List<Map<String,Object>>> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList(prefix+placeholders(ids.size())+" ORDER BY "+key+","+order,ids.toArray())) result.computeIfAbsent(longValue(row,key),ignored -> new ArrayList<>()).add(row); return result; }
	private Map<Long,List<Integer>> groupedIntegers(String prefix,List<Long> ids,String key,String value) { if(ids.isEmpty()) return Map.of(); Map<Long,List<Integer>> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList(prefix+placeholders(ids.size())+" ORDER BY "+key+","+value,ids.toArray())) result.computeIfAbsent(longValue(row,key),ignored -> new ArrayList<>()).add(intValue(row,value)); return result; }
	private Map<Long,LocalDate> nextSchedules(List<Long> ids,LocalDate today) { if(ids.isEmpty()) return Map.of(); Map<Long,LocalDate> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList("SELECT schedule.subscription_id,schedule.scheduled_date FROM subscription_schedules schedule LEFT JOIN subscription_orders existing_order ON existing_order.schedule_id=schedule.id WHERE schedule.subscription_id IN "+placeholders(ids.size())+" AND schedule.status='SCHEDULED' AND schedule.scheduled_date>=? AND existing_order.id IS NULL ORDER BY schedule.subscription_id,schedule.scheduled_date,schedule.id",withLast(ids,today))) result.putIfAbsent(longValue(row,"subscription_id"),((java.sql.Date)row.get("scheduled_date")).toLocalDate()); return result; }
	private Object[] withLast(List<Long> ids,Object last) { List<Object> args=new ArrayList<>(ids); args.add(last); return args.toArray(); }
	private String placeholders(int count) { return "("+String.join(",",Collections.nCopies(count,"?"))+")"; }
	private Map<String,Object> petDto(Map<String,Object> p) { return Map.of("petId",longValue(p,"id"),"name",p.get("name"),"petType",p.get("pet_type")); }
	private Optional<Map<String,Object>> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());}
	private long longValue(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
	private int intValue(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
	private Page page(int page, int size) { return new Page(page, size, page * size); }
	private Map<String,Object> pageResult(Page page,long total,List<Map<String,Object>> items){return Map.of("page",page.page(),"size",page.size(),"totalElements",total,"items",items);}
	private LocalDate today(){return LocalDate.now(clock.withZone(SEOUL));}
	private record Page(int page, int size, int offset) {}
}
