package com.unispeaking.common.persistence.typehandler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证 PostgreSQL TEXT[] 显式映射的绑定、读取和资源释放边界。
 */
class PostgresTextArrayTypeHandlerTest {

	private final PostgresTextArrayTypeHandler typeHandler =
			new PostgresTextArrayTypeHandler();

	@Test
	void bindsDefensiveCopyAsPostgresTextArray() throws Exception {
		PreparedStatement preparedStatement =
				mock(PreparedStatement.class);
		Connection connection = mock(Connection.class);
		Array sqlArray = mock(Array.class);
		String[] source = {"表达清晰", "发音自然"};
		ArgumentCaptor<Object[]> valuesCaptor =
				ArgumentCaptor.forClass(Object[].class);

		when(preparedStatement.getConnection()).thenReturn(connection);
		when(connection.createArrayOf(
				org.mockito.ArgumentMatchers.eq("text"),
				valuesCaptor.capture()))
				.thenReturn(sqlArray);

		typeHandler.setNonNullParameter(
				preparedStatement,
				4,
				source,
				JdbcType.ARRAY);
		source[0] = "已被调用方修改";

		assertAll(
				() -> assertArrayEquals(
						new String[]{"表达清晰", "发音自然"},
						valuesCaptor.getValue()),
				() -> assertNotSame(source, valuesCaptor.getValue()));
		verify(preparedStatement).setArray(4, sqlArray);
	}

	@Test
	void readsByColumnNameAsDefensiveCopyAndFreesArray()
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		String[] driverValues = {"优点一", "优点二"};
		Array sqlArray = textArray(driverValues);
		when(resultSet.getArray("strengths")).thenReturn(sqlArray);

		String[] result =
				typeHandler.getNullableResult(resultSet, "strengths");

		assertAll(
				() -> assertArrayEquals(driverValues, result),
				() -> assertNotSame(driverValues, result));
		verify(sqlArray).free();
	}

	@Test
	void readsByColumnIndexFromGenericObjectArray()
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		Array sqlArray =
				textArray(new Object[]{"建议一", null, "建议三"});
		when(resultSet.getArray(2)).thenReturn(sqlArray);

		String[] result = typeHandler.getNullableResult(resultSet, 2);

		assertArrayEquals(
				new String[]{"建议一", null, "建议三"},
				result);
		verify(sqlArray).free();
	}

	@Test
	void readsCallableStatementEntryAndFreesArray()
			throws Exception {
		CallableStatement callableStatement =
				mock(CallableStatement.class);
		Array sqlArray = textArray(new String[]{"待改进项"});
		when(callableStatement.getArray(3)).thenReturn(sqlArray);

		String[] result =
				typeHandler.getNullableResult(callableStatement, 3);

		assertArrayEquals(new String[]{"待改进项"}, result);
		verify(sqlArray).free();
	}

	@Test
	void returnsNullForSqlNullAcrossAllReadEntries()
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		CallableStatement callableStatement =
				mock(CallableStatement.class);

		assertAll(
				() -> assertNull(
						typeHandler.getNullableResult(
								resultSet,
								"strengths")),
				() -> assertNull(
						typeHandler.getNullableResult(resultSet, 1)),
				() -> assertNull(
						typeHandler.getNullableResult(
								callableStatement,
								1)));
	}

	@Test
	void rejectsNonTextBaseTypeAndStillFreesArray()
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		Array sqlArray = mock(Array.class);
		when(resultSet.getArray(1)).thenReturn(sqlArray);
		when(sqlArray.getBaseTypeName()).thenReturn("int4");

		assertThrows(
				SQLException.class,
				() -> typeHandler.getNullableResult(resultSet, 1));
		verify(sqlArray).free();
	}

	@Test
	void rejectsNonStringElementAndStillFreesArray()
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		Array sqlArray = textArray(new Object[]{"合法", 42});
		when(resultSet.getArray(1)).thenReturn(sqlArray);

		assertThrows(
				SQLException.class,
				() -> typeHandler.getNullableResult(resultSet, 1));
		verify(sqlArray).free();
	}

	@Test
	void rejectsUnsupportedRawArrayTypeAndStillFreesArray()
			throws Exception {
		ResultSet resultSet = mock(ResultSet.class);
		Array sqlArray = textArray(new int[]{1, 2});
		when(resultSet.getArray(1)).thenReturn(sqlArray);

		assertThrows(
				SQLException.class,
				() -> typeHandler.getNullableResult(resultSet, 1));
		verify(sqlArray).free();
	}

	/**
	 * 构造只暴露 TEXT 基础类型和指定底层值的 JDBC Array。
	 */
	private Array textArray(Object rawValues) throws SQLException {
		Array sqlArray = mock(Array.class);
		when(sqlArray.getBaseTypeName()).thenReturn("text");
		when(sqlArray.getArray()).thenReturn(rawValues);
		return sqlArray;
	}
}
