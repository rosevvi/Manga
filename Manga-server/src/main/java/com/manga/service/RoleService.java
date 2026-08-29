package com.manga.service;

import com.manga.dto.RoleResponse;
import com.manga.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提供角色目录查询能力。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    /**
     * 返回系统中的角色及其是否允许分配给数据库用户。
     */
    public List<RoleResponse> findAll() {
        return roleRepository.findAll().stream()
                .map(role -> new RoleResponse(
                        role.getId(),
                        role.getCode().name(),
                        role.getName(),
                        role.getDescription(),
                        role.getCode().assignable(),
                        role.getCreatedAt(),
                        role.getUpdatedAt(),
                        role.getCreatedBy(),
                        role.getUpdatedBy()
                ))
                .toList();
    }
}
