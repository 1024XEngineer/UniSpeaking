package com.unispeaking.common.persistence.typehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

class PostgresUuidTypeHandlerTest {

	private final PostgresUuidTypeHandler handler = new PostgresUuidTypeHandler();

	@Test
	void bindsUuidAsPostgresOther() throws Exception {
		PreparedStatement statement = mock(PreparedStatement.class);
		UUID value = UUID.fromString("11111111-1111-4111-8111-111111111111");

		handler.setNonNullParameter(statement, 1, value, JdbcType.OTHER);

		verify(statement).setObject(1, value, Types.OTHER);
	}

	@Test
	void readsNativeTextAndNullUuidValues() throws Exception {
		UUID value = UUID.fromString("11111111-1111-4111-8111-111111111111");
		ResultSet resultSet = mock(ResultSet.class);
		CallableStatement callable = mock(CallableStatement.class);
		when(resultSet.getObject("user_id")).thenReturn(value);
		when(resultSet.getObject(2)).thenReturn(value.toString());
		when(callable.getObject(3)).thenReturn(null);

		assertEquals(value, handler.getNullableResult(resultSet, "user_id"));
		assertEquals(value, handler.getNullableResult(resultSet, 2));
		assertNull(handler.getNullableResult(callable, 3));
	}
}
