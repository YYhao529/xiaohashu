package com.quanxiaoha.xiaohashu.auth.service;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.auth.model.vo.verificationcode.SendVerificationCodeReqVO;

public interface VerificationCodeService {

    /**
     * 发送短信验证码
     */
    Response<?> send(SendVerificationCodeReqVO sendVerificationCodeReqVO);
}
