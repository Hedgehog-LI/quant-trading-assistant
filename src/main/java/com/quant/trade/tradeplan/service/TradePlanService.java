package com.quant.trade.tradeplan.service;

import com.quant.trade.common.enums.PlanStatusEnum;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.tradeplan.convert.TradePlanConverter;
import com.quant.trade.tradeplan.dto.CreateTradePlanDTO;
import com.quant.trade.tradeplan.dto.UpdatePlanStatusDTO;
import com.quant.trade.tradeplan.dto.UpdateTradePlanDTO;
import com.quant.trade.tradeplan.manager.TradePlanManager;
import com.quant.trade.tradeplan.model.TradePlanDO;
import com.quant.trade.tradeplan.vo.TradePlanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易计划应用服务。
 * <p>
 * 负责事务边界和业务流程编排，核心校验和 DB 读写委托给 {@link TradePlanManager}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanService {

    private final TradePlanManager tradePlanManager;
    private final TradePlanConverter tradePlanConverter;

    @Transactional
    public TradePlanVO create(CreateTradePlanDTO dto) {
        TradePlanDO record = tradePlanConverter.toDO(dto);
        // symbol trim + 大写
        if (StringUtils.isNotBlank(record.getSymbol())) {
            record.setSymbol(record.getSymbol().trim().toUpperCase());
        }
        // 默认值
        if (record.getAllowedToTrade() == null) {
            record.setAllowedToTrade(false);
        }

        tradePlanManager.validateForCreate(record);
        tradePlanManager.insert(record);

        log.info("Created trade plan: symbol={}, date={}", record.getSymbol(), record.getPlanDate());
        return tradePlanConverter.toVO(record);
    }

    public List<TradePlanVO> list(LocalDate date, String symbol) {
        List<TradePlanDO> records = tradePlanManager.listByFilter(date, symbol);
        return tradePlanConverter.toVOList(records);
    }

    public TradePlanVO getById(Long id) {
        TradePlanDO record = tradePlanManager.getByIdOrThrow(id);
        return tradePlanConverter.toVO(record);
    }

    @Transactional
    public TradePlanVO update(Long id, UpdateTradePlanDTO dto) {
        TradePlanDO existing = tradePlanManager.getByIdOrThrow(id);

        // 合并 DTO 到已有记录
        tradePlanConverter.updateDOFromDTO(dto, existing);
        existing.setId(id);

        // 校验合并后的结果
        tradePlanManager.validateForUpdate(existing, existing);

        tradePlanManager.updateById(existing);

        log.info("Updated trade plan: id={}", id);
        return tradePlanConverter.toVO(tradePlanManager.selectById(id));
    }

    @Transactional
    public TradePlanVO updateStatus(Long id, UpdatePlanStatusDTO dto) {
        if (!PlanStatusEnum.isValid(dto.planStatus())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                    "Invalid planStatus: " + dto.planStatus());
        }

        TradePlanDO existing = tradePlanManager.getByIdOrThrow(id);
        existing.setPlanStatus(dto.planStatus());
        tradePlanManager.updateById(existing);

        log.info("Updated trade plan status: id={}, status={}", id, dto.planStatus());
        return tradePlanConverter.toVO(tradePlanManager.selectById(id));
    }

    public long countActivePlansByDate(LocalDate date) {
        return tradePlanManager.countActiveByDate(date);
    }

    /**
     * 物理删除交易计划。
     */
    @Transactional
    public void delete(Long id) {
        tradePlanManager.deleteById(id);
        log.info("Deleted trade plan: id={}", id);
    }
}
