package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.MarketDataConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** LongPort 未启用时的板块能力降级实现。 */
@Component
@ConditionalOnProperty(prefix = "qta.market-data.longport", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMarketSectorProvider implements MarketSectorProvider {

    @Override
    public String getProviderCode() {
        return MarketDataConstants.PROVIDER_CODE_LONGPORT;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public List<SectorRank> getIndustryRank(String market, String indicator, String sortType, int limit) {
        throw unavailable();
    }

    @Override
    public SectorPeer getIndustryPeers(String market, String counterId) {
        throw unavailable();
    }

    @Override
    public SectorConstituents getIndustryConstituents(String counterId) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCodeEnum.MARKET_SECTOR_PROVIDER_UNAVAILABLE,
                "LongPort 板块数据源未启用");
    }
}
