package com.pawcycle.backend.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pawcycle.backend.catalog.product.domain.Product;
import com.pawcycle.backend.catalog.product.infra.ProductRepository;
import com.pawcycle.backend.catalog.sku.domain.Sku;
import com.pawcycle.backend.catalog.sku.infra.SkuRepository;
import com.pawcycle.backend.member.domain.Member;
import com.pawcycle.backend.member.infra.MemberRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseFoundationIntegrationTests {

	private final DataSource dataSource;
	private final JdbcTemplate jdbcTemplate;
	private final Flyway flyway;
	private final MemberRepository memberRepository;
	private final ProductRepository productRepository;
	private final SkuRepository skuRepository;
	private final PasswordEncoder passwordEncoder;
	private final String datasourceUrl;

	@Autowired
	DatabaseFoundationIntegrationTests(
			DataSource dataSource,
			JdbcTemplate jdbcTemplate,
			Flyway flyway,
			MemberRepository memberRepository,
			ProductRepository productRepository,
			SkuRepository skuRepository,
			PasswordEncoder passwordEncoder,
			@Value("${spring.datasource.url}") String datasourceUrl) {
		this.dataSource = dataSource;
		this.jdbcTemplate = jdbcTemplate;
		this.flyway = flyway;
		this.memberRepository = memberRepository;
		this.productRepository = productRepository;
		this.skuRepository = skuRepository;
		this.passwordEncoder = passwordEncoder;
		this.datasourceUrl = datasourceUrl;
	}

	@Test
	void testProfileConnectsToMySqlAndCreatesOnlyApprovedTables() throws Exception {
		assertThat(datasourceUrl).startsWith("jdbc:mysql://");
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
			assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("8.4");
		}

		List<String> tables = jdbcTemplate.queryForList(
				"""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = DATABASE()
				""",
				String.class);

		assertThat(tables).contains("members", "products", "skus", "flyway_schema_history");
		assertThat(tables).doesNotContain("subscriptions", "orders", "payments", "deliveries", "inventory");
	}

	@Test
	void memberCredentialColumnsMatchApprovedPhysicalContract() {
		Map<String, Object> email = column("members", "email");
		assertThat(email.get("CHARACTER_SET_NAME")).isEqualTo("ascii");
		assertThat(email.get("COLLATION_NAME")).isEqualTo("ascii_bin");
		assertThat(email.get("IS_NULLABLE")).isEqualTo("NO");
		assertThat(email.get("COLUMN_TYPE")).isEqualTo("varchar(254)");

		Map<String, Object> passwordHash = column("members", "password_hash");
		assertThat(passwordHash.get("IS_NULLABLE")).isEqualTo("NO");
		assertThat(passwordHash.get("COLUMN_TYPE")).isEqualTo("varchar(100)");
		assertThat(passwordHash.get("COLUMN_DEFAULT")).isNull();

		Integer uniqueEmailIndex = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM information_schema.statistics
				WHERE table_schema = DATABASE()
				  AND table_name = 'members'
				  AND index_name = 'uk_members_email'
				  AND non_unique = 0
				""",
				Integer.class);
		assertThat(uniqueEmailIndex).isEqualTo(1);
	}

	@Test
	void rerunningFlywayDoesNotReapplyV1() {
		Integer before = appliedMigrationCount();
		flyway.migrate();
		Integer after = appliedMigrationCount();

		assertThat(before).isEqualTo(1);
		assertThat(after).isEqualTo(before);
	}

	@Test
	@Transactional
	void repositoriesPersistMemberProductAndSkuWithRuntimeBcryptFixture() {
		String email = "foundation-" + UUID.randomUUID() + "@example.test";
		String runtimePassword = UUID.randomUUID().toString();
		Member member = memberRepository.save(new Member(email, passwordEncoder.encode(runtimePassword)));

		Product product = productRepository.save(new Product(
				"Foundation product",
				"Foundation short description",
				"Foundation description",
				"DOG",
				null,
				"VISIBLE"));
		Sku sku = skuRepository.save(new Sku(product, "Foundation SKU", new BigDecimal("1000.00"), true, 1));

		assertThat(member.getId()).isNotNull();
		assertThat(memberRepository.findByEmail(email)).isPresent();
		assertThat(passwordEncoder.matches(runtimePassword, member.getPasswordHash())).isTrue();
		assertThat(sku.getId()).isNotNull();
		assertThat(skuRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId()))
				.extracting(Sku::getId)
				.containsExactly(sku.getId());
	}

	@Test
	void databaseConstraintsRejectDuplicateEmailAndNegativePrice() {
		String email = "constraint-" + UUID.randomUUID() + "@example.test";
		String passwordHash = passwordEncoder.encode(UUID.randomUUID().toString());
		jdbcTemplate.update("INSERT INTO members (email, password_hash) VALUES (?, ?)", email, passwordHash);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO members (email, password_hash) VALUES (?, ?)",
				email,
				passwordEncoder.encode(UUID.randomUUID().toString())))
				.isInstanceOf(DataIntegrityViolationException.class);

		Product product = productRepository.save(new Product(
				"Constraint product",
				"Constraint short description",
				null,
				"TEST",
				null,
				"TEST"));

		assertThatThrownBy(() -> jdbcTemplate.update(
				"""
				INSERT INTO skus (product_id, name, price, subscribable, display_order)
				VALUES (?, ?, ?, ?, ?)
				""",
				product.getId(),
				"Negative price SKU",
				new BigDecimal("-0.01"),
				true,
				1))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Map<String, Object> column(String table, String column) {
		return jdbcTemplate.queryForMap(
				"""
				SELECT character_set_name, collation_name, is_nullable, column_type, column_default
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = ?
				  AND column_name = ?
				""",
				table,
				column);
	}

	private Integer appliedMigrationCount() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
				Integer.class);
	}
}
