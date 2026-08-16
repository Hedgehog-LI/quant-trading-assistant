package com.quant.trade.marketdata.foundation.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.FoundationConstants;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetMapper;
import com.quant.trade.marketdata.foundation.dao.MdfDatasetVersionMapper;
import com.quant.trade.marketdata.foundation.model.MdfDatasetDO;
import com.quant.trade.marketdata.foundation.model.MdfDatasetVersionDO;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProvider;
import com.quant.trade.marketdata.foundation.provider.HistoricalBarProviderRegistry;
import com.quant.trade.marketdata.foundation.vo.DatasetVO;
import com.quant.trade.marketdata.foundation.vo.DatasetVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

/**
 * 数据集定义与版本查询。首期仅支持日 K（bar_type=DAILY、frequency=1D、adjust=NONE 有 Provider 支撑；
 * HFQ/QFQ 数据集在无 Provider 支撑时创建即拒绝，契约 D4）。
 */
@Service
@RequiredArgsConstructor
public class DataFoundationDatasetService {

    private static final Set<String> SUPPORTED_BAR_TYPES = Set.of("DAILY");
    private static final Set<String> SUPPORTED_FREQUENCIES = Set.of("1D");
    private static final Set<String> SUPPORTED_ADJUST = Set.of("NONE");

    private final MdfDatasetMapper datasetMapper;
    private final MdfDatasetVersionMapper versionMapper;
    private final HistoricalBarProviderRegistry providerRegistry;
    private final TransactionTemplate txRequiresNew;

