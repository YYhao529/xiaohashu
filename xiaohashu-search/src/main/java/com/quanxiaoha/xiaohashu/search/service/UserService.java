package com.quanxiaoha.xiaohashu.search.service;

import com.quanxiaoha.framework.common.response.PageResponse;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchUserReqVO;
import com.quanxiaoha.xiaohashu.search.model.vo.SearchUserRspVO;

import java.io.IOException;

/**
 * 用户搜索业务
 */
public interface UserService {

    /**
     * 搜索用户
     */
    PageResponse<SearchUserRspVO> searchUser(SearchUserReqVO searchUserReqVO);
}
