package com.unispeaking.component.document;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaterialDesensitizerTest {

	private final MaterialDesensitizer desensitizer = new MaterialDesensitizer();

	@Test
	void removesEmailsPhonesAndIdNumbers() {
		String text = "联系 zhang.san@example.com 或 13800138000，座机 0571-12345678，"
				+ "身份证 11010119900307737X";

		String result = desensitizer.desensitize(text);

		assertFalse(result.contains("zhang.san@example.com"));
		assertFalse(result.contains("13800138000"));
		assertFalse(result.contains("0571-12345678"));
		assertFalse(result.contains("11010119900307737X"));
		assertTrue(result.contains("【邮箱已隐藏】"));
		assertTrue(result.contains("【电话已隐藏】"));
		assertTrue(result.contains("【证件号已隐藏】"));
	}

	@Test
	void removesLabeledNameAndAddress() {
		String text = "姓名：张三\n地址：北京市海淀区中关村大街1号";

		String result = desensitizer.desensitize(text);

		assertFalse(result.contains("张三"));
		assertFalse(result.contains("北京市海淀区中关村大街1号"));
		assertTrue(result.contains("【姓名已隐藏】"));
		assertTrue(result.contains("【地址已隐藏】"));
	}

	@Test
	void removesSocialAccounts() {
		String text = "微信号：zhang_san，QQ：123456789";

		String result = desensitizer.desensitize(text);

		assertFalse(result.contains("zhang_san"));
		assertFalse(result.contains("123456789"));
		assertTrue(result.contains("【社交账号已隐藏】"));
	}

	@Test
	void preservesCompanyAndSchoolNames() {
		String text = "曾就职于某某科技有限公司，负责支付系统；毕业于某某大学计算机系";

		assertEquals(text, desensitizer.desensitize(text));
	}

	@Test
	void returnsNullAndBlankInputsUnchanged() {
		assertNull(desensitizer.desensitize(null));
		assertEquals("", desensitizer.desensitize(""));
		assertEquals("  ", desensitizer.desensitize("  "));
		assertDoesNotThrow(() -> desensitizer.desensitize("无敏感信息的普通文本"));
	}
}
