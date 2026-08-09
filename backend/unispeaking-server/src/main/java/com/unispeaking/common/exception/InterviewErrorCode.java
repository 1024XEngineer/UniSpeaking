package com.unispeaking.common.exception;

/** Interview 场景错误码常量，供业务抛错与 {@link GlobalExceptionHandler} 映射。 */
public final class InterviewErrorCode {

	public static final String INTERVIEW_MATERIAL_INVALID = "INTERVIEW_MATERIAL_INVALID";
	public static final String INTERVIEW_REQUEST_INVALID = "INTERVIEW_REQUEST_INVALID";
	public static final String INTERVIEW_SCENE_NOT_FOUND = "INTERVIEW_SCENE_NOT_FOUND";
	public static final String INTERVIEW_SCENE_ACCESS_DENIED = "INTERVIEW_SCENE_ACCESS_DENIED";
	public static final String INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID = "INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID";
	public static final String INTERVIEW_MATERIAL_LLM_RESPONSE_INVALID = "INTERVIEW_MATERIAL_LLM_RESPONSE_INVALID";
	public static final String INTERVIEW_SCENE_PERSISTENCE_FAILED = "INTERVIEW_SCENE_PERSISTENCE_FAILED";
	public static final String INTERVIEW_DAILY_LIMIT_REACHED = "INTERVIEW_DAILY_LIMIT_REACHED";

	private InterviewErrorCode() {
	}
}
