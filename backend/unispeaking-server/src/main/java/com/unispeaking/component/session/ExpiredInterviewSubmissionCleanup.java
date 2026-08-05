package com.unispeaking.component.session;

@FunctionalInterface
public interface ExpiredInterviewSubmissionCleanup {

	void cleanup(ExpiredInterviewSubmissionCleanupRequest request);
}
