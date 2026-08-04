package com.unispeaking.common.util.search;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class TitleRelevanceCalculator {

	public double score(String title, String keyword) {
		String normalizedTitle = normalize(title);
		String normalizedKeyword = normalize(keyword);
		if (normalizedTitle.isEmpty() || normalizedKeyword.isEmpty()) {
			return 0;
		}
		if (normalizedTitle.equals(normalizedKeyword)) {
			return 1;
		}
		double containsScore = normalizedTitle.contains(normalizedKeyword)
				? 0.8 + 0.2 * normalizedKeyword.length()
						/ normalizedTitle.length()
				: 0;
		return Math.max(containsScore, diceCoefficient(
				normalizedTitle,
				normalizedKeyword));
	}

	public boolean isKeywordMatch(String title, String keyword) {
		String normalizedTitle = normalize(title);
		String normalizedKeyword = normalize(keyword);
		return !normalizedTitle.isEmpty()
				&& !normalizedKeyword.isEmpty()
				&& normalizedTitle.contains(normalizedKeyword);
	}

	private double diceCoefficient(String left, String right) {
		Set<String> leftGrams = grams(left);
		Set<String> rightGrams = grams(right);
		if (leftGrams.isEmpty() || rightGrams.isEmpty()) {
			return 0;
		}
		long intersection = leftGrams.stream()
				.filter(rightGrams::contains)
				.count();
		return 2.0 * intersection / (leftGrams.size() + rightGrams.size());
	}

	private Set<String> grams(String value) {
		int[] points = value.codePoints().toArray();
		int size = points.length < 3 ? 1 : 3;
		Set<String> result = new HashSet<>();
		for (int index = 0; index <= points.length - size; index++) {
			result.add(new String(points, index, size));
		}
		return result;
	}

	private String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT)
				.replaceAll("[^\\p{L}\\p{N}]", "");
	}
}
