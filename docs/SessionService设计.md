package com.unispeaking.session;

public interface SessionService {

    /**
     * 开始一次业务会话，生成 sessionId 并记录开始时间。
     */
    StartSessionResponse startSession(
        StartSessionRequest request
    );

    /**
     * 向当前会话中追加一条用户或 AI 的完整消息。
     *
     * 只保存最终完整文本，不保存流式 delta。
     * 消息可以先追加到内存，再异步写入数据库。
     */
    void addMessage(
        AddSessionMessageRequest request
    );

    /**
     * 结束当前业务会话，记录结束时间。
     */
    EndSessionResponse endSession(
        EndSessionRequest request
    );
}
StartSessionRequest {
    SceneType sceneType;
    String prompt;
}
StartSessionResponse {
    String sessionId;
    String startTime;
}
EndSessionRequest {
    String sessionId;
}
EndSessionResponse {
    String sessionId;
    String stopTime;
}
AppendSessionMessageRequest {
    String sessionId;
    Message message;
}
Message {
    Integer owner;    // 1：用户，0：模型
    String content;   // 用户或模型的完整文本
    byte[] audio;     // 可选音频，模型消息通常为空
}