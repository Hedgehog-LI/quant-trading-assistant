package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.constant.ApiConstants;
import com.quant.trade.marketdata.service.MarketSectorCatalogService;
import com.quant.trade.marketdata.service.MarketSectorRankingService;
import com.quant.trade.marketdata.service.MarketSectorWatchService;
import com.quant.trade.marketdata.dto.CreateMarketSectorWatchDTO;
import com.quant.trade.marketdata.dto.UpdateMarketSectorRankingConfigDTO;
import com.quant.trade.marketdata.dto.UpdateMarketSectorWatchCollectionDTO;
import com.quant.trade.marketdata.vo.MarketSectorMemberSnapshotVO;
import com.quant.trade.marketdata.vo.MarketSectorSnapshotVO;
import com.quant.trade.marketdata.vo.MarketSectorWatchVO;
import com.quant.trade.marketdata.vo.MarketSectorRankingBatchVO;
import com.quant.trade.marketdata.vo.MarketSectorRankingConfigVO;
import com.quant.trade.marketdata.vo.MarketSectorRankingItemVO;
import com.quant.trade.marketdata.vo.PageResultVO;
import jakarta.validation.Valid;
import com.quant.trade.marketdata.vo.MarketSectorPeerVO;
import com.quant.trade.marketdata.vo.MarketSectorRankVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;

/** 市场板块发现接口，与手工维护的自定义分组分离。 */
@RestController
@RequestMapping(ApiConstants.API_V1 + "/market-data/sector-catalog")
@RequiredArgsConstructor
public class MarketSectorCatalogController {

    private final MarketSectorCatalogService service;
    private final MarketSectorWatchService watchService;
    private final MarketSectorRankingService rankingService;

    @GetMapping("/industry-rankings")
    public ApiResponse<List<MarketSectorRankVO>> industryRankings(
            @RequestParam(defaultValue = "CN") String market,
            @RequestParam(defaultValue = "leading-gainer") String indicator,
            @RequestParam(defaultValue = "single") String sortType,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.getIndustryRank(market, indicator, sortType, limit));
    }

    @GetMapping("/industry-peers")
    public ApiResponse<MarketSectorPeerVO> industryPeers(@RequestParam String market,
                                                          @RequestParam String providerSectorId) {
        return ApiResponse.ok(service.getIndustryPeers(market, providerSectorId));
    }

    @PostMapping("/watches")
    public ApiResponse<MarketSectorWatchVO> createWatch(@Valid @RequestBody CreateMarketSectorWatchDTO dto) {
        return ApiResponse.ok(watchService.create(dto));
    }

    @GetMapping("/watches")
    public ApiResponse<List<MarketSectorWatchVO>> listWatches(@RequestParam(required = false) String market) {
        return ApiResponse.ok(watchService.list(market));
    }

    @GetMapping("/watches/{id}")
    public ApiResponse<MarketSectorWatchVO> getWatch(@PathVariable Long id) {
        return ApiResponse.ok(watchService.get(id));
    }

    @PostMapping("/watches/{id}/refresh")
    public ApiResponse<MarketSectorWatchVO> refreshWatch(@PathVariable Long id) {
        return ApiResponse.ok(watchService.refresh(id));
    }

    @PostMapping("/watches/{id}/toggle")
    public ApiResponse<MarketSectorWatchVO> toggleWatch(@PathVariable Long id,
                                                         @RequestParam boolean enabled) {
        return ApiResponse.ok(watchService.setEnabled(id, enabled));
    }

    @PutMapping("/watches/{id}/collection")
    public ApiResponse<MarketSectorWatchVO> updateWatchCollection(
            @PathVariable Long id, @Valid @RequestBody UpdateMarketSectorWatchCollectionDTO dto) {
        return ApiResponse.ok(watchService.updateCollection(id, dto));
    }

    @DeleteMapping("/watches/{id}")
    public ApiResponse<Void> deleteWatch(@PathVariable Long id) {
        watchService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/watches/{id}/snapshots")
    public ApiResponse<PageResultVO<MarketSectorSnapshotVO>> snapshots(
            @PathVariable Long id, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(watchService.snapshots(id, page, size));
    }

    @GetMapping("/snapshots/{snapshotId}/members")
    public ApiResponse<List<MarketSectorMemberSnapshotVO>> members(@PathVariable Long snapshotId) {
        return ApiResponse.ok(watchService.members(snapshotId));
    }

    @GetMapping("/ranking-configs")
    public ApiResponse<List<MarketSectorRankingConfigVO>> rankingConfigs() {
        return ApiResponse.ok(rankingService.configs());
    }

    @PutMapping("/ranking-configs/{market}")
    public ApiResponse<MarketSectorRankingConfigVO> updateRankingConfig(
            @PathVariable String market, @Valid @RequestBody UpdateMarketSectorRankingConfigDTO dto) {
        return ApiResponse.ok(rankingService.updateConfig(market, dto));
    }

    @PostMapping("/ranking-configs/{market}/run")
    public ApiResponse<MarketSectorRankingBatchVO> runRanking(
            @PathVariable String market) {
        return ApiResponse.ok(rankingService.collectNow(market));
    }

    @GetMapping("/ranking-history")
    public ApiResponse<PageResultVO<MarketSectorRankingBatchVO>> rankingHistory(
            @RequestParam(required = false) String market,
            @RequestParam(required = false) LocalDate tradeDate,
            @RequestParam(required = false) String snapshotType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(rankingService.history(market, tradeDate, snapshotType, page, size));
    }

    @GetMapping("/ranking-history/{batchId}/items")
    public ApiResponse<List<MarketSectorRankingItemVO>> rankingItems(
            @PathVariable Long batchId) {
        return ApiResponse.ok(rankingService.items(batchId));
    }
}
