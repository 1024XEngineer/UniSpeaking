package com.unispeaking.domain.vo.scene;

/**
 * Normalized creation materials that exist only while an Interview is being created.
 */
public final class InterviewPreparedMaterials {

	private final String jobTitle;
	private final String jobDescription;
	private final String resumeText;

	InterviewPreparedMaterials(
			String jobTitle,
			String jobDescription,
			String resumeText) {
		this.jobTitle = jobTitle;
		this.jobDescription = jobDescription;
		this.resumeText = resumeText;
	}

	public String jobTitle() {
		return jobTitle;
	}

	public String jobDescription() {
		return jobDescription;
	}

	public String resumeText() {
		return resumeText;
	}

	public TargetRoleSummaryGenerationInput targetRoleSummaryInput() {
		return new TargetRoleSummaryGenerationInput(jobTitle, jobDescription);
	}

	public InterviewQuestionPlanGenerationInput questionPlanInput(
			InterviewDifficulty difficulty,
			TargetRoleSummary roleSummary) {
		return new InterviewQuestionPlanGenerationInput(
				difficulty,
				roleSummary,
				resumeText);
	}

	@Override
	public String toString() {
		return "InterviewPreparedMaterials[jobTitle=<redacted>, "
				+ "jobDescription=<redacted>, resumeText=<redacted>]";
	}
}
