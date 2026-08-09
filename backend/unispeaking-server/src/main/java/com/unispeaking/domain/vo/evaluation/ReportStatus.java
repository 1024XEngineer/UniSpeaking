package com.unispeaking.domain.vo.evaluation;

/** 面试报告生命周期状态：submitTurn 结束时置 PROCESSING，报告任务完成后置 COMPLETED/FAILED。 */
public enum ReportStatus {
	PROCESSING,
	COMPLETED,
	FAILED
}
