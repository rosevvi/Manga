package com.manga.repository;
import com.manga.entity.CreativeApproval;
import com.manga.mapper.CreativeApprovalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
/** 审批记录的持久化边界。 */
@Repository
@RequiredArgsConstructor
public class CreativeApprovalRepository {
    private final CreativeApprovalMapper mapper;
    /** 查询提案的一对一审批。 */
    public Optional<CreativeApproval> find(String id) { return Optional.ofNullable(mapper.findByChangeSet(id)); }
    /** 保存首次开放的审批。 */
    public void create(CreativeApproval value) { mapper.insert(value); }
    /** 保存最终决策与审批人。 */
    public void update(CreativeApproval value) { mapper.updateDecision(value); }
    /** 查询当前过期待处理的有限批审批。 */
    public List<String> findExpired(LocalDateTime now, int limit) { return mapper.findExpired(now, limit); }
    /** 一次获取当前页全部审批。 */
    public List<CreativeApproval> findAll(List<String> ids) { return ids.isEmpty() ? List.of() : mapper.findAll(ids); }
}
