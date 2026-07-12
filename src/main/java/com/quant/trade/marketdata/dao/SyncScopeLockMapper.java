package com.quant.trade.marketdata.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 同 scope 同步任务并发重试锁 Mapper。 */
@Mapper
public interface SyncScopeLockMapper {

    /** INSERT ... ON DUPLICATE KEY UPDATE — 确保锁行存在，重复请求保持幂等。 */
    int upsert(@Param("provider") String provider, @Param("taskType") String taskType, @Param("scopeHash") String scopeHash);

    /** SELECT ... FOR UPDATE — 行级锁，必须在事务内调用。返回值忽略，只做锁。 */
    int selectForUpdate(@Param("provider") String provider, @Param("taskType") String taskType, @Param("scopeHash") String scopeHash);
}
