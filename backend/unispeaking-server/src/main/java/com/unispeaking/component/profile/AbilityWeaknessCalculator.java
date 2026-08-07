package com.unispeaking.component.profile;

import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class AbilityWeaknessCalculator {

	public static final int MINIMUM_SAMPLE_COUNT = 3;

	public Result calculate(
			List<ProfileInsightsResponse.AbilityTrendPoint> trends) {
		Objects.requireNonNull(trends, "trends must not be null");
		var analysis = new ProfileInsightsResponse.WeaknessAnalysis(
				trends.size(),
				MINIMUM_SAMPLE_COUNT,
				trends.size() >= MINIMUM_SAMPLE_COUNT);
		if (!analysis.reliable()) {
			return new Result(analysis, List.of(), List.of());
		}

		List<DimensionAverage> averages = List.of(Dimension.values()).stream()
				.map(dimension -> summarize(dimension, trends))
				.sorted(Comparator.comparing(DimensionAverage::averageScore)
						.thenComparingInt(item -> item.dimension().ordinal()))
				.limit(2)
				.toList();
		List<ProfileInsightsResponse.AbilityWeakness> weaknesses =
				java.util.stream.IntStream.range(0, averages.size())
						.mapToObj(index -> toWeakness(
								averages.get(index),
								index + 1,
								trends.size()))
						.toList();
		List<ProfileInsightsResponse.TrainingRecommendation> recommendations =
				averages.stream()
						.map(this::toRecommendation)
						.toList();
		return new Result(analysis, weaknesses, recommendations);
	}

	private DimensionAverage summarize(
			Dimension dimension,
			List<ProfileInsightsResponse.AbilityTrendPoint> trends) {
		BigDecimal total = trends.stream()
				.map(ProfileInsightsResponse.AbilityTrendPoint::scores)
				.map(dimension.score())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal average = total.divide(
				BigDecimal.valueOf(trends.size()),
				1,
				RoundingMode.HALF_UP);
		BigDecimal recentChange = dimension.score()
				.apply(trends.getLast().scores())
				.subtract(dimension.score().apply(trends.getFirst().scores()))
				.setScale(1, RoundingMode.HALF_UP);
		return new DimensionAverage(dimension, average, recentChange);
	}

	private ProfileInsightsResponse.AbilityWeakness toWeakness(
			DimensionAverage average,
			int rank,
			int sampleCount) {
		String order = rank == 1 ? "最低" : "第二低";
		return new ProfileInsightsResponse.AbilityWeakness(
				average.dimension().id(),
				rank,
				average.averageScore(),
				average.recentChange(),
				"最近 " + sampleCount + " 次有效评分平均分" + order);
	}

	private ProfileInsightsResponse.TrainingRecommendation toRecommendation(
			DimensionAverage average) {
		Dimension dimension = average.dimension();
		return new ProfileInsightsResponse.TrainingRecommendation(
				dimension.id(),
				dimension.trainingType(),
				dimension.reason());
	}

	public record Result(
			ProfileInsightsResponse.WeaknessAnalysis analysis,
			List<ProfileInsightsResponse.AbilityWeakness> weaknesses,
			List<ProfileInsightsResponse.TrainingRecommendation> recommendations) {
	}

	private record DimensionAverage(
			Dimension dimension,
			BigDecimal averageScore,
			BigDecimal recentChange) {
	}

	private enum Dimension {
		ACCURACY(
				"accuracy",
				ProfileInsightsResponse.AbilityScores::accuracy,
				"CUSTOM_SCENE",
				"通过纠错型情景训练提升表达准确度"),
		FLUENCY(
				"fluency",
				ProfileInsightsResponse.AbilityScores::fluency,
				"FREE_CHAT",
				"通过自由对话和连续表达训练提升流利度"),
		GRAMMAR(
				"grammar",
				ProfileInsightsResponse.AbilityScores::grammar,
				"CUSTOM_SCENE",
				"通过语法纠错型情景训练巩固句式结构"),
		VOCABULARY(
				"vocabulary",
				ProfileInsightsResponse.AbilityScores::vocabulary,
				"CUSTOM_SCENE",
				"通过主题词汇和情景表达训练提升词汇运用"),
		NATURALNESS(
				"naturalness",
				ProfileInsightsResponse.AbilityScores::naturalness,
				"FREE_CHAT",
				"通过生活化自由对话训练提升表达自然度");

		private final String id;
		private final Function<ProfileInsightsResponse.AbilityScores, BigDecimal>
				score;
		private final String trainingType;
		private final String reason;

		Dimension(
				String id,
				Function<ProfileInsightsResponse.AbilityScores, BigDecimal> score,
				String trainingType,
				String reason) {
			this.id = id;
			this.score = score;
			this.trainingType = trainingType;
			this.reason = reason;
		}

		String id() {
			return id;
		}

		Function<ProfileInsightsResponse.AbilityScores, BigDecimal> score() {
			return score;
		}

		String trainingType() {
			return trainingType;
		}

		String reason() {
			return reason;
		}
	}
}
