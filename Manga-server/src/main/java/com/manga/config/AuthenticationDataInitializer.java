package com.manga.config;

import com.manga.common.constant.ExceptionMessageConstants;
import com.manga.common.constant.AuditConstants;
import com.manga.common.enums.UserRole;
import com.manga.common.enums.UserStatus;
import com.manga.common.enums.RegistrationSource;
import com.manga.config.properties.MangaSecurityProperties;
import com.manga.repository.RoleRepository;
import com.manga.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.EnumSet;

/**
 * 在首次建库时初始化系统角色和管理员账号。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthenticationDataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MangaSecurityProperties securityProperties;

    /**
     * 以幂等方式初始化角色；管理员不存在时使用外部密码创建账号。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (UserRole role : UserRole.values()) {
            roleRepository.ensureExists(role, AuditConstants.SYSTEM_ACTOR);
        }

        MangaSecurityProperties.InitialAdmin admin = securityProperties.initialAdmin();
        var existingAdmin = userRepository.findByUsername(admin.username());
        if (existingAdmin.isPresent()) {
            EnumSet<UserRole> roles = existingAdmin.get().getRoles().isEmpty()
                    ? EnumSet.noneOf(UserRole.class)
                    : EnumSet.copyOf(existingAdmin.get().getRoles());
            roles.add(UserRole.ADMIN);
            roles.add(UserRole.USER);
            roleRepository.replaceUserRoles(existingAdmin.get().getId(), roles, AuditConstants.SYSTEM_ACTOR);
            log.info("Initial administrator roles ensured userId={}", existingAdmin.get().getId());
            return;
        }
        if (!StringUtils.hasText(admin.password())) {
            throw new IllegalStateException(ExceptionMessageConstants.INITIAL_ADMIN_PASSWORD_REQUIRED);
        }

        long adminId = userRepository.create(
                admin.username(),
                passwordEncoder.encode(admin.password()),
                admin.displayName(),
                UserStatus.ACTIVE,
                RegistrationSource.PASSWORD,
                AuditConstants.SYSTEM_ACTOR
        );
        roleRepository.replaceUserRoles(
                adminId,
                EnumSet.of(UserRole.ADMIN, UserRole.USER),
                AuditConstants.SYSTEM_ACTOR
        );
        log.info("Initial administrator created username={}", admin.username());
    }
}
