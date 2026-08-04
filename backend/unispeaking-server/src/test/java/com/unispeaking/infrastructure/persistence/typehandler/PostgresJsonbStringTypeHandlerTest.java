package com.unispeaking.infrastructure.persistence.typehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class PostgresJsonbStringTypeHandlerTest {

	private final PostgresJsonbStringTypeHandler handler =
			new PostgresJsonbStringTypeHandler();

	@Test
	void bindsJsonAsPostgresOther() throws Exception {
		PreparedStatement statement = mock(PreparedStatement.class);

		handler.setNonNullParameter(
				statement,
				2,
				"{\"overview\":\"role\"}",
				JdbcType.OTHER);

		verify(statement).setObject(
				2,
				"{\"overview\":\"role\"}",
				Types.OTHER);
	}

	@Test
	void readsEveryJdbcResultShapeAndPreservesNull() throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		CallableStatement callable = mock(CallableStatement.class);
		when(resultSet.getObject("role_summary")).thenReturn("{\"a\":1}");
		when(resultSet.getObject(3)).thenReturn(new StringBuilder("{\"b\":2}"));
		when(callable.getObject(4)).thenReturn(null);

		assertEquals("{\"a\":1}",
				handler.getNullableResult(resultSet, "role_summary"));
		assertEquals("{\"b\":2}", handler.getNullableResult(resultSet, 3));
		assertNull(handler.getNullableResult(callable, 4));
	}
}
