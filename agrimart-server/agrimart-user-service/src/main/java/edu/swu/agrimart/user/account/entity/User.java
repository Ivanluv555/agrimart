package edu.swu.agrimart.user.account.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** 用户账户、凭据摘要及角色状态。 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
            @UniqueConstraint(name = "uk_user_phone", columnNames = "phone")
        })
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String realName;

    @Column(nullable = false, length = 11)
    private String phone;

    @Column(length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.FARMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /** 用户账户可拥有的业务角色。 */
    public enum Role {
        FARMER,
        MERCHANT,
        ADMIN
    }

    /** 用户账户的启用状态。 */
    public enum Status {
        ACTIVE,
        INACTIVE
    }

    /**
     * 读取记录标识。
     *
     * @return 记录标识
     */
    public Long getId() {
        return id;
    }

    /**
     * 读取用户名。
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     *
     * @param v 待设置的字段值
     */
    public void setUsername(String v) {
        username = v;
    }

    /**
     * 读取密码摘要。
     *
     * @return 密码摘要
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 设置密码摘要。
     *
     * @param v 待设置的字段值
     */
    public void setPasswordHash(String v) {
        passwordHash = v;
    }

    /**
     * 读取真实姓名。
     *
     * @return 真实姓名
     */
    public String getRealName() {
        return realName;
    }

    /**
     * 设置真实姓名。
     *
     * @param v 待设置的字段值
     */
    public void setRealName(String v) {
        realName = v;
    }

    /**
     * 读取手机号码。
     *
     * @return 手机号码
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置手机号码。
     *
     * @param v 待设置的字段值
     */
    public void setPhone(String v) {
        phone = v;
    }

    /**
     * 读取电子邮箱。
     *
     * @return 电子邮箱
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置电子邮箱。
     *
     * @param v 待设置的字段值
     */
    public void setEmail(String v) {
        email = v;
    }

    /**
     * 读取账户角色。
     *
     * @return 账户角色
     */
    public Role getRole() {
        return role;
    }

    /**
     * 设置账户角色。
     *
     * @param v 待设置的字段值
     */
    public void setRole(Role v) {
        role = v;
    }

    /**
     * 读取业务状态。
     *
     * @return 业务状态
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 读取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
