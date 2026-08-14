package com.unispeaking.service.help;

import com.unispeaking.domain.dto.help.HelpCenterResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HelpCenterService {

	private final HelpCenterResponse helpCenter = new HelpCenterResponse(List.of(
			category("quick-start", "快速开始", "完成首次设置，选择适合自己的口语练习方式。", 3),
			category("account-login", "账号与登录", "了解注册、登录、个人资料和密码安全。", 4),
			category("ai-training", "AI 对话与训练", "使用自由对话和情景口语完成一次完整练习。", 5),
			category("audio", "麦克风和音频", "排查麦克风权限、播放声音和实时连接问题。", 4),
			category("learning-records", "学习记录", "查看学习资产、评分、打卡和练习统计。", 4),
			category("membership", "会员与额度", "了解当前会员页面、额度提示和功能开放状态。", 3),
			category("privacy-security", "隐私与安全", "保护账号信息，安全使用麦克风并提交问题反馈。", 3),
			category("feedback", "问题反馈", "整理问题信息，帮助我们更快定位使用异常。", 3)));

	public HelpCenterResponse getHelpCenter() {
		return helpCenter;
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
}
