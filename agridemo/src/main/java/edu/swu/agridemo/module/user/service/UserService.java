package edu.swu.agridemo.module.user.service;

import edu.swu.agridemo.core.exception.BusinessException;
import edu.swu.agridemo.module.user.dto.UserLoginDto;
import edu.swu.agridemo.module.user.dto.UserRegisterDto;
import edu.swu.agridemo.module.user.dto.UserVo;
import edu.swu.agridemo.module.user.entity.User;
import edu.swu.agridemo.module.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserVo register(UserRegisterDto dto) {
        if (userRepository.existsByUsername(dto.username())) {
            throw new BusinessException("用户名已存在");
        }

        if (userRepository.existsByPhone(dto.phone())) {
            throw new BusinessException("手机号已被注册");
        }

        User user = new User();
        user.setUsername(dto.username());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRealName(dto.realName());
        user.setPhone(dto.phone());
        user.setEmail(dto.email());
        user.setRole(dto.role());
        user.setStatus(User.UserStatus.ACTIVE);
        user.setAddress(dto.address());

        user = userRepository.save(user);
        return convertToVo(user);
    }

    public UserVo login(UserLoginDto dto) {
        User user = userRepository.findByUsername(dto.username())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("账号已被禁用");
        }

        return convertToVo(user);
    }

    public UserVo getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return convertToVo(user);
    }

    private UserVo convertToVo(User user) {
        return new UserVo(
            user.getId(),
            user.getUsername(),
            user.getRealName(),
            user.getPhone(),
            user.getEmail(),
            user.getRole(),
            user.getStatus(),
            user.getAddress(),
            user.getAvatar(),
            user.getDescription(),
            user.getCreatedAt()
        );
    }
}
