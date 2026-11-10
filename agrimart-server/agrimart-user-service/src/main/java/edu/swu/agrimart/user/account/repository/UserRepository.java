package edu.swu.agrimart.user.account.repository;

import edu.swu.agrimart.user.account.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户持久化仓储，支持登录查询与账户重复检查。 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 按用户名查找用户账户。
     *
     * @param username 用户名
     * @return 查询结果，记录不存在时为空
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否已被占用。
     *
     * @param username 用户名
     * @return 是否符合查询或校验条件
     */
    boolean existsByUsername(String username);

    /**
     * 检查手机号是否已被占用。
     *
     * @param phone 手机号码
     * @return 是否符合查询或校验条件
     */
    boolean existsByPhone(String phone);
}
