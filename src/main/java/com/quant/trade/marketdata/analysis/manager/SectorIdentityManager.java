package com.quant.trade.marketdata.analysis.manager;

import com.quant.trade.marketdata.analysis.dao.MarketSectorIdentityMapper;
import com.quant.trade.marketdata.analysis.model.MarketSectorIdentityDO;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 板块稳定身份管理（设计 §6.1）。
 *
 * <p>两步声明：(1) {@code INSERT IGNORE} 锚点；(2) {@code SELECT ... FOR UPDATE} 锁定锚点；
 * (3) 校验所有 taxonomy version 的区间不重叠后写入。READ COMMITTED 下保证同一锚点单写者。</p>
 *
 * <p><b>身份稳定性</b>：数值 {@code id} 是 API 唯一 {@code sectorId}。watch 删除/重建不改变身份——
 * watch 只是关注关系，soft-archive 身份保留历史快照。衍生层只通过 {@code sector_identity_id} 查询，
 * watch_id 永不进入衍生幂等键或跨表 JOIN。</p>
 *
 * <p>H2/MODE=MySQL 注意：无法复现 InnoDB gap-lock 顺序；并发层断言 OUTCOME（行数 + 唯一约束），
 * 真实 FOR UPDATE 争用属 RUNTIME NOT_VERIFIED（已在 AC-02 声明）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SectorIdentityManager {

    private final MarketSectorIdentityMapper identityMapper;

    /**
     * 声明（或复用）板块稳定身份。
     *
     * <p>事务内：INSERT IGNORE 锚点 → SELECT FOR UPDATE → 区间重叠检查 → 写入。
     * 自然键已存在时直接返回既有身份（幂等）。区间重叠时拒绝并抛 {@link ErrorCodeEnum#PARAM_ERROR}。</p>
     *
     * @return 稳定身份（含 sectorId）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public MarketSectorIdentityDO claimIdentity(String providerCode, String marketCode,
                                                String providerSectorId, String taxonomyVersion,
                                                String sectorName, LocalDate validFrom, LocalDate validTo) {
        // 1. INSERT IGNORE 锚点（幂等，已存在不报错）
        identityMapper.insertIgnoreLockAnchor(providerCode, marketCode, providerSectorId);
        // 2. SELECT FOR UPDATE 锁定锚点（READ COMMITTED 下与 INSERT IGNORE 配合实现单写者）
        Long anchorId = identityMapper.selectLockAnchorForUpdate(providerCode, marketCode, providerSectorId);
        if (anchorId == null) {
            // 理论不可达（INSERT IGNORE 刚成功）；防御性 fail-closed
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "板块身份锚点声明失败");
        }
        // 3. 自然键已存在 → 幂等返回（不覆盖既有区间）
        MarketSectorIdentityDO existing = identityMapper.selectByNaturalKey(
                providerCode, marketCode, providerSectorId, taxonomyVersion);
        if (existing != null) {
            return existing;
        }
        // 4. 跨 taxonomy version 区间重叠检查（左闭右开）
        int overlaps = identityMapper.countOverlappingIntervals(
                providerCode, marketCode, providerSectorId, validFrom, validTo);
        if (overlaps > 0) {
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR,
                    "板块身份区间与既有 taxonomy 重叠: provider=" + providerCode
                            + ", sector=" + providerSectorId);
        }
        // 5. 写入新身份
        MarketSectorIdentityDO record = MarketSectorIdentityDO.builder()
                .providerCode(providerCode)
                .marketCode(marketCode)
                .providerSectorId(providerSectorId)
                .taxonomyVersion(taxonomyVersion)
                .sectorName(sectorName)
                .validFrom(validFrom)
                .validTo(validTo)
                .archived(false)
                .build();
        identityMapper.insertIdentity(record);
        log.info("声明板块稳定身份: provider={}, market={}, sector={}, taxonomy={}, sectorId={}",
                providerCode, marketCode, providerSectorId, taxonomyVersion, record.getId());
        return record;
    }

    /** 按自然键查询身份（衍生层稳定身份查询，不 JOIN watch_id）。 */
    public MarketSectorIdentityDO findByNaturalKey(String providerCode, String marketCode,
                                                   String providerSectorId, String taxonomyVersion) {
        return identityMapper.selectByNaturalKey(providerCode, marketCode, providerSectorId, taxonomyVersion);
    }

    /** 按数值 sectorId 查询身份。 */
    public MarketSectorIdentityDO findById(Long sectorId) {
        return identityMapper.selectById(sectorId);
    }

    /**
     * Soft-archive 身份（archived=true，保留行与历史快照）。
     * 不物理删除——衍生历史仍可按 sector_identity_id 回读。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void archiveIdentity(Long sectorId) {
        identityMapper.archiveIdentity(sectorId);
        log.info("归档板块身份: sectorId={}", sectorId);
    }

    /**
     * 回填既有快照的 sector_identity_id（market_sector_snapshot + market_sector_member_snapshot）。
     * 只回填 NULL 行；不覆盖既有值，不删除 V14 cascade FK。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void backfillExistingSnapshots(String providerCode, String marketCode) {
        int snapshotUpdated = identityMapper.backfillSnapshotIdentity(providerCode, marketCode);
        int memberUpdated = identityMapper.backfillMemberSnapshotIdentity(providerCode, marketCode);
        log.info("回填板块身份引用: provider={}, market={}, snapshotRows={}, memberRows={}",
                providerCode, marketCode, snapshotUpdated, memberUpdated);
    }
}
