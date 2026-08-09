package com.unispeaking.component.document;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 确定性规则脱敏组件：在 LLM-1 前删除简历/JD 中的个人可识别信息（PII）。
 * <p>覆盖：姓名（带标签）、电话（手机/座机）、邮箱、地址（带标签）、证件号、社交账号。
 * 保留公司、学校、项目名等面试信息。规则全部为正则替换，不调用 LLM、不引入随机性。
 * <p>限制：裸姓名（无"姓名："/"Name:"等标签）无法确定性识别，本组件不猜测；地址只处理带
 * {@code 地址/住址} 标签的字段，避免误删公司/学校名称。
 */
@Component
public class MaterialDesensitizer {

	private static final String EMAIL =
			"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";
	private static final String MOBILE =
			"\\b(?:\\+?86[- ]?)?1[3-9]\\d{9}\\b";
	private static final String LANDLINE =
			"\\b0\\d{2,3}[- ]?\\d{7,8}\\b";
	private static final String ID_CARD =
			"\\b\\d{17}[0-9Xx]\\b";
	private static final String LABELED_NAME =
			"(?:姓名|名字|Name|name)\\s*[：:]?\\s*[\\u4e00-\\u9fa5A-Za-z·. ]{2,30}";
	private static final String LABELED_ADDRESS =
			"(?:地址|住址|家庭住址|Address|address)\\s*[：:]?\\s*[\\u4e00-\\u9fa5A-Za-z0-9"
					+ "#\\-号单元室栋楼座巷弄小区园区大道街道街马路路镇乡村县市省区\\s]{4,80}";
	private static final String LABELED_PHONE =
			"(?:电话|手机|联系电话|Phone|Tel|Mobile)\\s*[：:]?\\s*[0-9+\\-\\s]{7,20}";
	private static final String SOCIAL_ACCOUNT =
			"(?:微信号|微信|QQ|微博|抖音|小红书|LinkedIn|领英|GitHub)\\s*[：:号]?\\s*[\\w@.-]{2,40}";

	private static final List<Rule> RULES = List.of(
			new Rule(Pattern.compile(EMAIL), "【邮箱已隐藏】"),
			new Rule(Pattern.compile(MOBILE), "【电话已隐藏】"),
			new Rule(Pattern.compile(LANDLINE), "【电话已隐藏】"),
			new Rule(Pattern.compile(ID_CARD), "【证件号已隐藏】"),
			new Rule(Pattern.compile(LABELED_NAME), "【姓名已隐藏】"),
			new Rule(Pattern.compile(LABELED_PHONE), "【电话已隐藏】"),
			new Rule(Pattern.compile(LABELED_ADDRESS), "【地址已隐藏】"),
			new Rule(Pattern.compile(SOCIAL_ACCOUNT), "【社交账号已隐藏】"));

	/**
	 * 对文本应用全部确定性脱敏规则；{@code null}/空白输入原样返回。
	 */
	public String desensitize(String text) {
		if (text == null || text.isBlank()) {
			return text;
		}
		String result = text;
		for (Rule rule : RULES) {
			result = rule.apply(result);
		}
		return result;
	}

	private record Rule(Pattern pattern, String replacement) {

		private Rule {
			pattern = java.util.Objects.requireNonNull(pattern, "pattern must not be null");
		}

		String apply(String text) {
			return pattern.matcher(text).replaceAll(replacement);
		}
	}
}
