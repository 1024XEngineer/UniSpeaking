package com.unispeaking.infrastructure.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class PostgresJsonbStringTypeHandler extends BaseTypeHandler<String> {

	@Override
	public void setNonNullParameter(
			PreparedStatement statement,
			int index,
			String parameter,
			JdbcType jdbcType) throws SQLException {
		statement.setObject(index, parameter, Types.OTHER);
	}

	@Override
	public String getNullableResult(ResultSet resultSet, String columnName)
			throws SQLException {
		return asString(resultSet.getObject(columnName));
	}

	@Override
	public String getNullableResult(ResultSet resultSet, int columnIndex)
			throws SQLException {
		return asString(resultSet.getObject(columnIndex));
	}

	@Override
	public String getNullableResult(CallableStatement statement, int columnIndex)
			throws SQLException {
		return asString(statement.getObject(columnIndex));
	}

	private String asString(Object value) {
		return value == null ? null : value.toString();
	}
}
