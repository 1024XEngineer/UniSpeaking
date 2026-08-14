package com.unispeaking.service.scene;

import com.unispeaking.common.exception.BusinessException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 场景阶段流转的通用实现，由子类提供首阶段、下一阶段和结束阶段规则。
 */
public class SceneFlowService<S> {

	private final Function<String, S> starter;
	private final BiFunction<String, S, S> advancer;
	private final Predicate<S> completionChecker;
	private final String notStartedMessage;
	private final Map<String, S> stages = new ConcurrentHashMap<>();

	public SceneFlowService(
			Function<String, S> starter,
			BiFunction<String, S, S> advancer,
			Predicate<S> completionChecker,
			String notStartedMessage) {
		this.starter = starter;
		this.advancer = advancer;
		this.completionChecker = completionChecker;
		this.notStartedMessage = notStartedMessage;
	}

	/** 初始化场景流程并返回第一个阶段。 */
	public S start(String sceneId) {
		S stage = starter.apply(sceneId);
		stages.put(sceneId, stage);
		return stage;
	}

	/** 返回场景当前所处的阶段。 */
	public S current(String sceneId) {
		S stage = stages.get(sceneId);
		if (stage == null) {
			throw new BusinessException(
					"SCENE_FLOW_NOT_FOUND",
					notStartedMessage);
		}
		return stage;
	}

	/** 推进场景流程并返回新的阶段。 */
	public S next(String sceneId) {
		S stage = advancer.apply(sceneId, current(sceneId));
		stages.put(sceneId, stage);
		return stage;
	}

	/** 判断场景流程是否已经到达结束阶段。 */
	public boolean isCompleted(String sceneId) {
		return completionChecker.test(current(sceneId));
	}

	/** 清除指定场景缓存的流程阶段。 */
	public void clear(String sceneId) {
		stages.remove(sceneId);
	}
}
