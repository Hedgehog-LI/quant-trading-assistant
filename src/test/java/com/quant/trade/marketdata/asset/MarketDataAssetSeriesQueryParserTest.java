package com.quant.trade.marketdata.asset;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.asset.dto.MarketDataAssetSeriesQueryDTO;
import com.quant.trade.marketdata.asset.manager.MarketDataAssetSeriesQueryParser;
import com.quant.trade.marketdata.dao.MarketCalendarMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * {@link MarketDataAssetSeriesQueryParser} 分钟 K from/to 解析单测。
 * <p>
 * 折算到 Asia/Shanghai 后必须为整分钟（second 与 nano 均为 0），非整分钟抛
 * {@code VALIDATION_ERROR}，禁止静默取整；合法整分钟（bare 或带 offset）正常接受。
 */
@ExtendWith(MockitoExtension.class)
class MarketDataAssetSeriesQueryParserTest {

    @Mock
    private MarketCalendarMapper calendarMapper;

    private MarketDataAssetSeriesQueryParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarketDataAssetSeriesQueryParser(calendarMapper);
    }

    private MarketDataAssetSeriesQueryDTO parse(String from, String to) {
        return parser.parseAndValidate("5M", from, to, "NONE", "LONGPORT");
    }

    private static void assertValidationError(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCodeEnum.VALIDATION_ERROR);
    }

    // ==================== 合法整分钟（bare） ====================

    @Test
    void bareWholeMinuteWithoutSecondsAccepted() {
        MarketDataAssetSeriesQueryDTO dto = parse("2026-07-17T09:30", "2026-07-17T10:00");
        assertThat(dto.fromTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 9, 30));
        assertThat(dto.toTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 0));
        assertThat(dto.fromTime().getSecond()).isZero();
        assertThat(dto.fromTime().getNano()).isZero();
    }

    @Test
    void bareWholeMinuteWithZeroSecondsAccepted() {
        MarketDataAssetSeriesQueryDTO dto = parse("2026-07-17T09:30:00", "2026-07-17T10:00:00");
        assertThat(dto.fromTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 9, 30));
        assertThat(dto.toTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 0));
    }

    // ==================== 合法整分钟（offset，折算到 Asia/Shanghai） ====================

    @Test
    void offsetWholeMinuteConvertedToShanghaiAccepted() {
        // +07:00 折算到 Asia/Shanghai：09:30 → 10:30、10:00 → 11:00，均整分钟
        MarketDataAssetSeriesQueryDTO dto = parse("2026-07-17T09:30:00+07:00", "2026-07-17T10:00:00+07:00");
        assertThat(dto.fromTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 30));
        assertThat(dto.toTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 11, 0));
    }

    @Test
    void utcOffsetWholeMinuteConvertedToShanghaiAccepted() {
        // 01:30Z 折算到 Asia/Shanghai：09:30、10:30，均整分钟
        MarketDataAssetSeriesQueryDTO dto = parse("2026-07-17T01:30:00Z", "2026-07-17T02:30:00Z");
        assertThat(dto.fromTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 9, 30));
        assertThat(dto.toTime()).isEqualTo(LocalDateTime.of(2026, 7, 17, 10, 30));
    }

    // ==================== 非整分钟：非零秒 / 非零纳秒，禁止静默取整 ====================

    @Test
    void bareNonZeroSecondRejected() {
        assertValidationError(catchThrowable(() -> parse("2026-07-17T09:30:30", "2026-07-17T10:00:00")));
    }

    @Test
    void offsetNonZeroSecondRejected() {
        assertValidationError(catchThrowable(() -> parse("2026-07-17T09:30:30+08:00", "2026-07-17T10:00:00+08:00")));
    }

    @Test
    void bareNonZeroNanoRejected() {
        assertValidationError(catchThrowable(() -> parse("2026-07-17T09:30:00.123", "2026-07-17T10:00:00")));
    }

    @Test
    void offsetNonZeroNanoRejected() {
        assertValidationError(catchThrowable(() -> parse("2026-07-17T09:30:00.123+08:00", "2026-07-17T10:00:00+08:00")));
    }

    @Test
    void nonZeroSecondOnToSideRejected() {
        assertValidationError(catchThrowable(() -> parse("2026-07-17T09:30:00", "2026-07-17T10:00:30+08:00")));
    }
}
