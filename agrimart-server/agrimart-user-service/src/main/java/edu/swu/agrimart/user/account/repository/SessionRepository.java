package edu.swu.agrimart.user.account.repository;

import edu.swu.agrimart.user.account.entity.Session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 会话持久化仓储，支持按令牌摘要查询。 */
public interface SessionRepository extends JpaRepository<Session, Long> {
    /**
     * 按令牌摘要查找用户会话。
     *
     * @param hash 令牌摘要
     * @return 查询结果，记录不存在时为空
     */
    Optional<Session> findByTokenHash(String hash);
}
