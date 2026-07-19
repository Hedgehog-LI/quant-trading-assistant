package com.quant.trade.marketdata.controller;

import com.quant.trade.common.api.ApiResponse;
import com.quant.trade.marketdata.dto.VerifySecurityDTO;
import com.quant.trade.marketdata.service.SecurityVerificationService;
import com.quant.trade.marketdata.vo.SecurityVerificationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 证券精确代码验证接口。 */
@RestController
@RequestMapping("/api/v1/market-data/securities")
@RequiredArgsConstructor
public class SecurityVerificationController {

    private final SecurityVerificationService verificationService;

    @PostMapping("/verify")
    public ApiResponse<SecurityVerificationVO> verify(@Valid @RequestBody VerifySecurityDTO request) {
        return ApiResponse.ok(verificationService.verify(request));
    }
}
