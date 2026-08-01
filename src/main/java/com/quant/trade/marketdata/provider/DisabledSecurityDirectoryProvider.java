package com.quant.trade.marketdata.provider;

import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.constant.SecurityDirectoryConstants;

/**
 * Disabled 兜底 provider。provider 未启用时使用：isEnabled=false、isConfigured=false，
 * fetch 抛 {@link SecurityDirectoryProviderException}(PROVIDER_DISABLED)。
 * 应用可正常启动，D1 本地搜索/详情/导入和 /stocks CRUD 不受影响。
 */
public class DisabledSecurityDirectoryProvider implements SecurityDirectoryProvider {

    @Override
    public String getProviderCode() {
        return SecurityDirectoryConstants.PROVIDER_CODE_CSV_SNAPSHOT_DIR;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public DirectorySnapshot fetch(String mode) {
        throw new SecurityDirectoryProviderException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                SecurityDirectoryConstants.REASON_PROVIDER_DISABLED,
                "证券目录同步 provider 未启用");
    }
}
