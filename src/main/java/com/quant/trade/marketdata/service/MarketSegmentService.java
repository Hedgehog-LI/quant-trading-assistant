package com.quant.trade.marketdata.service;

import com.quant.trade.common.exception.BusinessException;
import com.quant.trade.common.exception.ErrorCodeEnum;
import com.quant.trade.marketdata.dao.MarketSegmentMapper;
import com.quant.trade.marketdata.dao.MarketSegmentMemberMapper;
import com.quant.trade.marketdata.dto.SegmentDTO;
import com.quant.trade.marketdata.dto.SegmentMemberDTO;
import com.quant.trade.marketdata.model.MarketSegmentDO;
import com.quant.trade.marketdata.model.MarketSegmentMemberDO;
import com.quant.trade.marketdata.util.CanonicalSymbolUtils;
import com.quant.trade.marketdata.vo.MarketSegmentMemberVO;
import com.quant.trade.marketdata.vo.MarketSegmentVO;
import com.quant.trade.marketdata.vo.PageResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** 板块管理应用服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSegmentService {

    private final MarketSegmentMapper segmentMapper;
    private final MarketSegmentMemberMapper memberMapper;

    @Transactional
    public MarketSegmentVO createSegment(SegmentDTO dto) {
        String code = generateCode(dto.getSegmentName());
        if (segmentMapper.selectByCode(code) != null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "板块代码已存在: " + code);
        }
        MarketSegmentDO seg = MarketSegmentDO.builder()
                .segmentCode(code)
                .segmentName(dto.getSegmentName())
                .segmentType(dto.getSegmentType() != null ? dto.getSegmentType() : "CUSTOM")
                .description(dto.getDescription())
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .build();
        segmentMapper.insert(seg);
        log.info("创建板块: id={}, code={}, name={}", seg.getId(), code, seg.getSegmentName());
        return toSegmentVO(seg, 0);
    }

    public PageResultVO<MarketSegmentVO> listSegments(String segmentType, Boolean enabled,
                                                       String keyword, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        int offset = (Math.max(page, 1) - 1) * safeSize;
        List<MarketSegmentDO> segs = segmentMapper.selectByFilter(segmentType, enabled, keyword, safeSize, offset);
        long total = segmentMapper.countByFilter(segmentType, enabled, keyword);
        List<MarketSegmentVO> items = segs.stream()
                .map(s -> toSegmentVO(s, memberMapper.countBySegmentId(s.getId())))
                .collect(Collectors.toList());
        return PageResultVO.of(items, total, page, safeSize);
    }

    public MarketSegmentVO getSegment(Long id) {
        MarketSegmentDO seg = segmentMapper.selectById(id);
        if (seg == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "板块不存在: " + id);
        }
        return toSegmentVO(seg, memberMapper.countBySegmentId(id));
    }

    @Transactional
    public MarketSegmentVO updateSegment(Long id, SegmentDTO dto) {
        MarketSegmentDO existing = segmentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "板块不存在: " + id);
        }
        existing.setSegmentName(dto.getSegmentName());
        if (dto.getSegmentType() != null) existing.setSegmentType(dto.getSegmentType());
        existing.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) existing.setEnabled(dto.getEnabled());
        segmentMapper.updateById(existing);
        return toSegmentVO(existing, memberMapper.countBySegmentId(id));
    }

    @Transactional
    public void deleteSegment(Long id) {
        if (segmentMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "板块不存在: " + id);
        }
        segmentMapper.deleteById(id);
        log.info("删除板块: id={}", id);
    }

    // ==================== 成员 ====================

    public List<MarketSegmentMemberVO> listMembers(Long segmentId) {
        if (segmentMapper.selectById(segmentId) == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "板块不存在: " + segmentId);
        }
        return memberMapper.selectBySegmentId(segmentId).stream()
                .map(this::toMemberVO).collect(Collectors.toList());
    }

    @Transactional
    public MarketSegmentMemberVO addMember(Long segmentId, SegmentMemberDTO dto) {
        if (segmentMapper.selectById(segmentId) == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_RULE_VIOLATION, "板块不存在: " + segmentId);
        }
        String canonicalSymbol;
        try {
            canonicalSymbol = CanonicalSymbolUtils.normalize(dto.getCanonicalSymbol());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, exception.getMessage());
        }
        MarketSegmentMemberDO member = MarketSegmentMemberDO.builder()
                .segmentId(segmentId)
                .canonicalSymbol(canonicalSymbol)
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .remark(dto.getRemark())
                .build();
        memberMapper.insert(member);
        return toMemberVO(member);
    }

    @Transactional
    public void removeMember(Long segmentId, String canonicalSymbol) {
        try {
            memberMapper.deleteBySegmentAndSymbol(segmentId,
                    CanonicalSymbolUtils.normalize(canonicalSymbol));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCodeEnum.INVALID_CANONICAL_SYMBOL, exception.getMessage());
        }
    }

    // ==================== 内部 ====================

    private String generateCode(String name) {
        // 简单生成：时间戳 + 名称 hash
        return "SEG_" + System.currentTimeMillis() % 1000000;
    }

    private MarketSegmentVO toSegmentVO(MarketSegmentDO d, int memberCount) {
        return MarketSegmentVO.builder()
                .id(d.getId()).segmentCode(d.getSegmentCode()).segmentName(d.getSegmentName())
                .segmentType(d.getSegmentType()).description(d.getDescription()).enabled(d.getEnabled())
                .memberCount(memberCount).createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
                .build();
    }

    private MarketSegmentMemberVO toMemberVO(MarketSegmentMemberDO d) {
        return MarketSegmentMemberVO.builder()
                .id(d.getId()).segmentId(d.getSegmentId()).canonicalSymbol(d.getCanonicalSymbol())
                .sortOrder(d.getSortOrder()).remark(d.getRemark())
                .createdAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null)
                .build();
    }
}
