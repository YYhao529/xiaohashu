package com.quanxiaoha.xiaohashu.note.biz.rpc;

import com.quanxiaoha.framework.common.response.Response;
import com.quanxiaoha.xiaohashu.user.api.UserFeignApi;
import com.quanxiaoha.xiaohashu.user.dto.req.FindUserByIdReqDTO;
import com.quanxiaoha.xiaohashu.user.dto.resp.FindUserByIdRspDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户服务
 */
@Component
public class UserRpcService {

    @Autowired
    private UserFeignApi userFeignApi;

    /**
     * 根据用户 id 查询用户信息
     */
    public FindUserByIdRspDTO findById(Long userId){
        FindUserByIdReqDTO findUserByIdReqDTO = new FindUserByIdReqDTO();
        findUserByIdReqDTO.setId(userId);

        Response<FindUserByIdRspDTO> response = userFeignApi.findById(findUserByIdReqDTO);
        if (Objects.isNull(response)||!response.isSuccess()){
            return null;
        }

        return response.getData();
    }
}
