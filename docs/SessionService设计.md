package com.unispeaking.session;

public interface SessionService {

    /**
     * 开始一次业务会话，生成 sessionId 并记录开始时间。
     */
    StartSessionResponse startSession(String prompt);

    /**
     * 向当前会话中追加一条用户或 AI 的完整消息。
     *
     * 只保存最终完整文本，不保存流式 delta。
     * 消息可以先追加到内存，再异步写入数据库。
     */
    void addMessage(Message message);

    /**
     * 结束当前业务会话，记录结束时间。
     */
    void endSession(String sessionId, String stopTime);
}
StartSessionResponse {
    String sessionId;
    String startTime;
}
Message {
    Integer owner;    // 1：用户，0：模型
    String content;   // 用户或模型的完整文本
    byte[] audio;     // 可选音频，模型消息通常为空
}

`SessionService` 是业务会话接口，不负责 Offer/Answer SDP。每次业务会话
对应一个 prototype `FreeChatSessionService` 或
`CustomSceneSessionService` 实例，因此 `addMessage(Message)` 可以安全地
操作该实例持有的当前会话。外层 `SessionServiceSelector` 根据 WebSocket
帧中的 `sessionId` 找到对应实例。Realtime 建连由
`RealtimeSessionConnector` 单独编排。

自由聊天的 `addMessage(Message)` 通过 `FreeChatConversationService` 把
用户和 AI 的最终文本追加到 Redis List。Redis 只保存文本消息元数据，
不保存音频和流式 delta，并通过 TTL 自动清理临时会话消息。
