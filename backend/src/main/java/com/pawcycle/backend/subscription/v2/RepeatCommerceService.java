package com.pawcycle.backend.subscription.v2;

import com.pawcycle.backend.member.application.AuthenticatedMemberPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepeatCommerceService {
	private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
	private final JdbcTemplate jdbc; private final Clock clock;
	public RepeatCommerceService(JdbcTemplate jdbc, Clock clock){this.jdbc=jdbc;this.clock=clock;}

	@Transactional(readOnly=true)
	public Map<String,Object> reorderTiming(long memberId){
		LocalDate today=LocalDate.now(clock.withZone(SEOUL));
		Map<Long,ProductDates> products=new LinkedHashMap<>();
		jdbc.query("SELECT p.id,p.name,DATE(o.paid_at) purchased_date FROM orders o JOIN payments pay ON pay.order_id=o.id AND pay.status='SUCCEEDED' JOIN order_items oi ON oi.order_id=o.id JOIN skus sku ON sku.id=oi.sku_id JOIN products p ON p.id=sku.product_id WHERE o.member_id=? AND o.source='ONE_TIME' AND o.status='PAID' AND o.paid_at IS NOT NULL ORDER BY p.id, o.paid_at DESC, o.id DESC",(org.springframework.jdbc.core.RowCallbackHandler)rs->{long id=rs.getLong(1);ProductDates item=products.get(id);if(item==null){item=new ProductDates(id,rs.getString(2));products.put(id,item);}item.dates().add(rs.getDate(3).toLocalDate());},memberId);
		List<Map<String,Object>> result=new ArrayList<>();
		for(ProductDates product:products.values()){
			List<LocalDate> dates=product.dates().stream().sorted(Comparator.reverseOrder()).limit(5).toList();
			if(dates.size()<3)continue;
			List<Long> intervals=new ArrayList<>();for(int i=0;i<dates.size()-1;i++)intervals.add(java.time.temporal.ChronoUnit.DAYS.between(dates.get(i+1),dates.get(i)));
			intervals.sort(Long::compareTo);long median=intervals.size()%2==1?intervals.get(intervals.size()/2):(intervals.get(intervals.size()/2-1)+intervals.get(intervals.size()/2))/2;
			LocalDate expected=dates.getFirst().plusDays(median);if(expected.isAfter(today.plusDays(7)))continue;
			Map<String,Object> item=new LinkedHashMap<>();item.put("productId",product.id());item.put("productName",product.name());item.put("lastPurchasedDate",dates.getFirst());item.put("expectedReorderDate",expected);item.put("state",expected.isBefore(today)?"OVERDUE":"DUE_SOON");item.put("purchaseCount",dates.size());result.add(item);
		}
		result.sort(Comparator.comparing(item->(LocalDate)item.get("expectedReorderDate")));return Map.of("items",result.stream().limit(10).toList());
	}

	@Transactional(readOnly=true)
	public Map<String,Object> cycleSuggestion(long memberId,long subscriptionId){
		Map<String,Object> subscription=one("SELECT id,status,delivery_cycle_weeks,current_snapshot_id FROM subscriptions WHERE id=? AND member_id=? AND mvp2_managed=true",subscriptionId,memberId);
		if(subscription==null)throw new V2ApiException(404,"SUBSCRIPTION_NOT_FOUND","Subscription을 찾을 수 없습니다.");
		if(!"ACTIVE".equals(subscription.get("status")))throw new V2ApiException(409,"SUBSCRIPTION_COMMAND_NOT_ALLOWED","ACTIVE Subscription만 제안할 수 있습니다.");
		List<LocalDate> dates=jdbc.query("SELECT context.scheduled_date FROM subscription_order_context context JOIN orders o ON o.id=context.order_id JOIN payments pay ON pay.order_id=o.id AND pay.status='SUCCEEDED' WHERE context.subscription_id=? AND o.source='SUBSCRIPTION' AND o.status='PAID' AND context.scheduled_date IS NOT NULL ORDER BY context.scheduled_date DESC,context.order_id DESC",(rs,n)->rs.getDate(1).toLocalDate(),subscriptionId);
		if(dates.size()<3)throw new V2ApiException(409,"CYCLE_SUGGESTION_INSUFFICIENT_HISTORY","성공적으로 처리된 Subscription 주문이 3회 이상 필요합니다.");
		List<Long> intervals=new ArrayList<>();for(int i=0;i<dates.size()-1;i++)intervals.add(java.time.temporal.ChronoUnit.DAYS.between(dates.get(i+1),dates.get(i)));
		intervals.sort(Long::compareTo);long medianDays=intervals.size()%2==1?intervals.get(intervals.size()/2):(intervals.get(intervals.size()/2-1)+intervals.get(intervals.size()/2))/2;long median=medianDays/7;
		long versionId=((Number)subscription.get("current_snapshot_id")).longValue();versionId=jdbc.queryForObject("SELECT source_plan_version_id FROM subscription_snapshots WHERE id=?",Long.class,versionId);
		List<Integer> allowed=jdbc.queryForList("SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=? ORDER BY delivery_cycle_weeks",Integer.class,versionId);int current=((Number)subscription.get("delivery_cycle_weeks")).intValue();
		int chosen=allowed.stream().min(Comparator.comparingLong((Integer value)->Math.abs(value*7L-medianDays)).thenComparingInt((Integer value)->value==current?0:-value)).orElse(current);
		Map<String,Object> result=new LinkedHashMap<>();result.put("subscriptionId",subscriptionId);result.put("currentDeliveryCycleWeeks",current);result.put("medianSuccessfulIntervalWeeks",median);result.put("allowedDeliveryCycleWeeks",allowed);result.put("suggestion",chosen==current?null:Map.of("deliveryCycleWeeks",chosen));return result;
	}

	@Transactional(readOnly=true)
	public Map<String,Object> subscriptionOptions(long memberId,long orderId){
		Map<String,Object> order=one("SELECT id FROM orders o WHERE o.id=? AND o.member_id=? AND o.source='ONE_TIME' AND o.status='PAID' AND EXISTS (SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.status='SUCCEEDED')",orderId,memberId);
		if(order==null)throw new V2ApiException(404,"ORDER_NOT_FOUND","주문을 찾을 수 없습니다.");
		SetProducts source=orderProducts(orderId);List<Map<String,Object>> options=new ArrayList<>();
		List<Map<String,Object>> versions=jdbc.queryForList("SELECT v.id version_id,p.name plan_name,p.target_pet_type,v.package_price_krw FROM plan_versions v JOIN subscription_plans p ON p.id=v.plan_id WHERE p.current_plan_version_id=v.id AND p.name IS NOT NULL AND p.on_sale=true AND v.is_migration_only=false AND (p.sale_starts_on IS NULL OR p.sale_starts_on<=CURRENT_DATE()) AND (p.sale_ends_on IS NULL OR p.sale_ends_on>=CURRENT_DATE()) ORDER BY v.id");
		for(Map<String,Object> version:versions){long versionId=((Number)version.get("version_id")).longValue();List<Long> products=jdbc.queryForList("SELECT DISTINCT sku.product_id FROM plan_items item JOIN skus sku ON sku.id=item.sku_id WHERE item.plan_version_id=?",Long.class,versionId);List<Long> matching=products.stream().filter(source.productIds()::contains).toList();if(matching.isEmpty())continue;String type=(String)version.get("target_pet_type");List<Long> pets=jdbc.queryForList("SELECT id FROM pets WHERE member_id=? AND pet_type=? ORDER BY id",Long.class,memberId,type);Map<String,Object> option=new LinkedHashMap<>();option.put("planVersionId",versionId);option.put("planName",version.get("plan_name"));option.put("matchingProductIds",matching);option.put("compatibleOwnedPetIds",pets);option.put("allowedDeliveryCycleWeeks",jdbc.queryForList("SELECT delivery_cycle_weeks FROM plan_version_delivery_cycles WHERE plan_version_id=? ORDER BY delivery_cycle_weeks",Integer.class,versionId));option.put("packagePriceKrw",version.get("package_price_krw"));options.add(option);}
		return Map.of("orderId",orderId,"options",options);
	}

	private SetProducts orderProducts(long orderId){return new SetProducts(java.util.Set.copyOf(jdbc.queryForList("SELECT DISTINCT sku.product_id FROM order_items item JOIN skus sku ON sku.id=item.sku_id WHERE item.order_id=?",Long.class,orderId)));}
	private Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.getFirst();}
	private static final class ProductDates{private final long id;private final String name;private final List<LocalDate> dates=new ArrayList<>();ProductDates(long id,String name){this.id=id;this.name=name;}long id(){return id;}String name(){return name;}List<LocalDate> dates(){return dates;}}
	private record SetProducts(java.util.Set<Long> productIds){}
}
