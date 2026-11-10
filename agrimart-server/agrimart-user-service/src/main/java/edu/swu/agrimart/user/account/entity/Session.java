package edu.swu.agrimart.user.account.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** 仅保存令牌摘要的可撤销用户会话。 */
@Entity
@Table(
        name = "sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_hash", columnNames = "tokenHash"))
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String tokenHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    /**
     * 读取用户标识。
     *
     * @return 用户标识
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户标识。
     *
     * @param v 待设置的字段值
     */
    public void setUserId(Long v) {
        userId = v;
    }

    /**
     * 读取令牌摘要。
     *
     * @return 令牌摘要
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * 设置令牌摘要。
     *
     * @param v 待设置的字段值
     */
    public void setTokenHash(String v) {
        tokenHash = v;
    }

    /**
     * 读取到期时间。
     *
     * @return 到期时间
     */
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * 设置到期时间。
     *
     * @param v 待设置的字段值
     */
    public void setExpiresAt(OffsetDateTime v) {
        expiresAt = v;
    }

    /**
     * 判断当前会话是否已吊销。
     *
     * @return 是否符合查询或校验条件
     */
    public boolean isRevoked() {
        return revoked;
    }

    /**
     * 设置是否已吊销。
     *
     * @param v 待设置的字段值
     */
    public void setRevoked(boolean v) {
        revoked = v;
    }
}
