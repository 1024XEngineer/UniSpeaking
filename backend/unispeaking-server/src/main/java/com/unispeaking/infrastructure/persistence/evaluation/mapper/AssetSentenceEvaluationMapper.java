package com.unispeaking.infrastructure.persistence.evaluation.mapper;

import com.unispeaking.infrastructure.persistence.evaluation.asset.AssetSentenceEvaluationTarget;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 学习资产句子的跟读评分查询与最新结果覆盖 SQL。
 */
public interface AssetSentenceEvaluationMapper {

	/**
	 * 查询未软删除场景下的参考句及所属用户。
	 */
	@Select("""
			SELECT
			    sentence.id AS "sentenceId",
			    sentence.sentence AS "referenceText",
			    scene.user_id AS "userId"
			FROM public.asset_sentences sentence
			INNER JOIN public.learning_assets asset
			    ON asset.id = sentence.learning_asset_id
			INNER JOIN public.custom_scenes scene
			    ON scene.id = asset.scene_id
			WHERE sentence.id = #{sentenceId}
			  AND scene.deleted_at IS NULL
			""")
	AssetSentenceEvaluationTarget selectEvaluationTarget(
			@Param("sentenceId") UUID sentenceId);

	/**
	 * 使用完整 JSONB 整体覆盖句子的最近一次成功跟读结果。
	 */
	@Update("""
			UPDATE public.asset_sentences
			SET reading_details = CAST(#{readingDetailsJson} AS jsonb)
			WHERE id = #{sentenceId}
			""")
	int updateReadingDetails(
			@Param("sentenceId") UUID sentenceId,
			@Param("readingDetailsJson") String readingDetailsJson);
}
