package com.heng.support;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JSON 文本列与 Java 集合/Map 的转换器。
 * MyBatis-Plus 自带的 JacksonTypeHandler 绑定 Jackson 2，而 Spring Boot 4 引入的是 Jackson 3，
 * 因此这里按字段声明类型使用项目自带的 Jackson 3 完成序列化。
 */
public class JsonTypeHandler extends BaseTypeHandler<Object> {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final Class<?> type;

    public JsonTypeHandler(Class<?> type) {
        this.type = type;
    }

    public JsonTypeHandler(Class<?> type, Field field) {
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, Object parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(index, MAPPER.writeValueAsString(parameter));
    }

    @Override
    public Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Object getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Object parse(String json) {
        if (json == null || json.isBlank()) return null;
        Class<?> target = type == null || Object.class.equals(type) ? Object.class : type;
        return MAPPER.readValue(json, target);
    }
}
