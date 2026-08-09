package com.quant.trade.marketdata.dao;

import com.quant.trade.marketdata.model.StockBasicDO;
import com.quant.trade.marketdata.model.SecuritySearchCandidateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockBasicMapper {
    int insert(StockBasicDO record);
    int insertDirectory(StockBasicDO record);
    int updateById(StockBasicDO record);
    int updateDirectoryById(StockBasicDO record);
    StockBasicDO selectById(@Param("id") Long id);
    StockBasicDO selectByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
    List<StockBasicDO> selectByFilter(@Param("market") String market, @Param("keyword") String keyword,
                                      @Param("limit") int limit, @Param("offset") int offset);
    long countByFilter(@Param("market") String market, @Param("keyword") String keyword);
    int deleteByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol);
    List<StockBasicDO> selectByCanonicalSymbols(@Param("ids") List<String> canonicalSymbols);
    List<SecuritySearchCandidateDO> searchCandidates(
            @Param("query") String normalizedQuery,
            @Param("likeQuery") String escapedLikeQuery,
            @Param("queryUpper") String queryUpper,
            @Param("canonicalQuery") String canonicalQuery,
            @Param("hkSymbol") String hkSymbol,
            @Param("markets") List<String> markets,
            @Param("types") List<String> types,
            @Param("includeDelisted") boolean includeDelisted);
    long countAll();
    java.time.LocalDateTime selectMaxSourceUpdatedAt();

    /**
     * D3-03 enricher 原子条件更新：仅在数据库当前字段仍为 null/空字符串/纯空白时写入。
     * <p>
     * 数据库层保证「本地已有非空字段永不被覆盖」：SQL 用 CASE WHEN 逐列判断，目录同步
     * 在 LongPort 调用期间写入的非空值会被保留，不依赖调用前读取结果。本方法<b>不修改</b>
     * {@code source_updated_at} / {@code data_source} / {@code source_hash}，不污染目录新鲜度。
     * <p>
     * 必须检查受影响行数：为 0 时由 service 重新查询区分「行不存在（404）」与
     * 「行存在但无可补字段（NO_CHANGE）」。
     *
     * @param canonicalSymbol 本系统统一代码
     * @param nameCn          中文名称，null 表示不更新该列
     * @param nameHk          港文名称，null 表示不更新该列
     * @param nameEn          英文名称，null 表示不更新该列
     * @param exchange        交易所，null 表示不更新该列
     * @param currency        币种，null 表示不更新该列
     * @return 受影响行数（0/1）
     */
    int updateEmptyMetadataByCanonicalSymbol(@Param("canonicalSymbol") String canonicalSymbol,
                                             @Param("nameCn") String nameCn,
                                             @Param("nameHk") String nameHk,
                                             @Param("nameEn") String nameEn,
                                             @Param("exchange") String exchange,
                                             @Param("currency") String currency);
}
