package edu.swu.agrimart.user;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/** 验证用户服务的应用启动入口的测试。 */
class UserServiceApplicationTest {
    /** 通过静态替身验证启动入口传参且不启动实际服务器。 */
    @Test
    void mainDelegatesArgumentsToSpringBootWithoutStartingServer() {
        String[] args = {"--server.port=0", "--spring.profiles.active=test"};
        try (var spring = mockStatic(SpringApplication.class)) {
            UserServiceApplication.main(args);
            spring.verify(() -> SpringApplication.run(UserServiceApplication.class, args));
            spring.verifyNoMoreInteractions();
        }
    }
}
