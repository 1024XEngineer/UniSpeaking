package com.unispeaking.common.persistence.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(UUID.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class PostgresUuidTypeHandler extends BaseTypeHandler<UUID> {

	@Override
	public void setNonNullParameter(
			PreparedStatement statement,
			int index,
			UUID parameter,
			JdbcType jdbcType
	) throws SQLException {
		statement.setObject(index, parameter, Types.OTHER);
	}

	@Override
	public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
		return toUuid(resultSet.getObject(columnName));
	}

	@Override
	public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
		return toUuid(resultSet.getObject(columnIndex));
	}

	@Override
	public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
		return toUuid(statement.getObject(columnIndex));
	}

	private UUID toUuid(Object value) {
		if (value == null || value instanceof UUID) {
			return (UUID) value;
		}
		return UUID.fromString(value.toString());
	}
}
