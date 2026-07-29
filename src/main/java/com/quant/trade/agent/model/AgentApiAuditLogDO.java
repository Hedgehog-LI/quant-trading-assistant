package com.quant.trade.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Agent API 审计日志 DO。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentApiAuditLogDO {
    private Long id;
    private String requestId;
    private String clientId;
    private String senderHash;
    private String operationCode;
    private String method;
    private String path;
    private String paramSummary;
    private Integer httpStatus;
    private String errorCode;
    private Integer resultCount;
    private Long durationMs;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
}
