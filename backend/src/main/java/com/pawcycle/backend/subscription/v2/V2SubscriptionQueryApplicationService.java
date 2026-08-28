package com.pawcycle.backend.subscription.v2;

import java.util.ArrayList;
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
		Optional<V2SubscriptionData.PendingChange> pendingChange=store.findPendingChange(subscriptionId);
		V2SubscriptionData.Snapshot pending=pendingChange.map(V2SubscriptionData.PendingChange::snapshotId).map(store::findSnapshot).orElse(null);
		V2SubscriptionData.NextDeliverySchedule nextDelivery="ACTIVE".equals(subscription.status())?store.findNextDeliverySchedule(subscriptionId).orElse(null):null;
		V2SubscriptionData.Page<V2SubscriptionData.ScheduleView> schedules=store.findScheduleViews(subscriptionId,checkedSchedulePage,scheduleSize);
		V2SubscriptionData.Page<V2SubscriptionData.CommandHistory> history=store.findCommandHistory(subscriptionId,checkedCommandPage,commandSize);
		Map<String,Object> result=new LinkedHashMap<>(summary(subscription,pet,snapshot(current),store.findNextSchedule(subscriptionId,support.today()).orElse(null)));
		result.put("pendingSnapshot",pending==null?null:snapshot(pending));
		result.put("nextDelivery",nextDelivery==null?null:nextDelivery(nextDelivery,current,pendingChange.orElse(null),pending));
		result.put("pendingChange",pendingChange.map(change->pendingChange(change,pending)).orElse(null));
		result.put("issue",nextDelivery==null?null:issue(nextDelivery.holdReason()));
		result.put("availableActions",availableActions(subscription,nextDelivery));
		result.put("schedules",page(schedules,schedules.items().stream().map(this::schedule).toList()));
		result.put("commandHistory",page(history,history.items().stream().map(this::history).toList()));
		return result;
	}
	private Map<String,Object> summary(V2SubscriptionData.Subscription subscription,V2SubscriptionData.Pet pet,Map<String,Object> currentSnapshot,java.time.LocalDate nextScheduledDate){if(subscription.petId()!=null&&pet==null)throw new V2ApiException(404,"PET_NOT_FOUND","Pet을 찾을 수 없습니다.");Map<String,Object> result=new LinkedHashMap<>();result.put("subscriptionId",subscription.id());result.put("status",subscription.status());result.put("version",subscription.version());result.put("pet",pet==null?null:pet(pet));result.put("currentSnapshot",currentSnapshot);result.put("nextScheduledDate","ACTIVE".equals(subscription.status())?nextScheduledDate:null);return result;}
	private Map<String,Object> pet(V2SubscriptionData.Pet value){Map<String,Object> result=new LinkedHashMap<>();result.put("petId",value.id());result.put("name",value.name());result.put("petType",value.petType());result.put("breed",value.breed());result.put("weightKg",value.weightKg());result.put("profileComplete",value.profileComplete());return result;}
	private Map<String,Object> snapshot(V2SubscriptionData.SnapshotBase value,List<V2SubscriptionData.Item> items){if(value==null)throw new IllegalStateException("Subscription snapshot을 찾을 수 없습니다.");return Map.of("planVersionId",value.planVersionId(),"packagePriceKrw",value.packagePriceKrw(),"deliveryCycleWeeks",value.deliveryCycleWeeks(),"items",items.stream().map(item->Map.<String,Object>of("skuId",item.skuId(),"quantity",item.quantity())).toList());}
	private Map<String,Object> snapshot(V2SubscriptionData.Snapshot value){return snapshot(new V2SubscriptionData.SnapshotBase(value.id(),value.planVersionId(),value.packagePriceKrw(),value.deliveryCycleWeeks()),value.items());}
	private Map<String,Object> nextDelivery(V2SubscriptionData.NextDeliverySchedule schedule,V2SubscriptionData.Snapshot current,V2SubscriptionData.PendingChange pendingChange,V2SubscriptionData.Snapshot pending){long snapshotId=schedule.effectiveSnapshotId()!=null?schedule.effectiveSnapshotId():pendingChange!=null&&pendingChange.targetScheduleId()==schedule.id()?pendingChange.snapshotId():current.id();V2SubscriptionData.Snapshot effective=snapshotId==current.id()?current:pending!=null&&snapshotId==pending.id()?pending:store.findSnapshot(snapshotId);Map<String,Object> result=new LinkedHashMap<>();result.put("scheduleId",schedule.id());result.put("scheduledDate",schedule.scheduledDate());result.put("status",schedule.status());addSnapshotSummary(result,effective);List<V2SubscriptionData.ScheduleAddon> addOns=store.findScheduleAddons(schedule.id());java.math.BigDecimal addOnTotal=addOns.stream().map(V2SubscriptionData.ScheduleAddon::lineAmount).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);result.put("addOns",addOns.stream().map(this::addon).toList());result.put("addOnTotalKrw",addOnTotal);result.put("orderTotalKrw",java.math.BigDecimal.valueOf(effective.packagePriceKrw()).add(addOnTotal));return result;}
	private Map<String,Object> addon(V2SubscriptionData.ScheduleAddon addOn){Map<String,Object> result=new LinkedHashMap<>();result.put("skuId",addOn.skuId());result.put("productId",addOn.productId());result.put("productName",addOn.productName());result.put("skuName",addOn.skuName());result.put("quantity",addOn.quantity());result.put("unitPriceKrw",addOn.unitPriceKrw());result.put("lineAmountKrw",addOn.lineAmount());return result;}
	private Map<String,Object> pendingChange(V2SubscriptionData.PendingChange change,V2SubscriptionData.Snapshot pending){Map<String,Object> result=new LinkedHashMap<>();result.put("targetScheduleId",change.targetScheduleId());result.put("appliesOn",change.targetScheduledDate());addSnapshotSummary(result,pending);return result;}
	private void addSnapshotSummary(Map<String,Object> result,V2SubscriptionData.Snapshot snapshot){result.put("planVersionId",snapshot.planVersionId());result.put("packagePriceKrw",snapshot.packagePriceKrw());result.put("deliveryCycleWeeks",snapshot.deliveryCycleWeeks());result.put("items",store.findSnapshotItemDetails(snapshot.id()).stream().map(this::itemDetail).toList());}
	private Map<String,Object> itemDetail(V2SubscriptionData.ItemDetail item){Map<String,Object> result=new LinkedHashMap<>();result.put("skuId",item.skuId());result.put("skuName",item.skuName());result.put("productId",item.productId());result.put("productName",item.productName());result.put("thumbnailUrl",item.thumbnailUrl());result.put("quantity",item.quantity());return result;}
	private Map<String,Object> issue(String holdReason){if(holdReason==null)return null;return switch(holdReason){case "MISSING_SHIPPING_ADDRESS"->Map.of("code","SHIPPING_ADDRESS_REQUIRED","message","배송지를 등록해 주세요.");case "MISSING_BILLING_METHOD"->Map.of("code","BILLING_METHOD_REQUIRED","message","결제 수단을 등록해 주세요.");case "PAYMENT_RETRY_EXHAUSTED"->Map.of("code","PAYMENT_SUPPORT_REQUIRED","message","결제를 완료하지 못했습니다. 고객 지원에 문의해 주세요.");case "PAYMENT_RETRY_STOCK_UNAVAILABLE","ORDER_STOCK_UNAVAILABLE"->Map.of("code","STOCK_UNAVAILABLE","message","재고를 확보하지 못해 배송이 보류되었습니다.");default->throw new IllegalStateException("알 수 없는 Subscription issue입니다.");};}
	private List<String> availableActions(V2SubscriptionData.Subscription subscription,V2SubscriptionData.NextDeliverySchedule nextDelivery){if("PAUSED".equals(subscription.status()))return List.of("RESUME","CANCEL","UPDATE_SHIPPING_ADDRESS");if(!"ACTIVE".equals(subscription.status()))return List.of();if(nextDelivery==null)return List.of("CANCEL");if("HELD".equals(nextDelivery.status()))return switch(nextDelivery.holdReason()){case "MISSING_SHIPPING_ADDRESS"->List.of("UPDATE_SHIPPING_ADDRESS","CANCEL");case "MISSING_BILLING_METHOD"->List.of("REGISTER_BILLING_METHOD","CANCEL");case "ORDER_STOCK_UNAVAILABLE"->store.scheduleAddonCount(nextDelivery.id())>0?List.of("REMOVE_NEXT_DELIVERY_ADDON","CANCEL"):List.of("CANCEL");default->List.of("CANCEL");};if(!"SCHEDULED".equals(nextDelivery.status()))return List.of("CANCEL");List<String> actions=new ArrayList<>(List.of("CHANGE_PLAN","CHANGE_DELIVERY_CYCLE","RESCHEDULE_NEXT","SKIP_NEXT","PAUSE","CANCEL","UPDATE_SHIPPING_ADDRESS","SET_NEXT_DELIVERY_ADDON"));if(store.scheduleAddonCount(nextDelivery.id())>0)actions.add("REMOVE_NEXT_DELIVERY_ADDON");return List.copyOf(actions);}
	private Map<String,Object> schedule(V2SubscriptionData.ScheduleView value){Map<String,Object> result=new LinkedHashMap<>();result.put("scheduleId",value.id());result.put("scheduledDate",value.scheduledDate());result.put("status",value.status());result.put("effectiveSnapshotId",value.effectiveSnapshotId());return result;}
	private Map<String,Object> history(V2SubscriptionData.CommandHistory value){return Map.of("commandType",value.commandType(),"result","SUCCEEDED","occurredAt",value.occurredAt());}
	private Map<String,Object> page(V2SubscriptionData.Page<?> value,List<Map<String,Object>> items){return Map.of("page",value.page(),"size",value.size(),"totalElements",value.total(),"items",items);}
	private int page(int page,int size){if(page<0||size<1||size>100||page>Integer.MAX_VALUE/size)throw support.validation("page");return page;}
}
