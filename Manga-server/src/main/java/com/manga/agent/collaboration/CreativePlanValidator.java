package com.manga.agent.collaboration;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.harness.AgentRuntimeException;
import com.manga.agent.profile.AgentProfileKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** 在写入依赖前验证封闭 DAG，评审节点必须覆盖本轮所有其他节点。 */
public final class CreativePlanValidator {
    /** 拒绝重复节点、未知依赖、环、非 Worker 角色和未受评审覆盖的分支。 */
    public static void validate(DelegatePlanRequest request, int maxBatch) {
        if (request.nodes().size() > maxBatch) invalid();
        Map<String, DelegatePlanRequest.Node> indexed = new HashMap<>();
        for (var node : request.nodes()) {
            if (node.profile() == AgentProfileKey.MANGA_DIRECTOR || CollaborationToolContract.JOIN_NODE_KEY.equals(node.key())
                    || Objects.nonNull(indexed.put(node.key(), node))
                    || new HashSet<>(node.dependsOn()).size() != node.dependsOn().size()) invalid();
        }
        for (var node : request.nodes()) {
            if (!indexed.keySet().containsAll(node.dependsOn())) invalid();
            ancestors(node.key(), indexed, new HashSet<>());
        }
        var reviewers = request.nodes().stream().filter(node -> node.profile() == AgentProfileKey.CONTINUITY_REVIEWER).toList();
        if (reviewers.size() != 1 || ancestors(reviewers.getFirst().key(), indexed, new HashSet<>()).size() != indexed.size() - 1) invalid();
    }

    /** 收集祖先同时检测当前递归路径上的环；节点数受批量预算限制。 */
    private static Set<String> ancestors(String key, Map<String, DelegatePlanRequest.Node> nodes, Set<String> path) {
        if (!path.add(key)) invalid();
        Set<String> result = new HashSet<>();
        for (String dependency : nodes.get(key).dependsOn()) {
            result.add(dependency);
            result.addAll(ancestors(dependency, nodes, path));
        }
        path.remove(key);
        return result;
    }

    /** 拒绝不符合封闭 DAG 与评审覆盖约束的计划。 */
    private static void invalid() {
        throw new AgentRuntimeException(CreativeErrorCode.INVALID_REQUEST, CollaborationMessages.INVALID_PLAN);
    }
    private CreativePlanValidator() { }
}
