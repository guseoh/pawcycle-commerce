package com.pawcycle.backend.subscription.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class V2SubscriptionQueryApplicationService {
	private final V2SubscriptionJdbcStore store;
	private final V2SubscriptionApplicationSupport support;

	V2SubscriptionQueryApplicationService(V2SubscriptionJdbcStore store, tools.jackson.databind.ObjectMapper json, java.time.Clock clock) { this.store = store; this.support = new V2SubscriptionApplicationSupport(json, clock); }

	@Transactional(readOnly = true)
	Map<String, Object> subscriptions(long memberId, int page, int size) {
		V2SubscriptionData.Page<V2SubscriptionData.Subscription> subscriptions=store.findSubscriptions(memberId,page(page,size),size);
		List<Long> subscriptionIds=subscriptions.items().stream().map(V2SubscriptionData.Subscription::id).toList();
		Map<Long,V2SubscriptionData.Pet> pets=store.findOwnedPets(memberId,subscriptions.items().stream().map(V2SubscriptionData.Subscription::petId).filter(java.util.Objects::nonNull).toList());
		Map<Long,V2SubscriptionData.SnapshotBase> snapshots=store.findSnapshots(subscriptions.items().stream().map(V2SubscriptionData.Subscription::currentSnapshotId).toList());
		Map<Long,List<V2SubscriptionData.Item>> items=store.findSnapshotItems(snapshots.keySet().stream().toList());
		Map<Long,java.time.LocalDate> nextSchedules=store.findNextSchedules(subscriptionIds,support.today());
		return page(subscriptions,subscriptions.items().stream().map(subscription -> summary(subscription,pets.get(subscription.petId()),snapshot(snapshots.get(subscription.currentSnapshotId()),items.getOrDefault(subscription.currentSnapshotId(),List.of())),nextSchedules.get(subscription.id()))).toList());
	}

	@Transactional(readOnly = true)
	V2SubscriptionOperationResult subscription(long memberId, long subscriptionId, int schedulePage, int scheduleSize, int commandPage, int commandSize) {
		page(schedulePage, scheduleSize); page(commandPage, commandSize);
		Map<String, Object> body = detailBody(memberId, subscriptionId, schedulePage, scheduleSize, commandPage, commandSize);
		return new V2SubscriptionOperationResult(200, body, null, "\"" + body.get("version") + "\"", false);
	}

	Map<String, Object> detailBody(long memberId, long subscriptionId, int schedulePage, int scheduleSize, int commandPage, int commandSize) {
		int checkedSchedulePage=page(schedulePage,scheduleSize); int checkedCommandPage=page(commandPage,commandSize);
		V2SubscriptionData.Subscription subscription=store.findOwnedSubscription(memberId,subscriptionId);
		V2SubscriptionData.Pet pet=subscription.petId()==null?null:store.findOwnedPet(memberId,subscription.petId());
		V2SubscriptionData.Snapshot current=store.findSnapshot(subscription.currentSnapshotId());
		Optional<Long> pendingSnapshotId=store.findPendingSnapshotId(subscriptionId);
		V2SubscriptionData.Page<V2SubscriptionData.ScheduleView> schedules=store.findScheduleViews(subscriptionId,checkedSchedulePage,scheduleSize);
		V2SubscriptionData.Page<V2SubscriptionData.CommandHistory> history=store.findCommandHistory(subscriptionId,checkedCommandPage,commandSize);
		Map<String,Object> result=new LinkedHashMap<>(summary(subscription,pet,snapshot(current),store.findNextSchedule(subscriptionId,support.today()).orElse(null)));
		result.put("pendingSnapshot",pendingSnapshotId.map(store::findSnapshot).map(this::snapshot).orElse(null));
		result.put("schedules",page(schedules,schedules.items().stream().map(this::schedule).toList()));
		result.put("commandHistory",page(history,history.items().stream().map(this::history).toList()));
		return result;
	}
	private Map<String,Object> summary(V2SubscriptionData.Subscription subscription,V2SubscriptionData.Pet pet,Map<String,Object> currentSnapshot,java.time.LocalDate nextScheduledDate){if(subscription.petId()!=null&&pet==null)throw new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다.");Map<String,Object> result=new LinkedHashMap<>();result.put("subscriptionId",subscription.id());result.put("status",subscription.status());result.put("version",subscription.version());result.put("pet",pet==null?null:pet(pet));result.put("currentSnapshot",currentSnapshot);result.put("nextScheduledDate","ACTIVE".equals(subscription.status())?nextScheduledDate:null);return result;}
	private Map<String,Object> pet(V2SubscriptionData.Pet value){return Map.of("petId",value.id(),"name",value.name(),"petType",value.petType());}
	private Map<String,Object> snapshot(V2SubscriptionData.SnapshotBase value,List<V2SubscriptionData.Item> items){if(value==null)throw new IllegalStateException("Subscription snapshot을 찾을 수 없습니다.");return Map.of("planVersionId",value.planVersionId(),"packagePriceKrw",value.packagePriceKrw(),"deliveryCycleWeeks",value.deliveryCycleWeeks(),"items",items.stream().map(item->Map.<String,Object>of("skuId",item.skuId(),"quantity",item.quantity())).toList());}
	private Map<String,Object> snapshot(V2SubscriptionData.Snapshot value){return snapshot(new V2SubscriptionData.SnapshotBase(value.id(),value.planVersionId(),value.packagePriceKrw(),value.deliveryCycleWeeks()),value.items());}
	private Map<String,Object> schedule(V2SubscriptionData.ScheduleView value){Map<String,Object> result=new LinkedHashMap<>();result.put("scheduleId",value.id());result.put("scheduledDate",value.scheduledDate());result.put("status",value.status());result.put("effectiveSnapshotId",value.effectiveSnapshotId());return result;}
	private Map<String,Object> history(V2SubscriptionData.CommandHistory value){return Map.of("commandType",value.commandType(),"result","SUCCEEDED","occurredAt",value.occurredAt());}
	private Map<String,Object> page(V2SubscriptionData.Page<?> value,List<Map<String,Object>> items){return Map.of("page",value.page(),"size",value.size(),"totalElements",value.total(),"items",items);}
	private int page(int page,int size){if(page<0||size<1||size>100||page>Integer.MAX_VALUE/size)throw support.validation("page");return page;}
}
