package com.unispeaking.service.help;

import com.unispeaking.domain.dto.help.HelpCenterResponse;
import com.unispeaking.domain.dto.help.HelpArticleResponse;
import com.unispeaking.domain.dto.help.HelpArticleSummaryResponse;
import com.unispeaking.domain.dto.help.HelpCategoryDetailResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class HelpCenterService {
	private static final String UPDATED_AT = "2026-08-04";

	private final HelpCenterResponse helpCenter = new HelpCenterResponse(List.of(
			category("quick-start", "快速开始", "完成首次设置，选择适合自己的口语练习方式。", 3),
			category("account-login", "账号与登录", "了解注册、登录、个人资料和密码安全。", 4),
			category("ai-training", "AI 对话与训练", "使用自由对话和情景口语完成一次完整练习。", 5),
			category("audio", "麦克风和音频", "排查麦克风权限、播放声音和实时连接问题。", 4),
			category("learning-records", "学习记录", "查看学习资产、评分、打卡和练习统计。", 4),
			category("membership", "会员与额度", "了解当前会员页面、额度提示和功能开放状态。", 3),
			category("privacy-security", "隐私与安全", "保护账号信息，安全使用麦克风并提交问题反馈。", 3),
			category("feedback", "问题反馈", "整理问题信息，帮助我们更快定位使用异常。", 3)));

	private final List<Article> articles = List.of(
			article("complete-first-time-setup", "quick-start", "如何完成首次设置并开始练习？", "设置英语水平和 AI 老师后，即可进入自由对话或情景训练。"),
			article("choose-practice-mode", "quick-start", "自由对话和情景训练有什么区别？", "自由对话适合即时开口，情景训练适合围绕具体任务循序练习。"),
			article("adjust-assistant-settings", "quick-start", "如何更换英语水平、AI 老师和语速？", "在个人中心的助手设置中调整对话体验。"),
			article("register-and-login", "account-login", "如何注册和登录 UniSpeaking？", "使用有效邮箱和密码创建账号，之后可从登录页再次进入。"),
			article("change-account-password", "account-login", "如何修改账号密码？", "在账号与安全中验证当前密码并设置新密码。"),
			article("why-login-expired", "account-login", "为什么系统要求我重新登录？", "登录凭证失效、密码变更或账号状态变化时，系统会要求重新认证。"),
			article("update-profile-details", "account-login", "如何修改昵称和头像？", "通过个人概览顶部的编辑按钮更新展示昵称和个人头像。"),
			article("start-free-conversation", "ai-training", "如何开始一次 AI 自由对话？", "进入自由对话，确认麦克风可用后开始实时语音交流。"),
			article("create-custom-scene", "ai-training", "如何创建自己的情景口语训练？", "描述想练习的真实情景，由系统生成对应学习内容。"),
			article("learn-read-speak-flow", "ai-training", "“学、读、说”三个阶段分别做什么？", "先理解表达，再练习朗读，最后在完整情景中开口。"),
			article("use-subtitles-and-translation", "ai-training", "如何使用字幕和翻译？", "在实时对话中按需要显示完整字幕并翻译对话内容。"),
			article("finish-training-correctly", "ai-training", "怎样正确结束一次训练？", "使用页面中的结束操作，让系统完成会话收尾和结果保存。"),
			article("grant-microphone-permission", "audio", "如何允许应用使用麦克风？", "在系统权限提示中允许麦克风，并确认使用正确的输入设备。"),
			article("microphone-not-detected", "audio", "应用检测不到麦克风怎么办？", "检查设备连接、系统输入设置和应用权限。"),
			article("cannot-hear-ai-audio", "audio", "听不到 AI 老师的声音怎么办？", "检查输出设备、页面播放状态和系统音量。"),
			article("realtime-connection-interrupted", "audio", "实时对话连接中断后怎么办？", "结束异常会话，检查网络和权限后重新开始。"),
			article("what-learning-assets-save", "learning-records", "学习资产会保存哪些内容？", "集中查看已完成场景中的语言材料、对话记录和可用评分。"),
			article("view-conversation-feedback", "learning-records", "如何查看对话记录和评分？", "从学习资产打开最近完成的场景对话详情。"),
			article("practice-from-assets", "learning-records", "如何从学习资产再次练习？", "复用已有场景直接练口语，或从头重新学习内容。"),
			article("understand-checkin-statistics", "learning-records", "自动打卡和学习统计是怎样计算的？", "打卡来自已生成的训练报告，学习时长来自正常完成的有效会话。"),
			article("open-membership-page", "membership", "在哪里查看会员与额度页面？", "从个人中心进入会员权益，查看当前方案和额度信息。"),
			article("membership-payment-status", "membership", "当前会员升级会产生真实扣费吗？", "不会。当前版本的升级和支付流程是界面演示。"),
			article("understand-quota-reminders", "membership", "页面中的额度提示代表什么？", "当前额度和特训限制用于展示预期体验，不构成正式计费承诺。"),
			article("protect-account-security", "privacy-security", "如何保护我的 UniSpeaking 账号？", "使用独立密码，妥善保管登录状态，并在异常时及时修改密码。"),
			article("avoid-sensitive-feedback", "privacy-security", "提交问题反馈时不应包含哪些信息？", "不要提交密码、令牌、密钥、完整身份证明或其他敏感数据。"),
			article("use-microphone-safely", "privacy-security", "使用麦克风练习时需要注意什么？", "只在开始口语练习时授权麦克风，结束后及时停止会话。"),
			article("check-before-feedback", "feedback", "反馈问题前应该先做哪些检查？", "先确认网络、权限和页面状态，避免重复提交可以自行恢复的问题。"),
			article("prepare-feedback-details", "feedback", "一条有效的问题反馈应包含什么？", "提供发生位置、复现步骤、实际结果、期望结果和环境信息。"),
			article("report-security-concern", "feedback", "发现账号或隐私安全问题时怎么办？", "先保护账号并停止继续暴露数据，再整理最小必要问题信息。"));

	public HelpCenterResponse getHelpCenter() {
		return helpCenter;
	}

	public Optional<HelpCategoryDetailResponse> getCategory(String categoryId) {
		return helpCenter.categories().stream()
				.filter(category -> category.id().equals(categoryId))
				.findFirst()
				.map(category -> new HelpCategoryDetailResponse(
						category.id(),
						category.title(),
						category.description(),
						articles.stream()
								.filter(article -> article.categoryId().equals(categoryId))
								.map(article -> new HelpArticleSummaryResponse(
										article.id(), article.title(), article.summary()))
								.toList()));
	}

	public Optional<HelpArticleResponse> getArticle(String articleId) {
		return articles.stream()
				.filter(article -> article.id().equals(articleId))
				.findFirst()
				.map(article -> new HelpArticleResponse(
						article.id(),
						article.categoryId(),
						article.title(),
						article.summary(),
						UPDATED_AT));
	}

	private static HelpCenterResponse.HelpCategoryResponse category(
			String id,
			String title,
			String description,
			int articleCount) {
		return new HelpCenterResponse.HelpCategoryResponse(
				id,
				title,
				description,
				articleCount);
	}

	private static Article article(
			String id,
			String categoryId,
			String title,
			String summary) {
		return new Article(id, categoryId, title, summary);
	}

	private record Article(String id, String categoryId, String title, String summary) {
	}
}
