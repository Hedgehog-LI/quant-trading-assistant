package com.quant.trade.marketdata.analysis.dao;

import com.quant.trade.marketdata.analysis.model.MarketSectorIdentityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 板块稳定身份 Mapper（market_sector_identity + market_sector_identity_lock）。
 *
 * <p>身份声明两步：{@link #insertIgnoreLockAnchor}（INSERT IGNORE 锚点）后
 * {@link #selectLockAnchorForUpdate}（SELECT ... FOR UPDATE），再校验跨 taxonomy 区间不重叠并写入。
 * H2/MODE=MySQL 无法复现 InnoDB gap-lock 顺序，并发层断言 OUTCOME（行数 + 唯一约束），非锁顺序。</p>
 */
@Mapper
public interface MarketSectorIdentityMapper {

    /** INSERT IGNORE 锚点（已存在则返回 0，幂等）。 */
    int insertIgnoreLockAnchor(@Param("providerCode") String providerCode,
                               @Param("marketCode") String marketCode,
                               @Param("providerSectorId") String providerSectorId);

    /** SELECT ... FOR UPDATE 锁定锚点（READ COMMITTED 下与 INSERT IGNORE 配合实现单写者）。 */
    Long selectLockAnchorForUpdate(@Param("providerCode") String providerCode,
                                   @Param("marketCode") String marketCode,
                                   @Param("providerSectorId") String providerSectorId);

    /** 插入身份行（返回受影响行数；自然键冲突由唯一约束拒绝）。 */
    int insertIdentity(MarketSectorIdentityDO record);

    /** 按自然键查询身份（衍生层只使用此路径，不 JOIN watch_id）。 */
    MarketSectorIdentityDO selectByNaturalKey(@Param("providerCode") String providerCode,
                                              @Param("marketCode") String marketCode,
                                              @Param("providerSectorId") String providerSectorId,
                                              @Param("taxonomyVersion") String taxonomyVersion);

    /** 按数值 id 查询（= sectorId，衍生层稳定身份查询）。 */
    MarketSectorIdentityDO selectById(@Param("id") Long id);

    /**
     * 检查同一锚点下与目标区间重叠的有效身份行（跨 taxonomy version）。
     * 返回重叠行数；非 0 表示区间冲突，调用方拒绝写入。
     */
    int countOverlappingIntervals(@Param("providerCode") String providerCode,
                                  @Param("marketCode") String marketCode,
                                  @Param("providerSectorId") String providerSectorId,
                                  @Param("validFrom") LocalDate validFrom,
                                  @Param("validTo") LocalDate validTo);

    /** 统计某锚点下的身份行数（用于并发 claim 单结果断言）。 */
    int countByIdentityAnchor(@Param("providerCode") String providerCode,
                              @Param("marketCode") String marketCode,
                              @Param("providerSectorId") String providerSectorId);

    /** soft-archive 身份（archived=true，保留行，衍生历史可读）。 */
    int archiveIdentity(@Param("id") Long id);

    /** 查询某市场下所有未归档身份（衍生查询辅助）。 */
    List<MarketSectorIdentityDO> selectActiveByMarket(@Param("marketCode") String marketCode);

    /**
     * 回填 market_sector_snapshot.sector_identity_id（按 watch+snapshot 自然键映射到身份）。
     * 只回填 NULL 行，不覆盖已有值。返回受影响行数。
     */
    int backfillSnapshotIdentity(@Param("providerCode") String providerCode,
                                 @Param("marketCode") String marketCode);

    /**
     * 回填 market_sector_member_snapshot.sector_identity_id。
     * 通过 member → snapshot → watch 链路映射到身份。返回受影响行数。
     */
    int backfillMemberSnapshotIdentity(@Param("providerCode") String providerCode,
                                       @Param("marketCode") String marketCode);
}
