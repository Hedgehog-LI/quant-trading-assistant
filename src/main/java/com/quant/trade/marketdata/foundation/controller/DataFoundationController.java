package com.quant.trade.marketdata.foundation.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.foundation.dto.CreateBackfillTaskDTO;
import com.quant.trade.marketdata.foundation.dto.CreateDatasetDTO;
import com.quant.trade.marketdata.foundation.service.SnapshotImportService;
import com.quant.trade.marketdata.foundation.service.DataBackfillService;
import com.quant.trade.marketdata.foundation.service.DataFoundationDatasetService;
import com.quant.trade.marketdata.foundation.service.DataQualityService;
import com.quant.trade.marketdata.foundation.service.DatasetPublicationService;
import com.quant.trade.marketdata.foundation.vo.BackfillChunkVO;
import com.quant.trade.marketdata.foundation.vo.BackfillTaskVO;
import com.quant.trade.marketdata.foundation.vo.CoverageWatermarkVO;
import com.quant.trade.marketdata.foundation.vo.DatasetVO;
import com.quant.trade.marketdata.foundation.vo.DatasetVersionVO;
import com.quant.trade.marketdata.foundation.vo.ImportBatchVO;
import com.quant.trade.marketdata.foundation.vo.QualityResultVO;
import com.quant.trade.marketdata.vo.PageResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据底座 REST API（契约 AC-06，/api/v1/market-data/data-foundation/*）。
 *
 * 分层约束（T14）：controller 只做协议校验与响应映射，VO 装配在 service 层完成，
 * 本类不 import dao/model 持久化类型，也不注入任何 Mapper。
 *
 * 回补：创建/列表/详情/分片/启动继续/暂停/重试失败分片。
 * 数据集与版本：创建/列表/版本列表/手动建版本（导入类）/质量检查/发布。
 * 查询：覆盖水位、质量结果、导入批次。
 */
@RestController
@RequestMapping("/api/v1/market-data/data-foundation")
@RequiredArgsConstructor
public class DataFoundationController {

    private final DataFoundationDatasetService datasetService;
    private final DataBackfillService backfillService;
    private final DataQualityService qualityService;
    private final DatasetPublicationService publicationService;
    private final SnapshotImportService importService;

    // ---------------------------------------------------------------- 数据集

    @PostMapping("/datasets")
    public ApiResponse<DatasetVO> createDataset(@Valid @RequestBody CreateDatasetDTO dto) {
        return ApiResponse.ok(datasetService.toDatasetVO(datasetService.createDataset(dto.getDatasetCode(),
                dto.getDatasetName(), dto.getMarketCode(), dto.getBarType(), dto.getFrequency(),
                dto.getProviderCode(), dto.getAdjustType(), dto.getDescription())));
    }

    @GetMapping("/datasets")
    public ApiResponse<List<DatasetVO>> listDatasets() {
        return ApiResponse.ok(datasetService.listDatasets().stream().map(datasetService::toDatasetVO).toList());
    }

    @GetMapping("/datasets/{datasetCode}/versions")
    public ApiResponse<List<DatasetVersionVO>> listVersions(@PathVariable String datasetCode) {
        var dataset = datasetService.getDataset(datasetCode);
        return ApiResponse.ok(datasetService.listVersions(datasetCode).stream()
                .map(version -> datasetService.toVersionVO(version, dataset)).toList());
    }

    /** 手动建版本（仅导入类数据集）。 */
    @PostMapping("/datasets/{datasetCode}/versions")
    public ApiResponse<DatasetVersionVO> createVersion(@PathVariable String datasetCode,
                                                       @RequestBody Map<String, String> body) {
        LocalDate start = LocalDate.parse(required(body, "startDate"));
        LocalDate end = LocalDate.parse(required(body, "endDate"));
        var dataset = datasetService.getDataset(datasetCode);
        return ApiResponse.ok(datasetService.toVersionVO(
                datasetService.createVersion(datasetCode, start, end), dataset));
    }

    /** 当前已发布版本（无发布返回 data=null，前端显示未发布；R1 含血缘字段）。 */
    @GetMapping("/datasets/{datasetCode}/released")
    public ApiResponse<DatasetVersionVO> currentReleased(@PathVariable String datasetCode) {
        DatasetPublicationService.MdfDatasetVersionDTO released = publicationService.currentReleased(datasetCode);
        return ApiResponse.ok(released == null ? null : DatasetVersionVO.builder()
                .id(released.id()).datasetCode(released.datasetCode()).versionCode(released.versionCode())
                .status(released.status()).startDate(released.startDate()).endDate(released.endDate())
                .sourceProvider(released.sourceProvider()).rowCount(released.rowCount())
                .contentHash(released.contentHash()).manifestRowCount(released.manifestRowCount())
                .lineageStatus(released.lineageStatus())
                .isCurrentReleased(Boolean.TRUE).build());
    }

