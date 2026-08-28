package com.pawcycle.backend.catalog.engagement.application;

import com.pawcycle.backend.catalog.product.application.ProductNotFoundException;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewSummaryService {
	private static final List<String> UNSAFE_TERMS=List.of("질병","치료","약","처방","의학","medical","disease","treatment","medicine","prescription");
	private final JdbcTemplate jdbc; private final ProductRepository products; private final ReviewSummaryAiClient ai; private final Clock clock;
	public ReviewSummaryService(JdbcTemplate jdbc,ProductRepository products,ReviewSummaryAiClient ai,Clock clock){this.jdbc=jdbc;this.products=products;this.ai=ai;this.clock=clock;}

	@Transactional
	public ReviewSummaryResponse summary(long productId){
		if(products.findPublicById(productId).isEmpty() || !hasActiveBrand(productId))throw new ProductNotFoundException();
		List<ReviewRow> reviews=jdbc.query("SELECT id,rating,content,updated_at FROM reviews WHERE product_id=? AND visible=true ORDER BY created_at DESC,id DESC LIMIT 30",(rs,n)->new ReviewRow(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getTimestamp(4)),productId);
		long count=jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE product_id=? AND visible=true",Long.class,productId);
		BigDecimal average=jdbc.queryForObject("SELECT AVG(rating) FROM reviews WHERE product_id=? AND visible=true",BigDecimal.class,productId);
		if(count<3)return new ReviewSummaryResponse("INSUFFICIENT_REVIEWS",null,count,average);
		List<ReviewRow> sourceReviews=jdbc.query("SELECT id,rating,content,updated_at FROM reviews WHERE product_id=? AND visible=true ORDER BY id",(rs,n)->new ReviewRow(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getTimestamp(4)),productId);
		String fingerprint=fingerprint(sourceReviews);
		Map<String,Object> cached=jdbc.queryForList("SELECT source_fingerprint,summary FROM product_review_summaries WHERE product_id=?",productId).stream().findFirst().orElse(null);
		if(cached!=null&&fingerprint.equals(cached.get("source_fingerprint")))return new ReviewSummaryResponse("AVAILABLE",(String)cached.get("summary"),count,average);
		String generated;
		try{generated=ai.summarize(reviews.stream().map(row->new ReviewSummaryAiClient.ReviewInput(row.rating(),row.content())).toList());}catch(RuntimeException exception){return new ReviewSummaryResponse("UNAVAILABLE",null,count,average);}
		if(!validAiText(generated))return new ReviewSummaryResponse("UNAVAILABLE",null,count,average);
		jdbc.update("INSERT INTO product_review_summaries(product_id,source_fingerprint,summary,generated_at) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE source_fingerprint=VALUES(source_fingerprint),summary=VALUES(summary),generated_at=VALUES(generated_at)",productId,fingerprint,generated.trim(),Timestamp.from(clock.instant()));
		return new ReviewSummaryResponse("AVAILABLE",generated.trim(),count,average);
	}
	private boolean hasActiveBrand(long productId){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM products p JOIN brands b ON b.id=p.brand_id WHERE p.id=? AND b.active=true",Integer.class,productId);return Integer.valueOf(1).equals(count);}

	private boolean validAiText(String text){if(text==null||text.isBlank()||text.codePointCount(0,text.length())>500||text.contains("<")||text.contains(">"))return false;if(text.codePoints().anyMatch(codePoint->Character.isISOControl(codePoint)&&codePoint!='\n'&&codePoint!='\r'))return false;if(text.codePoints().noneMatch(codePoint->Character.UnicodeScript.of(codePoint)==Character.UnicodeScript.HANGUL))return false;String lower=text.toLowerCase(java.util.Locale.ROOT);return UNSAFE_TERMS.stream().noneMatch(lower::contains);}
	record ReviewRow(long id,int rating,String content,Timestamp updatedAt){}
	String fingerprint(List<ReviewRow> reviews){try{MessageDigest digest=MessageDigest.getInstance("SHA-256");for(ReviewRow review:reviews){byte[] value=(review.id()+"\u0000"+review.rating()+"\u0000"+review.content()+"\u0000"+review.updatedAt().getTime()).getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array());digest.update(value);}StringBuilder result=new StringBuilder(64);for(byte value:digest.digest())result.append(String.format("%02x",value));return result.toString();}catch(Exception exception){throw new IllegalStateException(exception);}}
	public record ReviewSummaryResponse(String status,String summary,long reviewCount,BigDecimal averageRating){}
}
