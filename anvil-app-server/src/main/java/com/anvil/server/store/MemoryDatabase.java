package com.anvil.server.store;

import com.anvil.protocol.ProtocolJson;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 SQLite 的线程历史持久化存储（持久化阶段）。
 *
 * <p>负责将每个线程的会话历史以 JSON 形式落盘到 SQLite，
 * 支持按线程加载、保存（UPSERT）与清除。底层连接在
 * {@link #init()} 中懒启动建立，并在首次使用时自动建表。</p>
 */
@Component
final class MemoryDatabase {

    /** SQLite 数据库文件路径（默认为用户主目录下的 .anvil/memory.db）。 */
    private final Path dbPath;
    /** 数据库连接（volatile，懒初始化）。 */
    private volatile Connection connection;

    /**
     * 构造内存数据库，解析数据库文件路径（可配置）。
     *
     * @param dbPath 数据库文件路径，默认为 {@code ${user.home}/.anvil/memory.db}
     */
    MemoryDatabase(@Value("${anvil.memory.db-path:${user.home}/.anvil/memory.db}") String dbPath) {
        this.dbPath = Path.of(dbPath).toAbsolutePath().normalize();
    }

    /**
     * 初始化：确保父目录存在、建立连接并创建线程历史表（如不存在）。
     */
    @PostConstruct
    void init() throws Exception {
        Files.createDirectories(dbPath.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (var st = connection.createStatement()) {
            st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS thread_history (
                      thread_id TEXT PRIMARY KEY,
                      history_json TEXT NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    /**
     * 加载指定线程的历史消息列表。
     *
     * @param threadId 线程标识
     * @return 历史消息列表（JSON 解析结果）；无记录或异常时返回空列表
     */
    List<Map<String, Object>> load(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return List.of();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT history_json FROM thread_history WHERE thread_id = ?")) {
            ps.setString(1, threadId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return List.of();
                }
                return parseHistory(rs.getString(1));
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 保存（覆盖）指定线程的历史消息到数据库。
     *
     * <p>使用 {@code ON CONFLICT ... DO UPDATE} 实现 UPSERT，
     * 若指定线程已存在记录则更新其内容与更新时间。</p>
     *
     * @param threadId 线程标识
     * @param history  待保存的历史消息列表（JSON 序列化后落盘）
     */
    void save(String threadId, List<Map<String, Object>> history) {
        if (threadId == null || threadId.isBlank() || history == null || history.isEmpty()) {
            return;
        }
        try {
            String json = ProtocolJson.toJson(history);
            try (PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO thread_history(thread_id, history_json, updated_at)
                    VALUES(?, ?, ?)
                    ON CONFLICT(thread_id) DO UPDATE SET
                      history_json = excluded.history_json,
                      updated_at = excluded.updated_at
                    """)) {
                ps.setString(1, threadId);
                ps.setString(2, json);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
            // 保存失败时静默降级：依赖 ThreadMemoryStore 短暂保留内存缓存
        }
    }

    /**
     * 清除指定线程的持久化历史记录。
     *
     * @param threadId 线程标识
     */
    void clear(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM thread_history WHERE thread_id = ?")) {
            ps.setString(1, threadId);
            ps.executeUpdate();
        } catch (Exception ignored) {
            // 清除失败视为无可再清，忽略
        }
    }

    /**
     * 将数据库中的 JSON 字符串解析为历史消息列表（Map 列表）。
     *
     * @param json 待解析的 JSON
     * @return 历史消息列表；非列表结构时返回空列表
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseHistory(String json) throws Exception {
        Object parsed = ProtocolJson.mapper().readValue(json, Object.class);
        if (!(parsed instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return List.copyOf(out);
    }
}
