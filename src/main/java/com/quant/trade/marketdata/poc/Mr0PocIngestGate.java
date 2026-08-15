package com.quant.trade.marketdata.poc;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * MR-0 PoC ingest 环境门禁（AC-02，REC-01）。ingest 执行必须同时满足：
 * <ol>
 *   <li>{@code qta.mr0-poc.ingest-enabled=true}（默认 false，本地脚本经命令行开启）；</li>
 *   <li>{@link Environment#getActiveProfiles()} 集合恰为 {@code {"local"}}（单元素恰等；
 *       {@code ["local","test"]}、{@code []}、{@code ["test"]}、{@code ["docker"]}、{@code ["prod"]}
 *       全部拒绝）。</li>
 * </ol>
 * Controller ingest 入口与 {@link Mr0PocIngestService#ingest} 入口各调用一次（双层防御，
 * 防绕过 controller 直调 service）。拒绝统一抛 {@link BusinessException}
 * （{@link ErrorCodeEnum#BUSINESS_RULE_VIOLATION}）→ 400 envelope，与未启用拒绝同一路径，
 * 不泄露内部细节；拒绝发生在任何 {@link PublicMarketDataClient} 交互之前。
 * 失效场景：容器/生产 profile 误开开关仍拒绝；脚本
 * {@code run-mr0-poc.sh} 以 {@code --spring.profiles.active=local} + 命令行开关启动，继续放行。
 */
@Component
public class Mr0PocIngestGate {

    /** 唯一放行 profile（单元素恰等，不做 contains 判断）。 */
    static final String LOCAL_PROFILE = "local";

    private final Environment environment;
    private final boolean ingestEnabled;

    public Mr0PocIngestGate(Environment environment,
                            @Value("${qta.mr0-poc.ingest-enabled:false}") boolean ingestEnabled) {
        this.environment = environment;
        this.ingestEnabled = ingestEnabled;
    }

    /** ingest 双门禁检查：开关 + active profiles 恰为 {"local"}；不满足即 400 BUSINESS_RULE_VIOLATION。 */
    public void checkIngestAllowed() {
        if (!ingestEnabled) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "MR-0 PoC ingest 未启用");
        }
        if (!isExactlyLocalProfile()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION,
                    "MR-0 PoC ingest 仅允许在 local profile 下执行");
        }
    }

    private boolean isExactlyLocalProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 1 && LOCAL_PROFILE.equals(activeProfiles[0]);
    }
}
