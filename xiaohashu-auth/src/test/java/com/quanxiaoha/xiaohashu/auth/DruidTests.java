package com.quanxiaoha.xiaohashu.auth;

import com.alibaba.druid.filter.config.ConfigTools;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class DruidTests {

    /**
     * Druid 密码加密
     */
    @Test
    @SneakyThrows
    void testEncoderPassword(){
        String password="123456";
        String[] keyPair = ConfigTools.genKeyPair(512);

        // 私钥
        log.info("privateKey: {}", keyPair[0]);
        // 公钥
        log.info("publicKey: {}", keyPair[1]);

        // 通过私钥加密密码
        String encodePassword = ConfigTools.encrypt(keyPair[0], password);
        log.info("password: {}", encodePassword);
    }
}