    // ---------------------------------------------------------------- 回补任务

    @PostMapping("/backfill-tasks")
    public ApiResponse<BackfillTaskVO> createBackfillTask(@Valid @RequestBody CreateBackfillTaskDTO dto) {
        return ApiResponse.ok(backfillService.toTaskVO(backfillService.createTask(dto.getDatasetCode(),
                dto.getMarketCode(), dto.getProviderCode(), dto.getFrequency(), dto.getAdjustType(),
                dto.getStartDate(), dto.getEndDate(), dto.getSymbols(), dto.getChunkSize())));
    }

    @GetMapping("/backfill-tasks")
    public ApiResponse<PageResultVO<BackfillTaskVO>> listBackfillTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        long total = backfillService.countTasks(status);
        List<BackfillTaskVO> items = backfillService.listTasks(status, page, pageSize).stream()
                .map(backfillService::toTaskVO).toList();
        return ApiResponse.ok(PageResultVO.of(items, total, page, pageSize));
    }

    @GetMapping("/backfill-tasks/{id}")
    public ApiResponse<BackfillTaskVO> getBackfillTask(@PathVariable long id) {
        return ApiResponse.ok(backfillService.toTaskVO(backfillService.getTask(id)));
    }

    @GetMapping("/backfill-tasks/{id}/chunks")
    public ApiResponse<List<BackfillChunkVO>> listChunks(@PathVariable long id) {
        return ApiResponse.ok(backfillService.listChunks(id).stream()
                .map(backfillService::toChunkVO).toList());
    }

    /** 启动或继续（R1：→QUEUED 立即返回，执行由后台 worker；断点续跑跳过终态分片）。 */
    @PostMapping("/backfill-tasks/{id}/run")
    public ApiResponse<BackfillTaskVO> runBackfillTask(@PathVariable long id) {
        return ApiResponse.ok(backfillService.toTaskVO(backfillService.run(id)));
    }

    @PostMapping("/backfill-tasks/{id}/pause")
    public ApiResponse<Void> pauseBackfillTask(@PathVariable long id) {
        backfillService.pause(id);
        return ApiResponse.ok(null);
    }

    /** 重试失败分片（FAILED→PENDING 后入队）。 */
    @PostMapping("/backfill-tasks/{id}/chunks/retry")
    public ApiResponse<BackfillTaskVO> retryFailedChunks(@PathVariable long id) {
        return ApiResponse.ok(backfillService.toTaskVO(backfillService.retryFailedChunks(id)));
    }

    // ---------------------------------------------------------------- 质量与发布

    @PostMapping("/dataset-versions/{id}/quality-check")
    public ApiResponse<List<QualityResultVO>> runQualityCheck(@PathVariable long id) {
        return ApiResponse.ok(qualityService.runChecks(id).stream().map(qualityService::toQualityVO).toList());
    }

    @PostMapping("/dataset-versions/{id}/publish")
    public ApiResponse<DatasetVersionVO> publishVersion(@PathVariable long id) {
        var version = publicationService.publish(id);
        return ApiResponse.ok(datasetService.toVersionVO(
                version, datasetService.getDatasetById(version.getDatasetId())));
    }

    @GetMapping("/dataset-versions/{id}/quality")
    public ApiResponse<List<QualityResultVO>> listQuality(@PathVariable long id) {
        return ApiResponse.ok(qualityService.listResults(id).stream().map(qualityService::toQualityVO).toList());
    }

    @GetMapping("/dataset-versions/{id}/coverage")
    public ApiResponse<List<CoverageWatermarkVO>> listCoverage(@PathVariable long id) {
        return ApiResponse.ok(qualityService.listCoverage(id).stream().map(qualityService::toCoverageVO).toList());
    }

    // ---------------------------------------------------------------- 导入

    @PostMapping("/imports")
    public ApiResponse<ImportBatchVO> importSnapshot(@RequestParam String kind,
                                                     @RequestParam(required = false) Long datasetVersionId,
                                                     @RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(importService.toImportVO(
                importService.importSnapshot(kind, datasetVersionId, file.getOriginalFilename(), file.getBytes())));
    }

    @GetMapping("/imports")
    public ApiResponse<List<ImportBatchVO>> listImports(@RequestParam(required = false) String kind,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(importService.listBatches(kind, page, pageSize).stream()
                .map(importService::toImportVO).toList());
    }

    @GetMapping("/imports/{id}")
    public ApiResponse<ImportBatchVO> getImport(@PathVariable long id) {
        return ApiResponse.ok(importService.toImportVO(importService.getBatch(id)));
    }

    // ---------------------------------------------------------------- 参数校验

    private static String required(Map<String, String> body, String key) {
        String value = body == null ? null : body.get(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, key + " 不能为空");
        }
        return value;
    }
}
