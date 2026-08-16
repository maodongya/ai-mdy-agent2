package com.anvil.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * 协议 DTO 的共享 JSON 工具类（Java ↔ Swift Workbench）。
 *
 * <p>统一使用一个配置好的 {@link ObjectMapper} 实例完成序列化/反序列化，
 * 约定时间戳以可读字符串输出（禁用时间戳数字格式）。</p>
 */
public final class ProtocolJson {

    /** 共享的 ObjectMapper（按需自动注册模块，时间戳用字符串输出）。 */
    private static final ObjectMapper MAPPER =
            JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private ProtocolJson() {}

    /**
     * 获取共享的 ObjectMapper 实例。
     *
     * @return ObjectMapper
     */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败时抛出
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json serialize failed", e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型对象。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @return 反序列化结果
     * @throws IllegalStateException 解析失败时抛出
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("json parse failed", e);
        }
    }

    /**
     * 将 JSON 字符串解析为 {@code Map<String,Object>}。
     *
     * @param json JSON 字符串
     * @return Map 表示
     * @throws IllegalStateException 解析失败时抛出
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapFromJson(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("json parse failed", e);
        }
    }
}
