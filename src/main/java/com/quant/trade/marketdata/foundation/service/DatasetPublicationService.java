package com.quant.trade.marketdata.foundation.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据集发布门禁（契约 AC-05）。
 *
 * 事务内原子切换：同 dataset 旧 RELEASED→RETIRED、目标版本→RELEASED、dataset.current_version_id 指针更新。
 * 失败版本（REJECTED/未跑质量）不得发布；失败版本永远不会成为研究默认版本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetPublicationService {

    private final MdfDatasetMapper datasetMapper;
    private final MdfDatasetVersionMapper versionMapper;
    private final DataQualityService qualityService;
    private final VersionLineageService lineageService;
    private final TransactionTemplate txRequiresNew;
    private final Clock marketDataClock;

    /**
     * 发布：必须已 QUALIFIED（未跑质量先拒绝）；R1 §六——已冻结版本先做漂移校验
     * （漂移→DRIFTED+拒绝），未冻结版本先冻结 manifest/content hash，再事务内原子切换指针。
     */
    public MdfDatasetVersionDO publish(long versionId) {
        MdfDatasetVersionDO version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_VERSION_NOT_FOUND, "数据集版本不存在");
        }
        if (!FoundationConstants.VERSION_QUALIFIED.equals(version.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED,
                    "版本状态为 " + version.getStatus() + "，只有 QUALIFIED 版本可发布（先运行质量检查）");
        }
        if (qualityService.countFail(versionId) > 0) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED,
                    "存在 FAIL 质量结果，禁止发布");
        }
        if (version.getContentHash() != null) {
            long drifted = lineageService.countDrifted(versionId);
            if (drifted > 0) {
                lineageService.markDrifted(versionId);
                throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_QUALITY_GATE_FAILED,
                        "版本底层事实已漂移（" + drifted + " 行不一致），禁止发布；如需发布请以新事实重建版本");
            }
        } else {
            lineageService.freeze(versionId);
        }
        LocalDateTime now = LocalDateTime.now(marketDataClock);
        txRequiresNew.executeWithoutResult(status -> {
            // 同 dataset 旧 RELEASED → RETIRED（旧版本保留可查，不删除）
            MdfDatasetDO dataset = datasetMapper.selectById(version.getDatasetId());
            for (MdfDatasetVersionDO candidate : versionMapper.selectByDatasetId(dataset.getId())) {
                if (FoundationConstants.VERSION_RELEASED.equals(candidate.getStatus())
                        && !candidate.getId().equals(versionId)) {
                    versionMapper.updateStatus(candidate.getId(), FoundationConstants.VERSION_RETIRED,
                            null, null, null);
                }
            }
            versionMapper.updateStatus(versionId, FoundationConstants.VERSION_RELEASED, null, now, null);
            datasetMapper.updateCurrentVersion(dataset.getId(), versionId);
        });
        log.info("数据集版本发布: versionId={}, datasetId={}", versionId, version.getDatasetId());
        return versionMapper.selectById(versionId);
    }

    /** 当前已发布版本（数据集不存在抛 NOT_FOUND；存在但未发布返回 null，前端显示"未发布"）。 */
    public MdfDatasetVersionDTO currentReleased(String datasetCode) {
        MdfDatasetDO dataset = datasetMapper.selectByCode(datasetCode);
        if (dataset == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_NOT_FOUND, "数据集不存在: " + datasetCode);
        }
        if (dataset.getCurrentVersionId() == null) {
            return null;
        }
        MdfDatasetVersionDO version = versionMapper.selectById(dataset.getCurrentVersionId());
        return version == null ? null : new MdfDatasetVersionDTO(version, dataset);
    }

    public List<MdfDatasetVersionDTO> listVersionsWithDataset(String datasetCode) {
        MdfDatasetDO dataset = datasetMapper.selectByCode(datasetCode);
        if (dataset == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_NOT_FOUND, "数据集不存在");
        }
        return versionMapper.selectByDatasetId(dataset.getId()).stream()
                .map(version -> new MdfDatasetVersionDTO(version, dataset))
                .toList();
    }

    /** 版本 + 所属数据集只读 DTO（前端展示用；R1 含血缘字段）。 */
    public record MdfDatasetVersionDTO(Long id, String datasetCode, String datasetName, String versionCode,
                                       String status, java.time.LocalDate startDate, java.time.LocalDate endDate,
                                       String sourceProvider, Long rowCount, Long currentVersionId,
                                       String contentHash, Long manifestRowCount, String lineageStatus) {
        public MdfDatasetVersionDTO(MdfDatasetVersionDO version, MdfDatasetDO dataset) {
            this(version.getId(), dataset.getDatasetCode(), dataset.getDatasetName(), version.getVersionCode(),
                    version.getStatus(), version.getStartDate(), version.getEndDate(),
                    version.getSourceProvider(), version.getRowCount(), dataset.getCurrentVersionId(),
                    version.getContentHash(), version.getManifestRowCount(), version.getLineageStatus());
        }
    }
}
