package com.unispeaking.component.statemachine;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewTopicEvent;
import com.unispeaking.domain.vo.scene.InterviewTopicState;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 面试主题状态机（会话内子流程，进程内、随 live session 生灭、不落库）。
 *
 * <p>与 {@code ScenarioDialogueStateMachine}/{@code IeltsQuestionStateMachine} 同物种：
 * 状态只存在于内存态，进程重启会话即死，无恢复对象。
 * 不实现 {@code SceneFlowService}（无场景级阶段）。</p>
 *
 * <p>终止规则（优先级高→低）：① 连续 3 次 UNKNOWN → 结束；② 当前为第 5 主题且已
 * 完成 → 强制结束；③ 完成主题 ≥4 ∧ 两必选已完成 ∧ 当前主题完成 → 结束；④ 否则继续。</p>
 *
 * <p>生成主题数为硬顶（来自 {@code InterviewContext.interviewTopics}），状态机不自行增长；
 * 识别到列表外的主题视为 UNKNOWN，防 LLM 幻觉越界。难度只约束每主题追问次数上限。</p>
 */
@Component
public class InterviewTopicStateMachine {

	private final Map<String, State> states = new ConcurrentHashMap<>();

	/** 初始化一个会话的主题状态。 */
	public InterviewTopicState start(
			String sessionId,
			List<String> topics,
			InterviewDifficulty difficulty) {
		if (sessionId == null || sessionId.isBlank()) {
			throw new BusinessException("INTERVIEW_STATE_INVALID", "会话标识不能为空");
		}
		if (topics == null || topics.isEmpty()) {
			throw new BusinessException("INTERVIEW_STATE_INVALID", "面试主题不能为空");
		}
		State state = new State(sessionId, topics, difficulty);
		states.put(sessionId, state);
		return state.toState();
	}

	/**
	 * 推进一轮主题状态。{@code turnNo == lastProcessed+1} 强序，乱序抛
	 * {@code INTERVIEW_TURN_OUT_OF_ORDER}；已处理轮次短路返回当前态（幂等）。
	 */
	public InterviewTopicState advance(
			String sessionId,
			int turnNo,
			InterviewTopicEvent event) {
		if (turnNo < 1) {
			throw new BusinessException(
					"INTERVIEW_TURN_INVALID",
					"面试轮次必须大于 0");
		}
		return requireState(sessionId).advance(turnNo, event);
	}

	/** 返回当前状态；会话尚未启动时返回 {@code null}。 */
	public InterviewTopicState current(String sessionId) {
		State state = states.get(sessionId);
		return state == null ? null : state.toState();
	}

	/** 清除会话状态（会话结束时调用）。 */
	public void clear(String sessionId) {
		states.remove(sessionId);
	}

	private State requireState(String sessionId) {
		State state = states.get(sessionId);
		if (state == null) {
			throw new BusinessException(
					"INTERVIEW_STATE_NOT_FOUND",
					"面试主题状态不存在");
		}
		return state;
	}

	private static final class State {

		private final String sessionId;
		private final List<String> topics;
		private final int maxFollowUps;
		private final Set<Integer> processedTurns = new LinkedHashSet<>();
		private final Set<String> completedTopics = new HashSet<>();
		private final Set<String> completedMandatoryTopics = new HashSet<>();
		private String currentTopic;
		private int unknownStreak;
		private int followUpCount;
		private boolean currentTopicCompleted;
		private boolean shouldEnd;
		private int lastProcessedTurnNo;

		private State(
				String sessionId,
				List<String> topics,
				InterviewDifficulty difficulty) {
			this.sessionId = sessionId;
			this.topics = List.copyOf(topics);
			this.maxFollowUps = maxFollowUps(difficulty);
		}

		private synchronized InterviewTopicState advance(
				int turnNo,
				InterviewTopicEvent event) {
			if (shouldEnd || processedTurns.contains(turnNo)) {
				return toState();
			}
			if (turnNo != lastProcessedTurnNo + 1) {
				throw new BusinessException(
						InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
						"面试轮次乱序");
			}
			processedTurns.add(turnNo);
			lastProcessedTurnNo = turnNo;
			apply(event);
			return toState();
		}

		private synchronized InterviewTopicState toState() {
			return new InterviewTopicState(
					currentTopic,
					completedTopics.size(),
					unknownStreak,
					followUpCount,
					completedMandatoryTopics.size() >= 2,
					shouldEnd);
		}

		private void apply(InterviewTopicEvent event) {
			if (event == null) {
				recordUnknown();
				return;
			}
			String topic = normalize(event.topic());
			if (isUnknown(topic)) {
				recordUnknown();
				return;
			}
			String matched = matchTopic(topic);
			if (matched == null) {
				recordUnknown();
				return;
			}
			unknownStreak = 0;
			if (matched.equals(currentTopic)) {
				followUpCount = Math.min(followUpCount + 1, maxFollowUps);
				if (event.topicCompleted()) {
					completeCurrentTopic();
				}
			}
			else {
				completeCurrentTopic();
				currentTopic = matched;
				currentTopicCompleted = false;
				followUpCount = 0;
				if (event.topicCompleted()) {
					completeCurrentTopic();
				}
			}
			checkTermination();
		}

		private void recordUnknown() {
			unknownStreak++;
			if (unknownStreak >= 3) {
				shouldEnd = true;
			}
		}

		private void completeCurrentTopic() {
			if (currentTopic == null || currentTopicCompleted) {
				return;
			}
			currentTopicCompleted = true;
			completedTopics.add(currentTopic);
			if (isMandatoryTopic(currentTopic)) {
				completedMandatoryTopics.add(currentTopic);
			}
		}

		private void checkTermination() {
			if (shouldEnd) {
				return;
			}
			if (topics.size() >= 5
					&& topics.indexOf(currentTopic) == 4
					&& currentTopicCompleted) {
				shouldEnd = true;
				return;
			}
			if (completedTopics.size() >= 4
					&& completedMandatoryTopics.size() >= 2
					&& currentTopicCompleted) {
				shouldEnd = true;
			}
		}

		private String matchTopic(String topic) {
			return topics.stream()
					.filter(candidate -> candidate.equalsIgnoreCase(topic))
					.findFirst()
					.orElse(null);
		}

		private boolean isUnknown(String topic) {
			return topic == null
					|| topic.isBlank()
					|| "UNKNOWN".equalsIgnoreCase(topic);
		}

		private boolean isMandatoryTopic(String topic) {
			String value = topic.toLowerCase(Locale.ROOT);
			boolean selfIntroduction = value.contains("self-intro")
					|| value.contains("self intro")
					|| value.contains("introduce yourself")
					|| value.contains("about yourself")
					|| value.contains("tell me about yourself")
					|| value.contains("自我介绍");
			boolean experience = value.contains("experience")
					|| value.contains("project")
					|| value.contains("经历")
					|| value.contains("项目")
					|| value.contains("经验");
			return selfIntroduction || experience;
		}

		private int maxFollowUps(InterviewDifficulty difficulty) {
			return switch (difficulty == null
					? InterviewDifficulty.STANDARD
					: difficulty) {
				case EASY, STANDARD -> 1;
				case HARD -> 2;
			};
		}

		private String normalize(String topic) {
			return topic == null ? null : topic.strip();
		}
	}
}
