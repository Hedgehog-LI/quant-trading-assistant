package com.quant.trade.watchlist.service;

import com.quant.trade.watchlist.convert.WatchlistConverter;
import com.quant.trade.watchlist.dto.CreateWatchlistDTO;
import com.quant.trade.watchlist.dto.UpdateEnabledDTO;
import com.quant.trade.watchlist.dto.UpdateWatchlistDTO;
import com.quant.trade.watchlist.manager.WatchlistManager;
import com.quant.trade.watchlist.model.WatchlistDO;
import com.quant.trade.watchlist.vo.WatchlistVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自选股应用服务。
 * <p>
 * 负责事务边界和业务流程编排，核心校验逻辑委托给 {@link WatchlistManager}，
 * DB 读写统一通过 Manager 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistManager watchlistManager;
    private final WatchlistConverter watchlistConverter;

    /**
     * 新增自选股。
     */
    @Transactional
    public WatchlistVO create(CreateWatchlistDTO dto) {
        WatchlistDO record = watchlistConverter.toDO(dto);

        watchlistManager.validateAndNormalizeForCreate(record);
        watchlistManager.insert(record);

        log.info("Created watchlist: symbol={}, name={}", record.getSymbol(), record.getName());
        return watchlistConverter.toVO(record);
    }

    /**
     * 按条件筛选自选股列表。
     */
    public List<WatchlistVO> list(Boolean enabled, String keyword, String tradeStyle) {
        List<WatchlistDO> records = watchlistManager.listByFilter(enabled, keyword, tradeStyle);
        return watchlistConverter.toVOList(records);
    }

    /**
     * 根据 ID 查询自选股。
     */
    public WatchlistVO getById(Long id) {
        WatchlistDO record = watchlistManager.getByIdOrThrow(id);
        return watchlistConverter.toVO(record);
    }

    /**
     * 更新自选股。
     */
    @Transactional
    public WatchlistVO update(Long id, UpdateWatchlistDTO dto) {
        WatchlistDO existing = watchlistManager.getByIdOrThrow(id);

        // 合并 DTO 到已有记录（仅覆盖非 null 字段）
        watchlistConverter.updateDOFromDTO(dto, existing);
        existing.setId(id);

        // 校验合并后的结果
        watchlistManager.validateForUpdate(existing, existing);

        watchlistManager.updateById(existing);

        log.info("Updated watchlist: id={}", id);
        return watchlistConverter.toVO(watchlistManager.selectById(id));
    }

    /**
     * 更新启用状态。
     */
    @Transactional
    public WatchlistVO updateEnabled(Long id, UpdateEnabledDTO dto) {
        WatchlistDO existing = watchlistManager.getByIdOrThrow(id);
        existing.setEnabled(dto.enabled());
        watchlistManager.updateById(existing);

        log.info("Updated watchlist enabled: id={}, enabled={}", id, dto.enabled());
        return watchlistConverter.toVO(watchlistManager.selectById(id));
    }

    /**
     * 软删除自选股（设置 enabled=false）。
     */
    @Transactional
    public void delete(Long id) {
        WatchlistDO existing = watchlistManager.getByIdOrThrow(id);
        existing.setEnabled(false);
        watchlistManager.updateById(existing);

        log.info("Soft deleted watchlist: id={}, symbol={}", id, existing.getSymbol());
    }
}
