package com.pawcycle.backend.subscription.v2;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.ObjectMapper;

final class V2SubscriptionApplicationSupport {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final ObjectMapper json;
	private final Clock clock;

	V2SubscriptionApplicationSupport(ObjectMapper json, Clock clock) {
		this.json = json;
		this.clock = clock;
	}

	void validateKey(String key) { if (key == null || !key.matches("[A-Za-z0-9._-]{1,128}")) throw validation("Idempotency-Key"); }
	long parseEtag(String value) { if(value==null) throw new V2ApiException(428,"IF_MATCH_REQUIRED","If-Match가 필요합니다."); if(!value.matches("\\\"[0-9]+\\\"")) throw new V2ApiException(400,"IF_MATCH_INVALID","If-Match 형식이 올바르지 않습니다."); try{return Long.parseLong(value.substring(1,value.length()-1));}catch(NumberFormatException e){throw new V2ApiException(400,"IF_MATCH_INVALID","If-Match 형식이 올바르지 않습니다.",e);} }
	String fingerprint(Map<String,Object> body) { try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(body))); StringBuilder out=new StringBuilder(); for(byte b:bytes) out.append(String.format("%02x",b)); return out.toString(); } catch(Exception e) { throw new IllegalStateException(e); } }
	Map<String,Object> responseBody(String bodyJson) { try { @SuppressWarnings("unchecked") Map<String,Object> body=json.readValue(bodyJson,Map.class); return body; } catch(Exception e) { throw new IllegalStateException("저장된 멱등 결과를 읽을 수 없습니다.",e); } }
	boolean removeInternalSnapshotIds(Map<String, Object> body) { return removeInternalSnapshotId(body.get("currentSnapshot")) | removeInternalSnapshotId(body.get("pendingSnapshot")); }
	String bodyJson(Map<String,Object> body) { try { return json.writeValueAsString(body); } catch(Exception e) { throw new IllegalStateException(e); } }
	long requiredLong(Map<String,Object>b,String key){Object value=b.get(key);if(!(value instanceof Number number))throw validation(key);try{return new BigDecimal(number.toString()).longValueExact();}catch(NumberFormatException|ArithmeticException exception){throw validation(key);}}
	int requiredInt(Map<String,Object>b,String key){long n=requiredLong(b,key);if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw validation(key);return(int)n;}
	LocalDate requiredDate(Map<String,Object>b,String key){Object value=b.get(key);if(!(value instanceof String text))throw validation(key);try{return LocalDate.parse(text);}catch(DateTimeParseException exception){throw validation(key);}}
	String requiredText(Map<String,Object>b,String key,int max){Object v=b.get(key);if(!(v instanceof String s))throw validation(key);s=s.trim();if(s.isBlank()||s.codePointCount(0,s.length())>max)throw validation(key);return s;}
	LocalDate today() { return LocalDate.now(clock.withZone(SEOUL)); }
	V2ApiException validation(String field) { return new V2ApiException(400,"VALIDATION_FAILED",field+" 값을 확인해 주세요."); }
	V2ApiException state() { return new V2ApiException(409,"SUBSCRIPTION_COMMAND_NOT_ALLOWED","현재 Subscription 상태에서는 명령을 실행할 수 없습니다."); }

	private Object canonical(Object value) { if(value instanceof Map<?,?> map) { Map<String,Object> sorted=new TreeMap<>(); map.forEach((key,item) -> sorted.put(String.valueOf(key),canonical(item))); return sorted; } if(value instanceof List<?> list) return list.stream().map(this::canonical).toList(); if(value instanceof Number number) return new BigDecimal(number.toString()).stripTrailingZeros(); return value; }
	private boolean removeInternalSnapshotId(Object snapshot) { if (!(snapshot instanceof Map<?, ?> map) || !map.containsKey("snapshotId")) return false; map.remove("snapshotId"); return true; }
}
