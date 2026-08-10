package com.unispeaking.infrastructure.persistence.repository.scene;

import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import java.util.List;
import java.util.Optional;

/** 面试场景资产仓储：Service 访问 interview_scene 的唯一入口。 */
public interface InterviewSceneRepository {

	/** 保存面试场景资产；数据库时间戳由列默认值/触发器维护。 */
	void save(InterviewSceneDefinition definition);

	/** 按场景标识查询，软删过滤。 */
	Optional<InterviewSceneDefinition> findById(String sceneId);

	/** 按用户查询全部未删除场景，按更新时间倒序。 */
	List<InterviewSceneDefinition> findByUserId(String userId);

	/** 归属辅助：查询指定用户拥有的未删除场景。 */
	Optional<InterviewSceneDefinition> findOwnedById(String sceneId, String userId);

	/** 软删：deleted_at 置当前时间；仅限本人未删除场景，返回是否更新。 */
	boolean softDelete(String sceneId, String userId);
}
