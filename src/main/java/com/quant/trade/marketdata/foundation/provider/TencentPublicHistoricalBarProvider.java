package com.quant.trade.marketdata.foundation.provider;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.poc.PublicMarketDataClient;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TENCENT_PUBLIC 历史日 K 回补 Provider（EXPERIMENTAL，ADR-0015：非官方公共端点、无 SLA，
 * 只作实验/降级来源，不宣称生产稳定）。
 *
 * - 单位换算（MR-0 D6 冻结）：量手×100→股、额万元×10000→元、换手 %/100→小数；价格元、NONE 复权。
 * - 节流：任意两次外联之间保持最小间隔（throttleMs，默认 300ms）。
 * - 失败重试：指数退避（backoffBaseMs×2^n），最多 maxAttempts 次；HTTP 401/403/权限类不重试，
 *   立即抛 MARKET_DATA_PROVIDER_PERMISSION_DENIED。
 * - sleeper/nanoTime 可注入，单测零等待零联网。
 */
@Slf4j
@Component
public class TencentPublicHistoricalBarProvider implements HistoricalBarProvider {

    public static final String PROVIDER_CODE = "TENCENT_PUBLIC";

    private final PublicMarketDataClient client;
    private final long throttleMs;
    private final long backoffBaseMs;
    private final int maxAttempts;
    private final Sleeper sleeper;
    private final NanoClock nanoClock;
    private final AtomicLong lastCallNanos = new AtomicLong();

    /** 测试注入点：默认 Thread.sleep。 */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** 测试注入点：默认 System.nanoTime。 */
    public interface NanoClock {
        long nanoTime();
    }

    @Autowired
    public TencentPublicHistoricalBarProvider(
            PublicMarketDataClient client,
            @Value("${qta.data-foundation.throttle-ms:300}") long throttleMs,
            @Value("${qta.data-foundation.backoff-base-ms:500}") long backoffBaseMs,
            @Value("${qta.data-foundation.max-attempts:3}") int maxAttempts) {
        this(client, throttleMs, backoffBaseMs, maxAttempts,
                millis -> Thread.sleep(millis), System::nanoTime);
    }

    TencentPublicHistoricalBarProvider(PublicMarketDataClient client, long throttleMs, long backoffBaseMs,
                                       int maxAttempts, Sleeper sleeper, NanoClock nanoClock) {
        this.client = client;
        this.throttleMs = throttleMs;
        this.backoffBaseMs = backoffBaseMs;
        this.maxAttempts = maxAttempts;
        this.sleeper = sleeper;
        this.nanoClock = nanoClock;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String supportedAdjustType() {
        return "NONE";
    }

    @Override
    public List<ProviderDailyBar> getDailyBars(String canonicalSymbol, LocalDate start, LocalDate end) {
        String tencentCode = toTencentCode(canonicalSymbol);
        throttle();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return convert(canonicalSymbol, client.fetchDailyBars(tencentCode, start, end));
            } catch (RuntimeException exception) {
                if (isPermissionDenied(exception)) {
                    throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_PERMISSION_DENIED,
                            "公共源拒绝访问（401/403），不重试: " + truncate(exception.getMessage()));
                }
                last = exception;
                if (attempt < maxAttempts) {
                    backoff(attempt);
                    throttle();
                }
            }
        }
        throw new BusinessException(ErrorCodeEnum.MARKET_DATA_PROVIDER_TIMEOUT,
                "公共源日 K 拉取失败（已退避重试 " + maxAttempts + " 次）: " + truncate(last == null ? "" : last.getMessage()));
    }

    private List<ProviderDailyBar> convert(String canonicalSymbol, List<PublicMarketDataClient.DailyBarEntry> bars) {
        List<ProviderDailyBar> result = new ArrayList<>(bars.size());
        for (PublicMarketDataClient.DailyBarEntry bar : bars) {
            result.add(new ProviderDailyBar(
                    canonicalSymbol,
                    bar.getTradeDate(),
                    bar.getOpen(),
                    bar.getHigh(),
                    bar.getLow(),
                    bar.getClose(),
                    bar.getVolumeHands() == null ? null
                            : Long.valueOf(bar.getVolumeHands().multiply(java.math.BigDecimal.valueOf(100)).longValue()),
                    bar.getAmountTenThousand() == null ? null
                            : bar.getAmountTenThousand().multiply(java.math.BigDecimal.valueOf(10000)),
                    bar.getTurnoverPercent() == null ? null
                            : bar.getTurnoverPercent().divide(java.math.BigDecimal.valueOf(100), 8,
                            java.math.RoundingMode.HALF_UP)));
        }
        return result;
    }

    private void throttle() {
        if (throttleMs <= 0) {
            return;
        }
        long now = nanoClock.nanoTime();
        long previous = lastCallNanos.getAndSet(now);
        if (previous == 0) {
            return;
        }
        long elapsedMs = (now - previous) / 1_000_000;
        long wait = throttleMs - elapsedMs;
        if (wait > 0) {
            try {
                sleeper.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "回补节流等待被中断");
            }
        }
    }

    private void backoff(int attempt) {
        long delay = backoffBaseMs * (1L << (attempt - 1));
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.INTERNAL_ERROR, "回补退避等待被中断");
        }
    }

    private static boolean isPermissionDenied(RuntimeException exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("status=401") || message.contains("status=403"));
    }

    /** SH.600519 → sh600519（腾讯端点代码；SH.000001 指数同规则）。 */
    static String toTencentCode(String canonicalSymbol) {
        String normalized = CanonicalSymbolUtils.normalize(canonicalSymbol);
        String prefix = normalized.substring(0, 2).toLowerCase(Locale.ROOT);
        return prefix + normalized.substring(3);
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }
}
