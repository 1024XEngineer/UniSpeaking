package com.unispeaking.component.session;

@FunctionalInterface
public interface ExpiredInterviewCleanup {

	/**
	 * Handles the cross-service effects for an interview that can no longer be
	 * resumed: fail its practice session, complete its flow as unsuccessful,
	 * delete the unfinished interview, and remove remaining temporary data.
	 */
	void cleanup(ExpiredInterviewCleanupRequest request);
}
