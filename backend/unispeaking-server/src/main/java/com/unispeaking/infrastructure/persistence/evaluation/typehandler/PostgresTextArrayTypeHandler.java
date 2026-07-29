package com.unispeaking.infrastructure.persistence.evaluation.typehandler;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * PostgreSQL {@code TEXT[]} 与 Java {@code String[]} 的显式 MyBatis 映射。
 *
 * <p>该处理器不做全局注册，只供具体 Mapper 字段显式引用，避免影响其他模块
 * 对数组或集合类型的处理。</p>
 */
public final class PostgresTextArrayTypeHandler
		extends BaseTypeHandler<String[]> {

	private static final String POSTGRES_TEXT_TYPE = "text";

	/**
	 * 将非空字符串数组绑定为 PostgreSQL {@code TEXT[]} 参数。
	 *
	 * <p>绑定前复制调用方数组，避免后续修改影响驱动正在使用的参数。创建出的
	 * JDBC Array 不能在此处释放，因为驱动可能在语句执行时才读取其中内容。</p>
	 */
	@Override
	public void setNonNullParameter(
			PreparedStatement preparedStatement,
			int parameterIndex,
			String[] values,
			JdbcType jdbcType) throws SQLException {
		String[] safeValues = values.clone();
		Connection connection = preparedStatement.getConnection();
		Array sqlArray =
				connection.createArrayOf(POSTGRES_TEXT_TYPE, safeValues);
		preparedStatement.setArray(parameterIndex, sqlArray);
	}

	@Override
	public String[] getNullableResult(
			ResultSet resultSet,
			String columnName) throws SQLException {
		return readTextArray(resultSet.getArray(columnName));
	}

	@Override
	public String[] getNullableResult(
			ResultSet resultSet,
			int columnIndex) throws SQLException {
		return readTextArray(resultSet.getArray(columnIndex));
	}

	@Override
	public String[] getNullableResult(
			CallableStatement callableStatement,
			int columnIndex) throws SQLException {
		return readTextArray(callableStatement.getArray(columnIndex));
	}

	/**
	 * 严格读取并复制 PostgreSQL {@code TEXT[]}，同时释放数据库返回的 Array。
	 */
	private String[] readTextArray(Array sqlArray) throws SQLException {
		if (sqlArray == null) {
			return null;
		}

		try {
			String baseTypeName = sqlArray.getBaseTypeName();
			if (!POSTGRES_TEXT_TYPE.equalsIgnoreCase(baseTypeName)) {
				throw new SQLException(
						"预期 PostgreSQL TEXT[]，实际基础类型为："
								+ baseTypeName);
			}

			Object rawValues = sqlArray.getArray();
			if (rawValues instanceof String[] stringValues) {
				return stringValues.clone();
			}
			if (rawValues instanceof Object[] objectValues) {
				return copyStringValues(objectValues);
			}
			throw new SQLException(
					"PostgreSQL TEXT[] 返回了不支持的底层数组类型");
		}
		finally {
			sqlArray.free();
		}
	}

	/**
	 * 复制驱动返回的通用对象数组，并拒绝其中的非字符串元素。
	 */
	private String[] copyStringValues(Object[] rawValues)
			throws SQLException {
		String[] result = new String[rawValues.length];
		for (int index = 0; index < rawValues.length; index++) {
			Object value = rawValues[index];
			if (value != null && !(value instanceof String)) {
				throw new SQLException(
						"PostgreSQL TEXT[] 包含非字符串元素，索引："
								+ index);
			}
			result[index] = (String) value;
		}
		return result;
	}
}
