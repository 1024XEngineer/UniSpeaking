public interface SceneService {

    /**
     * 根据场景类型和用户输入生成场景内容。
     */
    SceneGenerationResponse generateScene(
        SceneGenerationRequest request
    );
}
enum SceneType {
    FREE_CHAT,
    CUSTOM_SCENE,
    INTERVIEW_SCENE,
    IELTS_SCENE
}
LearningContentItem {
    String contentId;
    String englishText;
    String chineseText;
    String phonetic;
}
SceneGenerationRequest {
    String userId;
    String userPreference;
    SceneType sceneType;
    String sceneInput;
}
SceneGenerationResponse {
    String sceneId;
    List<LearningContentItem> wordList;
    List<LearningContentItem> phraseList;
    List<LearningContentItem> sentenceList;
    String scenePrompt;
}

`SceneService.generateScene(...)` 在一次调用中完成身份校验、Profile
读取、五层 Prompt 合成和场景结果保存。`scenePrompt` 是已经按 L1-L5
顺序合成的完整字符串；Session 启动时直接传给 Realtime Provider，
不再重复生成或拼接 Prompt。

`sceneId` 使用场景类型前缀：`freechat_`、`custom_`、`interview_`、
`ielts_`。前缀后的随机部分为不带连字符的 UUID。
