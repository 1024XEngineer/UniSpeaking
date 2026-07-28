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
    IETLS_SCENE
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
    String sceneName;
    SceneType sceneType;
    List<LearningContentItem> wordList;
    List<LearningContentItem> phraseList;
    List<LearningContentItem> sentenceList;
    String scenePrompt;
}