    public MdfDatasetDO createDataset(String datasetCode, String datasetName, String marketCode,
                                      String barType, String frequency, String providerCode,
                                      String adjustType, String description) {
        if (datasetMapper.selectByCode(datasetCode) != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_RESOURCE, "数据集已存在: " + datasetCode);
        }
        if (!SUPPORTED_BAR_TYPES.contains(barType) || !SUPPORTED_FREQUENCIES.contains(frequency)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE, "首期仅支持 DAILY/1D");
        }
        if (providerCode.startsWith("IMPORT_")) {
            // 导入类数据集：无网络 Provider，仅承载版本/质量/发布语义；不通过回补引擎执行。
            if (!SUPPORTED_ADJUST.contains(adjustType)) {
                throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE, "首期仅支持 NONE 复权");
            }
        } else {
            HistoricalBarProvider provider = providerRegistry.require(providerCode);
            if (!SUPPORTED_ADJUST.contains(adjustType) || !provider.supportedAdjustType().equals(adjustType)) {
                throw new BusinessException(ErrorCodeEnum.INVALID_ENUM_CODE,
                        "复权口径无 Provider 支撑（首期仅 NONE）: adjustType=" + adjustType);
            }
        }
        MdfDatasetDO dataset = MdfDatasetDO.builder()
                .datasetCode(datasetCode).datasetName(datasetName).marketCode(marketCode)
                .barType(barType).frequency(frequency).providerCode(providerCode).adjustType(adjustType)
                .unitCaliber("价格=元，volume=股，amount=元，换手率=小数（ADR-0015 单位冻结）")
                .description(description).build();
        txRequiresNew.executeWithoutResult(status -> datasetMapper.insert(dataset));
        return datasetMapper.selectByCode(datasetCode);
    }

    public List<MdfDatasetDO> listDatasets() {
        return datasetMapper.selectAll();
    }

    public MdfDatasetDO getDataset(String datasetCode) {
        MdfDatasetDO dataset = datasetMapper.selectByCode(datasetCode);
        if (dataset == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_NOT_FOUND, "数据集不存在: " + datasetCode);
        }
        return dataset;
    }

    public MdfDatasetDO getDatasetById(long datasetId) {
        MdfDatasetDO dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_NOT_FOUND, "数据集不存在: id=" + datasetId);
        }
        return dataset;
    }

    /** 内置默认数据集（幂等；本地/运行时验证用）。 */
    public MdfDatasetDO ensureDefaultDataset() {
        MdfDatasetDO existing = datasetMapper.selectByCode(FoundationConstants.DATASET_CN_DAILY);
        if (existing != null) {
            return existing;
        }
        return createDataset(FoundationConstants.DATASET_CN_DAILY, "A股日K历史数据集（TENCENT_PUBLIC 实验源）",
                "CN", "DAILY", "1D", "TENCENT_PUBLIC", "NONE",
                "全A日K底座；TENCENT_PUBLIC 为实验性公共源（ADR-0015），生产化需另立 ADR");
    }

    public MdfDatasetVersionDO getVersion(long versionId) {
        MdfDatasetVersionDO version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_VERSION_NOT_FOUND, "数据集版本不存在");
        }
        return version;
    }

    /**
     * 手动创建版本（仅导入类数据集：provider IMPORT_*；回补数据集版本由回补任务自动创建）。
     * 导入完成后对版本跑质量检查与发布。
     */
    public MdfDatasetVersionDO createVersion(String datasetCode, java.time.LocalDate startDate,
                                             java.time.LocalDate endDate) {
        MdfDatasetDO dataset = getDataset(datasetCode);
        if (!dataset.getProviderCode().startsWith("IMPORT_")) {
            throw new BusinessException(ErrorCodeEnum.DATA_FOUNDATION_DATASET_CONFLICT,
                    "非导入类数据集的版本由回补任务自动创建，不支持手动建版本");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCodeEnum.VALIDATION_ERROR, "startDate 不能晚于 endDate");
        }
        int nextSeq = versionMapper.selectMaxVersionSeq(dataset.getId()) + 1;
        MdfDatasetVersionDO version = MdfDatasetVersionDO.builder()
                .datasetId(dataset.getId()).versionCode("v" + nextSeq)
                .status(FoundationConstants.VERSION_DRAFT)
                .startDate(startDate).endDate(endDate)
                .sourceProvider(dataset.getProviderCode()).rowCount(0L)
                .sourceNote("manual import version").build();
        txRequiresNew.executeWithoutResult(status -> versionMapper.insert(version));
        return versionMapper.selectById(version.getId());
    }

    public List<MdfDatasetVersionDO> listVersions(String datasetCode) {
        return versionMapper.selectByDatasetId(getDataset(datasetCode).getId());
    }

    // ---------------------------------------------------------------- VO 装配（controller 不接触持久化模型）

    public DatasetVO toDatasetVO(MdfDatasetDO dataset) {
        return DatasetVO.builder()
                .id(dataset.getId()).datasetCode(dataset.getDatasetCode()).datasetName(dataset.getDatasetName())
                .marketCode(dataset.getMarketCode()).barType(dataset.getBarType()).frequency(dataset.getFrequency())
                .providerCode(dataset.getProviderCode()).adjustType(dataset.getAdjustType())
                .unitCaliber(dataset.getUnitCaliber()).description(dataset.getDescription())
                .currentVersionId(dataset.getCurrentVersionId()).createdAt(dataset.getCreatedAt())
                .build();
    }

    public DatasetVersionVO toVersionVO(MdfDatasetVersionDO version, MdfDatasetDO dataset) {
        return DatasetVersionVO.builder()
                .id(version.getId()).datasetId(version.getDatasetId()).datasetCode(dataset.getDatasetCode())
                .versionCode(version.getVersionCode()).status(version.getStatus())
                .startDate(version.getStartDate()).endDate(version.getEndDate())
                .sourceProvider(version.getSourceProvider()).sourceNote(version.getSourceNote())
                .rowCount(version.getRowCount()).qualifiedAt(version.getQualifiedAt())
                .releasedAt(version.getReleasedAt()).createdAt(version.getCreatedAt())
                .isCurrentReleased(dataset.getCurrentVersionId() != null
                        && dataset.getCurrentVersionId().equals(version.getId()))
                .build();
    }
}
