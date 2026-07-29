package com.quant.trade.agent.dao;

import com.quant.trade.agent.model.AgentApiAuditLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Agent API 审计日志 Mapper。 */
@Mapper
public interface AgentApiAuditLogMapper {

    int insert(AgentApiAuditLogDO record);

    List<AgentApiAuditLogDO> selectRecent(@Param("limit") int limit);
}
