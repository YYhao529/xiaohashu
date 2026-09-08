package com.quanxiaoha.framework.common.util;

import com.quanxiaoha.framework.common.constant.DateConstants;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 日期工具类
 */
public class DateUtils {

    /**
     * LocalDateTime 转时间戳
     */
    public static long localDateTime2Timestamp(LocalDateTime localDateTime) {
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * LocalDateTime 转字符串
     * eg: 2024-06-07 16:06:47 -> "2024-06-07 16:06:47"
     */
    public static String localDateTime2String(LocalDateTime localDateTime) {
        return localDateTime.format(DateConstants.DATE_FORMAT_Y_M_D_H_M_S);
    }
}
