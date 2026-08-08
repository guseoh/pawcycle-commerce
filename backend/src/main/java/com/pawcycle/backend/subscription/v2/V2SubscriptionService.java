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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** V2 uses JDBC deliberately: its multi-table aggregate and compare-and-set writes stay explicit. */
@Service
public class V2SubscriptionService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final Clock clock;

	public V2SubscriptionService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	@Transactional
	public Map<String, Object> createPet(long memberId, Map<String, Object> body) {
		String name = requiredText(body, "name", 50);
		if (name.chars().anyMatch(Character::isISOControl)) throw validation("name");
		String type = requiredText(body, "petType", 3);
		if (!("DOG".equals(type) || "CAT".equals(type))) throw validation("petType");
		jdbc.update("INSERT INTO pets(member_id,name,pet_type) VALUES (?,?,?)", memberId, name, type);
		long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return pet(memberId, id);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> pets(long memberId, int page, int size) {
		Page pagination = page(page, size);
		long total = jdbc.queryForObject("SELECT COUNT(*) FROM pets WHERE member_id=?", Long.class, memberId);
		List<Map<String, Object>> items = jdbc.queryForList("SELECT id,name,pet_type FROM pets WHERE member_id=? ORDER BY id ASC LIMIT ? OFFSET ?", memberId, pagination.size(), pagination.offset());
		return pageResult(pagination, total, items.stream().map(this::petDto).toList());
	}

	@Transactional(readOnly = true)
	public Map<String, Object> pet(long memberId, long petId) { return petDto(ownedPet(memberId, petId)); }

	@Transactional(readOnly = true)
	public Map<String, Object> plans(long memberId, long petId, int page, int size) {
		Page pagination = page(page, size); Map<String, Object> pet = ownedPet(memberId, petId); LocalDate today = today();
		String where = " FROM subscription_plans p JOIN plan_versions v ON v.id=p.current_plan_version_id WHERE p.name IS NOT NULL AND p.target_pet_type=? AND p.on_sale=true AND v.is_migration_only=false AND (p.sale_starts_on IS NULL OR p.sale_starts_on<=?) AND (p.sale_ends_on IS NULL OR p.sale_ends_on>=?)";
		long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, pet.get("pet_type"), today, today);
		List<Map<String,Object>> rows = jdbc.queryForList("SELECT p.id plan_id,p.name plan_name,p.target_pet_type,p.on_sale,p.sale_starts_on,p.sale_ends_on,v.id version_id,v.package_price_krw" + where + " ORDER BY p.id ASC,v.id ASC LIMIT ? OFFSET ?", pet.get("pet_type"), today, today, pagination.size(), pagination.offset());
		return pageResult(pagination, total, planDtos(rows));
	}

	@Transactional(readOnly = true)
	public Map<String, Object> planVersion(long memberId, long petId, long versionId) {
		Map<String,Object> pet = ownedPet(memberId, petId);
		Map<String,Object> version = version(versionId);
		validateVersionAvailability(pet, version);
		return planDto(version);
	}

	@Transactional
	public V2Result createSubscription(long memberId, String key, Map<String, Object> body) {
		validateKey(key); String fingerprint = fingerprint(body);
		try { jdbc.update("INSERT INTO subscription_creation_idempotency_results(member_id,idempotency_key,payload_fingerprint) VALUES (?,?,?)", memberId,key,fingerprint); }
		catch (DuplicateKeyException duplicate) {
			Map<String,Object> row=one("SELECT payload_fingerprint,response_status,response_body,location_header,etag_header FROM subscription_creation_idempotency_results WHERE member_id=? AND idempotency_key=? FOR UPDATE",memberId,key).orElseThrow(() -> duplicate);
			StoredReplay replayed=replay(row,fingerprint);
			if(replayed.bodyChanged()) jdbc.update("UPDATE subscription_creation_idempotency_results SET response_body=? WHERE member_id=? AND idempotency_key=?",bodyJson(replayed.result().body()),memberId,key);
			return replayed.result();
		}
		long petId = requiredLong(body, "petId"); long versionId = requiredLong(body, "planVersionId"); int cycle = requiredInt(body, "deliveryCycleWeeks");
		Map<String,Object> pet = ownedPet(memberId, petId); Map<String,Object> version = availableVersion(pet, versionId, cycle);
		LocalDate created = today(); LocalDate next = created.plusWeeks(cycle);
		jdbc.update("INSERT INTO subscriptions(member_id,sku_id,quantity,delivery_cycle_weeks,created_date,next_order_date,pet_id,status,version,current_snapshot_id,legacy_api_visible,mvp2_managed) VALUES (?,?,?,?,?,?,?,'ACTIVE',0,NULL,false,true)", memberId, firstSku(versionId), 1, cycle, created, next, petId);
		long subscriptionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		long snapshotId = snapshot(subscriptionId, versionId, cycle, longValue(version, "package_price_krw"));
		jdbc.update("UPDATE subscriptions SET current_snapshot_id=? WHERE id=?", snapshotId, subscriptionId);
		jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) VALUES (?,?,'SCHEDULED',NULL)", subscriptionId, next);
		V2Result result = result(201, detail(memberId, subscriptionId, 0, 20, 0, 20), "/api/v2/subscriptions/" + subscriptionId, "\"0\"", false);
		jdbc.update("UPDATE subscription_creation_idempotency_results SET subscription_id=?,response_status=?,response_body=?,location_header=?,etag_header=? WHERE member_id=? AND idempotency_key=?",subscriptionId,result.status(),bodyJson(result.body()),result.location(),result.etag(),memberId,key);
		return result;
	}

	@Transactional(readOnly = true)
	public Map<String,Object> subscriptions(long memberId, int page, int size) {
		Page pagination = page(page,size); long total=jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions WHERE member_id=? AND mvp2_managed=true",Long.class,memberId);
		List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,status,version,next_order_date,pet_id,current_snapshot_id FROM subscriptions WHERE member_id=? AND mvp2_managed=true ORDER BY id DESC LIMIT ? OFFSET ?",memberId,pagination.size(),pagination.offset());
		return pageResult(pagination,total,subscriptionSummaryDtos(memberId,rows));
	}

	@Transactional(readOnly = true)
	public V2Result subscription(long memberId,long subscriptionId,int schedulePage,int scheduleSize,int commandPage,int commandSize) {
		Map<String,Object> body=detail(memberId,subscriptionId,schedulePage,scheduleSize,commandPage,commandSize);
		return result(200,body,null,"\""+body.get("version")+"\"",false);
	}

	@Transactional(noRollbackFor = ReconciliationVersionChangedException.class)
	public V2Result command(long memberId,long subscriptionId,String rawCommand,String key,String ifMatch,Map<String,Object> body) {
		String command = rawCommand.toUpperCase(Locale.ROOT).replace('-', '_'); if (!List.of("CHANGE_PLAN","SKIP_NEXT","PAUSE","RESUME","CANCEL").contains(command)) throw new V2ApiException(404,"SUBSCRIPTION_NOT_FOUND","Subscription을 찾을 수 없습니다.");
		validateKey(key); String fp=fingerprint(body);
		ownedSubscription(memberId,subscriptionId);
		try { jdbc.update("INSERT INTO subscription_command_idempotency_results(member_id,subscription_id,command_type,idempotency_key,payload_fingerprint) VALUES (?,?,?,?,?)",memberId,subscriptionId,command,key,fp); }
		catch(DuplicateKeyException duplicate) {
			Map<String,Object> row=one("SELECT payload_fingerprint,response_status,response_body,location_header,etag_header FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=? FOR UPDATE",memberId,subscriptionId,command,key).orElseThrow(() -> duplicate);
			StoredReplay replayed=replay(row,fp);
			if(replayed.bodyChanged()) jdbc.update("UPDATE subscription_command_idempotency_results SET response_body=? WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",bodyJson(replayed.result().body()),memberId,subscriptionId,command,key);
			return replayed.result();
		}
		long expected=parseEtag(ifMatch); Map<String,Object> subscription=lockedOwnedSubscription(memberId,subscriptionId);
		if(longValue(subscription,"version")!=expected) throw new V2ApiException(412,"SUBSCRIPTION_VERSION_MISMATCH","Subscription version이 일치하지 않습니다.");
		reconcile(subscription); subscription=lockedOwnedSubscription(memberId,subscriptionId);
		if(longValue(subscription,"version")!=expected) {
			jdbc.update("DELETE FROM subscription_command_idempotency_results WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",memberId,subscriptionId,command,key);
			throw new ReconciliationVersionChangedException();
		}
		switch(command) {
			case "CHANGE_PLAN" -> changePlan(memberId,subscription,body);
			case "SKIP_NEXT" -> skip(subscription);
			case "PAUSE" -> pause(subscription);
			case "RESUME" -> resume(subscription);
			case "CANCEL" -> cancel(subscription);
			default -> throw new IllegalStateException();
		}
		int updated=jdbc.update("UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",subscriptionId,expected);
		if(updated==0) throw new V2ApiException(412,"SUBSCRIPTION_VERSION_MISMATCH","Subscription version이 일치하지 않습니다.");
		jdbc.update("INSERT INTO subscription_command_history(subscription_id,command_type,occurred_at,version_before,version_after) VALUES (?,?,UTC_TIMESTAMP(6),?,?)",subscriptionId,command,expected,expected+1);
		Map<String,Object> response=detail(memberId,subscriptionId,0,20,0,20); V2Result outcome=result(200,response,null,"\""+(expected+1)+"\"",false);
		jdbc.update("UPDATE subscription_command_idempotency_results SET response_status=?,response_body=?,location_header=?,etag_header=? WHERE member_id=? AND subscription_id=? AND command_type=? AND idempotency_key=?",200,bodyJson(response),null,outcome.etag(),memberId,subscriptionId,command,key);
		return outcome;
	}

	@Transactional
	public void reconcileActiveSubscriptions() {
		List<Long> active = jdbc.queryForList("SELECT id FROM subscriptions WHERE mvp2_managed=true AND status='ACTIVE' ORDER BY id", Long.class);
		for (Long id : active) one("SELECT * FROM subscriptions WHERE id=? AND mvp2_managed=true AND status='ACTIVE' FOR UPDATE", id).ifPresent(this::reconcile);
	}

	private void changePlan(long memberId,Map<String,Object> sub,Map<String,Object> body) {
		if(!"ACTIVE".equals(sub.get("status"))) throw state(); long petId=sub.get("pet_id")==null?requiredLong(body,"petId"):longValue(sub,"pet_id"); if(body.containsKey("petId")&&petId!=requiredLong(body,"petId")) throw new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다.");
		Map<String,Object> pet=ownedPet(memberId,petId); int cycle=intValue(sub,"delivery_cycle_weeks"); long versionId=requiredLong(body,"planVersionId"); Map<String,Object> version=availableVersion(pet,versionId,cycle);
		long snapshot=snapshot(longValue(sub,"id"),versionId,cycle,longValue(version,"package_price_krw")); Map<String,Object> schedule=nextScheduled(longValue(sub,"id"));
		jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?",sub.get("id")); jdbc.update("INSERT INTO pending_plan_changes(subscription_id,snapshot_id,target_schedule_id) VALUES (?,?,?)",sub.get("id"),snapshot,schedule.get("id"));
		if(sub.get("pet_id")==null) jdbc.update("UPDATE subscriptions SET pet_id=? WHERE id=?",petId,sub.get("id"));
	}
	private void skip(Map<String,Object> sub) { if(!"ACTIVE".equals(sub.get("status"))) throw state(); Map<String,Object> schedule=nextScheduled(longValue(sub,"id")); jdbc.update("UPDATE subscription_schedules SET status='SKIPPED' WHERE id=?",schedule.get("id")); LocalDate date=((java.sql.Date)schedule.get("scheduled_date")).toLocalDate().plusWeeks(intValue(sub,"delivery_cycle_weeks")); jdbc.update("INSERT INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) VALUES (?,?,'SCHEDULED',NULL)",sub.get("id"),date); long next=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); jdbc.update("UPDATE pending_plan_changes SET target_schedule_id=? WHERE subscription_id=?",next,sub.get("id")); }
	private void pause(Map<String,Object> sub) { if(!"ACTIVE".equals(sub.get("status"))) throw state(); jdbc.update("UPDATE subscriptions SET status='PAUSED' WHERE id=?",sub.get("id")); jdbc.update("UPDATE subscription_schedules SET status='HELD' WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>=?",sub.get("id"),today()); }
	private void resume(Map<String,Object> sub) {
		if(!"PAUSED".equals(sub.get("status"))) throw state();
		long subscriptionId=longValue(sub,"id"); int cycle=intValue(sub,"delivery_cycle_weeks"); LocalDate candidate=today().plusWeeks(cycle);
		List<Long> heldIds=jdbc.queryForList("SELECT id FROM subscription_schedules WHERE subscription_id=? AND status='HELD' ORDER BY scheduled_date,id",Long.class,subscriptionId);
		for(Long heldId:heldIds) {
			while(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=? AND scheduled_date=? AND id<>?",Integer.class,subscriptionId,candidate,heldId)>0) candidate=candidate.plusWeeks(cycle);
			jdbc.update("UPDATE subscription_schedules SET scheduled_date=?,status='SCHEDULED' WHERE id=?",candidate,heldId);
			candidate=candidate.plusWeeks(cycle);
		}
		jdbc.update("UPDATE subscriptions SET status='ACTIVE' WHERE id=?",subscriptionId);
	}
	private void cancel(Map<String,Object> sub) { if(!List.of("ACTIVE","PAUSED").contains(sub.get("status"))) throw state(); jdbc.update("UPDATE subscriptions SET status='CANCELED' WHERE id=?",sub.get("id")); jdbc.update("UPDATE subscription_schedules SET status='CANCELED' WHERE subscription_id=? AND scheduled_date>=?",sub.get("id"),today()); jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?",sub.get("id")); }

	private void reconcile(Map<String,Object> sub) { if(!"ACTIVE".equals(sub.get("status"))) return; long id=longValue(sub,"id"); List<Map<String,Object>> overdue=jdbc.queryForList("SELECT id,scheduled_date FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED' AND effective_snapshot_id IS NULL AND scheduled_date<? ORDER BY scheduled_date,id",id,today()); boolean changed=false; for(Map<String,Object> schedule:overdue) { long scheduleId=longValue(schedule,"id"); Optional<Map<String,Object>> pending=one("SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=? AND target_schedule_id=?",id,scheduleId); long effective=longValue(sub,"current_snapshot_id"); if(pending.isPresent()) { effective=longValue(pending.get(),"snapshot_id"); jdbc.update("UPDATE subscriptions SET current_snapshot_id=? WHERE id=?",effective,id); jdbc.update("DELETE FROM pending_plan_changes WHERE subscription_id=?",id); sub.put("current_snapshot_id",effective); } jdbc.update("UPDATE subscription_schedules SET effective_snapshot_id=? WHERE id=? AND effective_snapshot_id IS NULL",effective,scheduleId); int cycle=jdbc.queryForObject("SELECT delivery_cycle_weeks FROM subscription_snapshots WHERE id=?",Integer.class,effective); LocalDate next=((java.sql.Date)schedule.get("scheduled_date")).toLocalDate(); do { next=next.plusWeeks(cycle); } while(!next.isAfter(today())); jdbc.update("INSERT IGNORE INTO subscription_schedules(subscription_id,scheduled_date,status,effective_snapshot_id) VALUES (?,?,'SCHEDULED',NULL)",id,next); changed=true; } if(changed) { long version=longValue(sub,"version"); jdbc.update("UPDATE subscriptions SET version=version+1 WHERE id=? AND version=?",id,version); sub.put("version",version+1); } }

	private Map<String,Object> detail(long memberId,long id,int schedulePage,int scheduleSize,int commandPage,int commandSize) { Page sp=page(schedulePage,scheduleSize), cp=page(commandPage,commandSize); Map<String,Object> sub=ownedSubscription(memberId,id); Map<String,Object> result=subscriptionSummaryDto(memberId,sub); Optional<Map<String,Object>> pending=one("SELECT snapshot_id FROM pending_plan_changes WHERE subscription_id=?",id); result.put("pendingSnapshot",pending.map(p -> snapshotDto(longValue(p,"snapshot_id"))).orElse(null)); long st=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_schedules WHERE subscription_id=?",Long.class,id); List<Map<String,Object>> schedules=jdbc.queryForList("SELECT id,scheduled_date,status,effective_snapshot_id FROM subscription_schedules WHERE subscription_id=? ORDER BY scheduled_date DESC,id DESC LIMIT ? OFFSET ?",id,sp.size(),sp.offset()).stream().map(this::scheduleDto).toList(); result.put("schedules",pageResult(sp,st,schedules)); long ct=jdbc.queryForObject("SELECT COUNT(*) FROM subscription_command_history WHERE subscription_id=?",Long.class,id); List<Map<String,Object>> history=jdbc.query("SELECT command_type,occurred_at FROM subscription_command_history WHERE subscription_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",(rs,rowNum) -> Map.<String,Object>of("commandType",rs.getString("command_type"),"result","SUCCEEDED","occurredAt",rs.getTimestamp("occurred_at").toInstant().atZone(SEOUL).toOffsetDateTime().toString()),id,cp.size(),cp.offset()); result.put("commandHistory",pageResult(cp,ct,history)); return result; }
	private Map<String,Object> subscriptionSummaryDto(long memberId,Map<String,Object> sub) { Map<String,Object> result=new LinkedHashMap<>(); result.put("subscriptionId",longValue(sub,"id")); result.put("status",sub.get("status")); result.put("version",longValue(sub,"version")); result.put("pet",sub.get("pet_id")==null?null:petDto(ownedPet(memberId,longValue(sub,"pet_id")))); result.put("currentSnapshot",snapshotDto(longValue(sub,"current_snapshot_id"))); result.put("nextScheduledDate", "ACTIVE".equals(sub.get("status")) ? jdbc.query("SELECT scheduled_date FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>=? ORDER BY scheduled_date,id LIMIT 1", rs -> rs.next()?rs.getDate(1).toLocalDate():null,longValue(sub,"id"),today()) : null); return result; }
	private List<Map<String,Object>> subscriptionSummaryDtos(long memberId,List<Map<String,Object>> rows) { List<Long> ids=ids(rows,"id",false); Map<Long,Map<String,Object>> pets=rowsById("SELECT id,name,pet_type FROM pets WHERE member_id=? AND id IN ",ids(rows,"pet_id",true),memberId); Map<Long,Map<String,Object>> snapshots=rowsById("SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM subscription_snapshots WHERE id IN ",ids(rows,"current_snapshot_id",false)); Map<Long,List<Map<String,Object>>> snapshotItems=groupedRows("SELECT snapshot_id,sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id IN ",ids(rows,"current_snapshot_id",false),"snapshot_id","sku_id"); Map<Long,LocalDate> schedules=nextSchedules(ids,today()); return rows.stream().map(sub -> subscriptionSummaryDto(memberId,sub,pets,snapshots,snapshotItems,schedules)).toList(); }
	private Map<String,Object> subscriptionSummaryDto(long memberId,Map<String,Object> sub,Map<Long,Map<String,Object>> pets,Map<Long,Map<String,Object>> snapshots,Map<Long,List<Map<String,Object>>> snapshotItems,Map<Long,LocalDate> schedules) { long id=longValue(sub,"id"), snapshotId=longValue(sub,"current_snapshot_id"); Map<String,Object> result=new LinkedHashMap<>(); result.put("subscriptionId",id); result.put("status",sub.get("status")); result.put("version",longValue(sub,"version")); if(sub.get("pet_id")==null) result.put("pet",null); else { Map<String,Object> pet=pets.get(longValue(sub,"pet_id")); if(pet==null) throw new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다."); result.put("pet",petDto(pet)); } Map<String,Object> snapshot=snapshots.get(snapshotId); if(snapshot==null) throw new IllegalStateException("Subscription snapshot을 찾을 수 없습니다."); result.put("currentSnapshot",snapshotDto(snapshot,snapshotItems.getOrDefault(snapshotId,List.of()))); result.put("nextScheduledDate","ACTIVE".equals(sub.get("status"))?schedules.get(id):null); return result; }
	private Map<String,Object> scheduleDto(Map<String,Object> row) { Map<String,Object> result=new LinkedHashMap<>(); result.put("scheduleId",longValue(row,"id")); result.put("scheduledDate",row.get("scheduled_date")); result.put("status",row.get("status")); result.put("effectiveSnapshotId",row.get("effective_snapshot_id")==null?null:longValue(row,"effective_snapshot_id")); return result; }
	private Map<String,Object> snapshotDto(long id) { Map<String,Object> s=one("SELECT id,source_plan_version_id,package_total_krw,delivery_cycle_weeks FROM subscription_snapshots WHERE id=?",id).orElseThrow(); List<Map<String,Object>> items=jdbc.queryForList("SELECT sku_id,quantity FROM subscription_snapshot_items WHERE snapshot_id=? ORDER BY sku_id",id); return snapshotDto(s,items); }
	private Map<String,Object> snapshotDto(Map<String,Object> snapshot,List<Map<String,Object>> items) { return Map.of("planVersionId",longValue(snapshot,"source_plan_version_id"),"packagePriceKrw",longValue(snapshot,"package_total_krw"),"deliveryCycleWeeks",intValue(snapshot,"delivery_cycle_weeks"),"items",items.stream().map(i -> Map.<String,Object>of("skuId",longValue(i,"sku_id"),"quantity",intValue(i,"quantity"))).toList()); }
	private long snapshot(long subscriptionId,long versionId,int cycle,long price) { jdbc.update("INSERT INTO subscription_snapshots(subscription_id,source_plan_version_id,package_total_krw,delivery_cycle_weeks) VALUES (?,?,?,?)",subscriptionId,versionId,price,cycle); long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); jdbc.update("INSERT INTO subscription_snapshot_items(snapshot_id,sku_id,quantity) SELECT ?,sku_id,quantity FROM plan_items WHERE plan_version_id=?",id,versionId); return id; }
	private Map<String,Object> availableVersion(Map<String,Object> pet,long versionId,int cycle) { Map<String,Object> version=version(versionId); validateVersionAvailability(pet,version); Integer allowed=jdbc.queryForObject("SELECT COUNT(*) FROM plan_version_delivery_cycles WHERE plan_version_id=? AND delivery_cycle_weeks=?",Integer.class,versionId,cycle); if(allowed==0) throw new V2ApiException(409,"DELIVERY_CYCLE_NOT_ALLOWED","허용되지 않은 배송 주기입니다."); return version; }
	private Map<String,Object> version(long versionId) { return one("SELECT p.id plan_id,p.name plan_name,p.current_plan_version_id,p.target_pet_type,p.on_sale,p.sale_starts_on,p.sale_ends_on,v.id version_id,v.package_price_krw,v.is_migration_only FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE v.id=?",versionId).orElseThrow(() -> new V2ApiException(404,"PLAN_VERSION_NOT_FOUND","PlanVersion을 찾을 수 없습니다.")); }
	private void validateVersionAvailability(Map<String,Object> pet,Map<String,Object> version) { LocalDate today=today(); long versionId=longValue(version,"version_id"); if(!pet.get("pet_type").equals(version.get("target_pet_type"))) throw new V2ApiException(409,"PLAN_PET_TYPE_MISMATCH","Pet 종과 Plan이 호환되지 않습니다."); Object current=version.get("current_plan_version_id"); if(!(current instanceof Number) || ((Number)current).longValue()!=versionId || version.get("plan_name")==null || !Boolean.TRUE.equals(version.get("on_sale")) || Boolean.TRUE.equals(version.get("is_migration_only")) || (version.get("sale_starts_on") != null && ((java.sql.Date)version.get("sale_starts_on")).toLocalDate().isAfter(today)) || (version.get("sale_ends_on") != null && ((java.sql.Date)version.get("sale_ends_on")).toLocalDate().isBefore(today))) throw new V2ApiException(409,"PLAN_NOT_AVAILABLE","판매 가능한 PlanVersion이 아닙니다."); }
	private Map<String,Object> ownedPet(long member,long id) { return one("SELECT id,name,pet_type FROM pets WHERE id=? AND member_id=?",id,member).orElseThrow(() -> new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다.")); }
	private Map<String,Object> ownedSubscription(long member,long id) { return one("SELECT * FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true",id,member).orElseThrow(() -> new V2ApiException(404,"SUBSCRIPTION_NOT_FOUND","Subscription을 찾을 수 없습니다.")); }
	private Map<String,Object> lockedOwnedSubscription(long member,long id) { return one("SELECT * FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true FOR UPDATE",id,member).orElseThrow(() -> new V2ApiException(404,"SUBSCRIPTION_NOT_FOUND","Subscription을 찾을 수 없습니다.")); }
	private Map<String,Object> nextScheduled(long id) { return one("SELECT id,scheduled_date FROM subscription_schedules WHERE subscription_id=? AND status='SCHEDULED' AND scheduled_date>=? ORDER BY scheduled_date,id LIMIT 1",id,today()).orElseThrow(() -> new V2ApiException(409,"SUBSCRIPTION_COMMAND_NOT_ALLOWED","다음 Schedule이 없습니다.")); }
	private long firstSku(long versionId) { return jdbc.queryForObject("SELECT sku_id FROM plan_items WHERE plan_version_id=? ORDER BY sku_id LIMIT 1",Long.class,versionId); }
	private List<Map<String,Object>> planDtos(List<Map<String,Object>> rows) { List<Long> versionIds=ids(rows,"version_id",false); Map<Long,List<Map<String,Object>>> items=groupedRows("SELECT plan_version_id,sku_id,quantity FROM plan_items WHERE plan_version_id IN ",versionIds,"plan_version_id","sku_id"); Map<Long,List<Integer>> cycles=groupedIntegers("SELECT plan_version_id,delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id IN ",versionIds,"plan_version_id","delivery_cycle_weeks"); return rows.stream().map(v -> planDto(v,items.getOrDefault(longValue(v,"version_id"),List.of()),cycles.getOrDefault(longValue(v,"version_id"),List.of()))).toList(); }
	private Map<String,Object> planDto(Map<String,Object> v) { long id=longValue(v,"version_id"); List<Map<String,Object>> items=jdbc.queryForList("SELECT sku_id,quantity FROM plan_items WHERE plan_version_id=? ORDER BY sku_id",id); List<Integer> cycles=jdbc.queryForList("SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=? ORDER BY delivery_cycle_weeks",Integer.class,id); return planDto(v,items,cycles); }
	private Map<String,Object> planDto(Map<String,Object> v,List<Map<String,Object>> itemRows,List<Integer> cycles) { long id=longValue(v,"version_id"); List<Map<String,Object>> items=itemRows.stream().map(i -> Map.<String,Object>of("skuId",longValue(i,"sku_id"),"quantity",intValue(i,"quantity"))).toList(); Map<String,Object> sale=new LinkedHashMap<>(); sale.put("onSale",v.get("on_sale")); sale.put("startsOn",v.get("sale_starts_on")); sale.put("endsOn",v.get("sale_ends_on")); Map<String,Object> result=new LinkedHashMap<>(); result.put("planId",longValue(v,"plan_id")); result.put("planName",v.get("plan_name")); result.put("targetPetType",v.get("target_pet_type")); result.put("planVersionId",id); result.put("packagePriceKrw",longValue(v,"package_price_krw")); result.put("items",items); result.put("allowedDeliveryCycleWeeks",cycles); result.put("sale",sale); return result; }
	private List<Long> ids(List<Map<String,Object>> rows,String column,boolean nullable) { return rows.stream().map(row -> row.get(column)).filter(value -> !nullable || value!=null).filter(java.util.Objects::nonNull).map(value -> ((Number)value).longValue()).distinct().toList(); }
	private Map<Long,Map<String,Object>> rowsById(String prefix,List<Long> ids,Object...leading) { if(ids.isEmpty()) return Map.of(); List<Object> args=new ArrayList<>(List.of(leading)); args.addAll(ids); Map<Long,Map<String,Object>> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList(prefix+placeholders(ids.size()),args.toArray())) result.put(longValue(row,"id"),row); return result; }
	private Map<Long,List<Map<String,Object>>> groupedRows(String prefix,List<Long> ids,String key,String order) { if(ids.isEmpty()) return Map.of(); Map<Long,List<Map<String,Object>>> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList(prefix+placeholders(ids.size())+" ORDER BY "+key+","+order,ids.toArray())) result.computeIfAbsent(longValue(row,key),ignored -> new ArrayList<>()).add(row); return result; }
	private Map<Long,List<Integer>> groupedIntegers(String prefix,List<Long> ids,String key,String value) { if(ids.isEmpty()) return Map.of(); Map<Long,List<Integer>> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList(prefix+placeholders(ids.size())+" ORDER BY "+key+","+value,ids.toArray())) result.computeIfAbsent(longValue(row,key),ignored -> new ArrayList<>()).add(intValue(row,value)); return result; }
	private Map<Long,LocalDate> nextSchedules(List<Long> ids,LocalDate today) { if(ids.isEmpty()) return Map.of(); Map<Long,LocalDate> result=new HashMap<>(); for(Map<String,Object> row:jdbc.queryForList("SELECT subscription_id,scheduled_date FROM subscription_schedules WHERE subscription_id IN "+placeholders(ids.size())+" AND status='SCHEDULED' AND scheduled_date>=? ORDER BY subscription_id,scheduled_date,id",withLast(ids,today))) result.putIfAbsent(longValue(row,"subscription_id"),((java.sql.Date)row.get("scheduled_date")).toLocalDate()); return result; }
	private Object[] withLast(List<Long> ids,Object last) { List<Object> args=new ArrayList<>(ids); args.add(last); return args.toArray(); }
	private String placeholders(int count) { return "("+String.join(",",Collections.nCopies(count,"?"))+")"; }
	private Map<String,Object> petDto(Map<String,Object> p) { return Map.of("petId",longValue(p,"id"),"name",p.get("name"),"petType",p.get("pet_type")); }
	private StoredReplay replay(Map<String,Object> row,String fingerprint) { if(!fingerprint.equals(row.get("payload_fingerprint"))) throw new V2ApiException(409,"IDEMPOTENCY_KEY_REUSED","동일 key에 다른 요청 본문을 사용할 수 없습니다."); try { @SuppressWarnings("unchecked") Map<String,Object> body=json.readValue((String)row.get("response_body"),Map.class); boolean changed=removeInternalSnapshotId(body.get("currentSnapshot"))|removeInternalSnapshotId(body.get("pendingSnapshot")); return new StoredReplay(result(intValue(row,"response_status"),body,(String)row.get("location_header"),(String)row.get("etag_header"),true),changed); } catch(Exception e) { throw new IllegalStateException("저장된 멱등 결과를 읽을 수 없습니다.",e); } }
	private boolean removeInternalSnapshotId(Object snapshot) { if(!(snapshot instanceof Map<?,?> map)||!map.containsKey("snapshotId")) return false; map.remove("snapshotId"); return true; }
	private V2Result result(int status,Map<String,Object> body,String location,String etag,boolean replay) { return new V2Result(status,body,location,etag,replay); }
	private String bodyJson(Map<String,Object> body) { try { return json.writeValueAsString(body); } catch(Exception e) { throw new IllegalStateException(e); } }
	private String fingerprint(Map<String,Object> body) { try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(body))); StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format("%02x",b)); return out.toString(); } catch(Exception e) { throw new IllegalStateException(e); } }
	private long parseEtag(String value) { if(value==null) throw new V2ApiException(428,"IF_MATCH_REQUIRED","If-Match가 필요합니다."); if(!value.matches("\\\"[0-9]+\\\"")) throw new V2ApiException(400,"IF_MATCH_INVALID","If-Match 형식이 올바르지 않습니다."); try{return Long.parseLong(value.substring(1,value.length()-1));}catch(NumberFormatException e){throw new V2ApiException(400,"IF_MATCH_INVALID","If-Match 형식이 올바르지 않습니다.",e);} }
	private void validateKey(String key) { if(key==null||!key.matches("[A-Za-z0-9._-]{1,128}")) throw validation("Idempotency-Key"); }
	private V2ApiException validation(String field) { return new V2ApiException(400,"VALIDATION_FAILED",field+" 값을 확인해 주세요."); }
	private V2ApiException state() { return new V2ApiException(409,"SUBSCRIPTION_COMMAND_NOT_ALLOWED","현재 Subscription 상태에서는 명령을 실행할 수 없습니다."); }
	private String requiredText(Map<String,Object>b,String key,int max){Object v=b.get(key);if(!(v instanceof String s))throw validation(key);s=s.trim();if(s.isBlank()||s.codePointCount(0,s.length())>max)throw validation(key);return s;}
	private long requiredLong(Map<String,Object>b,String key){Object value=b.get(key);if(!(value instanceof Number number))throw validation(key);try{return new BigDecimal(number.toString()).longValueExact();}catch(NumberFormatException|ArithmeticException exception){throw validation(key);}}
	private int requiredInt(Map<String,Object>b,String key){long n=requiredLong(b,key);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw validation(key);return(int)n;}
	private Page page(int p,int s){if(p<0||s<1||s>100||p>Integer.MAX_VALUE/s)throw validation("page");return new Page(p,s,p*s);}
	private Object canonical(Object value) { if(value instanceof Map<?,?> map) { Map<String,Object> sorted=new TreeMap<>(); map.forEach((key,item) -> sorted.put(String.valueOf(key),canonical(item))); return sorted; } if(value instanceof List<?> list) return list.stream().map(this::canonical).toList(); if(value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros(); return value; }
	private Map<String,Object> pageResult(Page p,long total,List<Map<String,Object>> items){return Map.of("page",p.page(),"size",p.size(),"totalElements",total,"items",items);}
	private Optional<Map<String,Object>> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?Optional.empty():Optional.of(rows.getFirst());}
	private long longValue(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}
	private int intValue(Map<String,Object> r,String k){return ((Number)r.get(k)).intValue();}
	private LocalDate today(){return LocalDate.now(clock.withZone(SEOUL));}
	private record Page(int page,int size,int offset) {}
	private record StoredReplay(V2Result result,boolean bodyChanged) {}
	private static final class ReconciliationVersionChangedException extends V2ApiException { private ReconciliationVersionChangedException() { super(412,"SUBSCRIPTION_VERSION_MISMATCH","Subscription version이 reconciliation으로 변경되었습니다."); } }
	public record V2Result(int status,Map<String,Object> body,String location,String etag,boolean replay) { ResponseEntity<Map<String,Object>> response(){ResponseEntity.BodyBuilder b=ResponseEntity.status(status);if(location!=null)b.header("Location",location);if(etag!=null)b.header("ETag",etag);if(replay)b.header("Idempotency-Replayed","true");return b.body(body);} }
}
