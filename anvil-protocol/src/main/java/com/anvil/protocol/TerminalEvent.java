package com.anvil.protocol;

/**
 * 终端（Terminal）事件。
 *
 * <p>用于将终端会话中的输出、任务状态、错误等信息通过 SSE 流式推送给前端。</p>
 *
 * @param type   事件类型：terminal.output / terminal.job_start / terminal.job_done / terminal.status / terminal.error
 * @param sessionId 终端会话 ID
 * @param seq    事件序号（单调递增，用于增量消费）
 * @param payload 事件载荷
 */
public record TerminalEvent(String type, String sessionId, long seq, java.util.Map<String, Object> payload) {}
