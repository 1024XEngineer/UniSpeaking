package com.unispeaking.component.achievement;

import com.unispeaking.domain.vo.achievement.AchievementDefinition;
import com.unispeaking.domain.vo.achievement.AchievementSeriesDefinition;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AchievementCatalog {

	private static final List<AchievementSeriesDefinition> SERIES = List.of(
			series("conversation", "开口", "对话历程", "次",
					milestone(1, "初次开口", "累计完成 1 次有效对话", 1),
					milestone(2, "渐入佳境", "累计完成 5 次有效对话", 5),
					milestone(3, "对话常客", "累计完成 20 次有效对话", 20),
					milestone(4, "对话达人", "累计完成 50 次有效对话", 50),
					milestone(5, "百炼成章", "累计完成 100 次有效对话", 100)),
			series("streak", "连续", "连续学习", "天",
					milestone(1, "三日启程", "历史最长连续打卡达到 3 天", 3),
					milestone(2, "七日同行", "历史最长连续打卡达到 7 天", 7),
					milestone(3, "两周不辍", "历史最长连续打卡达到 14 天", 14),
					milestone(4, "月度坚守", "历史最长连续打卡达到 30 天", 30),
					milestone(5, "百日如一", "历史最长连续打卡达到 100 天", 100)),
			series("scene-exploration", "场景", "场景探索", "个",
					milestone(1, "场景初探", "完成过 1 个不同场景", 1),
					milestone(2, "场景探索者", "完成过 5 个不同场景", 5),
					milestone(3, "场景行者", "完成过 10 个不同场景", 10),
					milestone(4, "场景达人", "完成过 20 个不同场景", 20),
					milestone(5, "世界漫游者", "完成过 50 个不同场景", 50)),
			series("expression-score", "成长", "表达质量", "分",
					milestone(1, "表达进阶", "单轮表达历史最高分达到 80", 80),
					milestone(2, "表达新星", "单轮表达历史最高分达到 90", 90),
					milestone(3, "高光表达", "单轮表达历史最高分达到 95", 95),
					milestone(4, "满分时刻", "单轮表达历史最高分达到 100", 100)),
			series("pronunciation-attempt", "成长", "跟读训练", "次",
					milestone(1, "发音初试", "累计完成 1 次句子跟读评分", 1),
					milestone(2, "找准节奏", "累计完成 10 次句子跟读评分", 10),
					milestone(3, "发音校准师", "累计完成 30 次句子跟读评分", 30),
					milestone(4, "百次精练", "累计完成 100 次句子跟读评分", 100),
					milestone(5, "千锤百炼", "累计完成 300 次句子跟读评分", 300)),
			series("asset-collection", "场景", "资产积累", "项",
					milestone(1, "初藏一景", "历史累计拥有 1 项学习资产", 1),
					milestone(2, "素材积累者", "历史累计拥有 5 项学习资产", 5),
					milestone(3, "资产收藏家", "历史累计拥有 20 项学习资产", 20),
					milestone(4, "学习馆主", "历史累计拥有 50 项学习资产", 50),
					milestone(5, "知识典藏家", "历史累计拥有 100 项学习资产", 100)),
			series("monthly-checkin", "连续", "月度打卡", "天",
					milestone(1, "月初蓄力", "任意自然月累计打卡 5 天", 5),
					milestone(2, "稳步前行", "任意自然月累计打卡 10 天", 10),
					milestone(3, "月度全勤", "任意自然月累计打卡 20 天", 20),
					milestone(4, "超级全勤", "任意自然月累计打卡 25 天", 25)),
			series("practice-duration", "开口", "累计学习时长", "小时",
					milestone(1, "初心投入", "有效学习时长累计达到 1 小时", 1),
					milestone(2, "渐深之境", "有效学习时长累计达到 10 小时", 10),
					milestone(3, "熟能生巧", "有效学习时长累计达到 50 小时", 50),
					milestone(4, "长期主义", "有效学习时长累计达到 100 小时", 100),
					milestone(5, "长路耕耘", "有效学习时长累计达到 500 小时", 500)),
			series("active-days", "连续", "累计学习天数", "天",
					milestone(1, "留下足迹", "累计打卡达到 1 天", 1),
					milestone(2, "习惯养成", "累计打卡达到 10 天", 10),
					milestone(3, "日积月累", "累计打卡达到 30 天", 30),
					milestone(4, "百日积累者", "累计打卡达到 100 天", 100),
					milestone(5, "年度学习者", "累计打卡达到 365 天", 365)),
			series("quality-sessions", "成长", "稳定高质量输出", "场",
					milestone(1, "初见锋芒", "累计 1 场最终评分达到 80", 1),
					milestone(2, "稳定发挥", "累计 5 场最终评分达到 80", 5),
					milestone(3, "优质输出", "累计 20 场最终评分达到 80", 20),
					milestone(4, "表达强者", "累计 50 场最终评分达到 80", 50),
					milestone(5, "稳定卓越", "累计 100 场最终评分达到 80", 100)));

	public List<AchievementSeriesDefinition> series() {
		return SERIES;
	}

	public Optional<AchievementSeriesDefinition> findSeries(String seriesId) {
		return SERIES.stream()
				.filter(series -> series.seriesId().equals(seriesId))
				.findFirst();
	}

	public Optional<AchievementDefinition> findAchievement(String achievementId) {
		return SERIES.stream()
				.flatMap(series -> series.milestones().stream())
				.filter(milestone -> milestone.achievementId().equals(achievementId))
				.findFirst();
	}

	private static AchievementSeriesDefinition series(
			String seriesId,
			String category,
			String title,
			String unit,
			Milestone... milestones) {
		return new AchievementSeriesDefinition(
				seriesId,
				category,
				title,
				unit,
				java.util.Arrays.stream(milestones)
						.map(milestone -> new AchievementDefinition(
								seriesId + "-" + milestone.level(),
								milestone.level(),
								milestone.title(),
								milestone.description(),
								BigDecimal.valueOf(milestone.threshold())))
						.toList());
	}

	private static Milestone milestone(
			int level,
			String title,
			String description,
			long threshold) {
		return new Milestone(level, title, description, threshold);
	}

	private record Milestone(
			int level,
			String title,
			String description,
			long threshold) {
	}
}
