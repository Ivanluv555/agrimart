package edu.swu.agrimart.user.account.repository;

import static org.assertj.core.api.Assertions.*;

import edu.swu.agrimart.user.account.entity.Session;
import edu.swu.agrimart.user.account.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

import javax.sql.DataSource;

/** 验证账户实体、唯一约束和会话仓储的测试。 */
@SpringJUnitConfig(RepositoriesTest.Database.class)
@Transactional
class RepositoriesTest {
    /** 账户仓储测试使用的独立内存数据库配置。 */
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class Database {
        /**
         * 创建账户仓储测试使用的独立内存数据源。
         *
         * @return 测试所需的样例数据或配置对象
         */
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:user-repository-tests;DB_CLOSE_DELAY=-1", "sa", "");
        }

        /**
         * 创建扫描账户实体的测试实体管理器工厂。
         *
         * @param dataSource 测试数据源
         * @return 测试所需的样例数据或配置对象
         */
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(User.class.getPackageName());
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }

        /**
         * 创建测试使用的 JPA 事务管理器。
         *
         * @param factory 实体管理器工厂
         * @return 测试所需的样例数据或配置对象
         */
        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired EntityManager entityManager;

    /** 验证新账户默认角色、启用状态及创建时间。 */
    @Test
    void newUserHasSafeDefaultsAndCreationTime() {
        OffsetDateTime before = OffsetDateTime.now();
        User user = new User();
        assertThat(user.getId()).isNull();
        assertThat(user.getRole()).isEqualTo(User.Role.FARMER);
        assertThat(user.getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(user.getCreatedAt()).isBetween(before, OffsetDateTime.now());
    }

    /**
     * 通过实际持久化验证账户字段及所有角色。
     *
     * @param role 账户角色
     */
    @ParameterizedTest
    @EnumSource(User.Role.class)
    void userFieldsAndAllRolesSurviveActualPersistence(User.Role role) {
        User user = user("farmer", "13800138000");
        user.setRole(role);
        var createdAt = user.getCreatedAt();
        users.saveAndFlush(user);
        entityManager.clear();
        User found = users.findByUsername("farmer").orElseThrow();
        assertThat(found.getId()).isPositive();
        assertThat(found.getUsername()).isEqualTo("farmer");
        assertThat(found.getRealName()).isEqualTo("Real Name");
        assertThat(found.getPasswordHash()).isEqualTo("encoded");
        assertThat(found.getPhone()).isEqualTo("13800138000");
        assertThat(found.getEmail()).isEqualTo("farmer@example.com");
        assertThat(found.getRole()).isEqualTo(role);
        assertThat(found.getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(found.getCreatedAt().toInstant())
                .isCloseTo(createdAt.toInstant(), within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(users.existsByUsername("farmer")).isTrue();
        assertThat(users.existsByPhone("13800138000")).isTrue();
        assertThat(users.findById(found.getId())).isPresent();
        assertThat(users.findByUsername("missing")).isEmpty();
        assertThat(users.existsByUsername("missing")).isFalse();
        assertThat(users.existsByPhone("13900139000")).isFalse();
    }

    /** 验证禁用状态与可空邮箱能够持久化。 */
    @Test
    void inactiveStatusAndOptionalEmailSurvivePersistence() {
        User user = user("farmer", "13800138000");
        user.setEmail(null);
        ReflectionTestUtils.setField(user, "status", User.Status.INACTIVE);
        users.saveAndFlush(user);
        entityManager.clear();
        User found = users.findById(user.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(User.Status.INACTIVE);
        assertThat(found.getEmail()).isNull();
    }

    /** 验证数据库拒绝重复用户名。 */
    @Test
    void duplicateUsernameIsRejectedByDatabase() {
        users.saveAndFlush(user("farmer", "13800138000"));
        assertThatThrownBy(() -> users.saveAndFlush(user("farmer", "13900139000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 验证数据库拒绝重复手机号。 */
    @Test
    void duplicatePhoneIsRejectedByDatabase() {
        users.saveAndFlush(user("farmer", "13800138000"));
        assertThatThrownBy(() -> users.saveAndFlush(user("other", "13800138000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 验证数据库拒绝空密码摘要。 */
    @Test
    void userPasswordCannotBeNull() {
        User user = user("farmer", "13800138000");
        user.setPasswordHash(null);
        assertThatThrownBy(() -> users.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 通过真实仓储验证会话查询与吊销状态持久化。 */
    @Test
    void sessionQueryAndRevocationPersistWithoutMockingRepository() {
        Session session = session("digest");
        assertThat(session.isRevoked()).isFalse();
        sessions.saveAndFlush(session);
        entityManager.clear();
        Session found = sessions.findByTokenHash("digest").orElseThrow();
        assertThat(found.getTokenHash()).isEqualTo("digest");
        assertThat(found.getUserId()).isEqualTo(42L);
        assertThat(found.getExpiresAt()).isEqualTo(session.getExpiresAt());
        assertThat(found.isRevoked()).isFalse();
        found.setRevoked(true);
        sessions.saveAndFlush(found);
        entityManager.clear();
        assertThat(sessions.findByTokenHash("digest").orElseThrow().isRevoked()).isTrue();
        assertThat(sessions.findByTokenHash("missing")).isEmpty();
    }

    /** 验证数据库拒绝重复会话摘要。 */
    @Test
    void duplicateSessionDigestIsRejectedByDatabase() {
        sessions.saveAndFlush(session("digest"));
        assertThatThrownBy(() -> sessions.saveAndFlush(session("digest")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 验证会话到期时间不能为空。 */
    @Test
    void sessionRequiresExpiry() {
        Session session = session("digest");
        session.setExpiresAt(null);
        assertThatThrownBy(() -> sessions.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 构造测试使用的用户。
     *
     * @param username 用户名
     * @param phone 手机号码
     * @return 测试所需的样例数据或配置对象
     */
    private static User user(String username, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash("encoded");
        user.setRealName("Real Name");
        user.setEmail("farmer@example.com");
        return user;
    }

    /**
     * 构造测试使用的会话。
     *
     * @param digest 测试输入或业务依赖
     * @return 测试所需的样例数据或配置对象
     */
    private static Session session(String digest) {
        Session session = new Session();
        session.setTokenHash(digest);
        session.setUserId(42L);
        session.setExpiresAt(OffsetDateTime.parse("2030-01-01T12:00:00Z"));
        return session;
    }
}
