package com.wlf.translateprovider.utils;

import java.io.UnsupportedEncodingException;

public class StringUtil {

    /**
     * 去掉字符串中的标点符号、空格和数字
     *
     * @param input
     * @return
     */
    public static String removePunctuation(String input) {
        return input.replaceAll(" +", "").replaceAll("[\\pP\\p{Punct}]", "").replaceAll("\\d+", "");
    }

    /**
     * 判断字符串是否为中文
     *
     * @param input
     * @return
     */
    public static boolean isChinese(String input) {
        return input.matches("^[\u4e00-\u9fa5]+$");
    }

    /**
     * 判断字符串是否为日文
     *
     * @param input
     * @return
     */
    public static boolean isJapanese(String input) {
        try {
            return input.getBytes("shift-jis").length >= (2 * input.length());
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    /**
     * 判断字符串是否为英文
     *
     * @param input
     * @return
     */
    public static boolean isEnglish(String input) {
        return input.toUpperCase().matches("^[\u0041-\u005a]+$");
    }

}

