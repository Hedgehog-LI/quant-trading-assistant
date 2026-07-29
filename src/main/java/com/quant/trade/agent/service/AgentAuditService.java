package com.quant.trade.agent.service;

import com.quant.trade.agent.dao.AgentApiAuditLogMapper;
import com.quant.trade.agent.model.AgentApiAuditLogDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 审计服务。持久化到 agent_api_audit_log 表。
 * <p>
 * 严禁记录 Token、Longbridge 凭据、完整请求/响应、异常堆栈或完整持仓明细。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAuditService {

    private final AgentApiAuditLogMapper auditMapper;

    /**
     * 记录一次 Agent API 调用。
     *
     * @param requestId    请求追踪 ID
     * @param clientId     客户端标识（Token hash）
     * @param senderHash   QQ OpenID hash（可空）
     * @param operationCode 操作码
     * @param method       HTTP 方法
     * @param path         请求路径
     * @param paramSummary 参数摘要（脱敏）
     * @param httpStatus   HTTP 状态码
     * @param errorCode    业务错误码（可空）
     * @param resultCount  结果条数
     * @param durationMs   耗时毫秒
     * @param requestedAt  请求开始时间
     */
    public void record(String requestId, String clientId, String senderHash,
                       String operationCode, String method, String path,
                       String paramSummary, int httpStatus, String errorCode,
                       int resultCount, long durationMs, LocalDateTime requestedAt) {
        try {
            AgentApiAuditLogDO log = AgentApiAuditLogDO.builder()
                .requestId(requestId)
                .clientId(clientId)
                .senderHash(senderHash)
                .operationCode(operationCode)
                .method(method)
                .path(path)
                .paramSummary(truncate(paramSummary, 500))
                .httpStatus(httpStatus)
                .errorCode(errorCode)
                .resultCount(resultCount)
                .durationMs(durationMs)
                .requestedAt(requestedAt)
                .completedAt(LocalDateTime.now())
                .build();
            auditMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不影响主请求，仅记录日志
            AgentAuditService.log.warn("Agent audit insert failed for requestId={}: {}", requestId, e.getMessage());
        }
    }

    public List<AgentApiAuditLogDO> getRecent(int limit) {
        return auditMapper.selectRecent(Math.min(limit, 500));
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
