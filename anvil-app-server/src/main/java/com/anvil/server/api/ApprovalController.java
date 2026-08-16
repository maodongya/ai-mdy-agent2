package com.anvil.server.api;

import com.anvil.protocol.ApprovalDecision;
import com.anvil.server.service.ApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 审批控制器 —— 处理 HTTP 请求中与“审批”相关的操作。
 * <p>
 * 审批流程用于支持智能体在运行时需要人工确认或授权的场景（例如执行高风险操作、
 * 删除文件、修改受保护资源等）。前端（如 Web UI 或 CLI）通过本控制器将用户
 * 作出的决定转发给 {@link ApprovalService} 进行处理。
 *
 * @see ApprovalService
 * @see ApprovalDecision
 */
@RestController
@RequestMapping("/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * 构造审批控制器。
     *
     * @param approvalService 审批逻辑服务，由 Spring 容器注入
     */
    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /**
     * 响应一个待审批请求。
     * <p>
     * 对指定 {@code id} 的审批请求提交用户的决定（同意 / 拒绝等）。
     * 如果对应的审批请求不存在或已失效，则返回 HTTP 404（Not Found）。
     *
     * @param id   审批请求唯一标识（路径参数）
     * @param body 请求体，包含用户作出的决定（见 {@link ApprovalRespondRequest}）
     * @return 成功时返回 HTTP 200，并携带审批 ID 与被记录的决定的响应体；
     *         审批不存在时返回 HTTP 404。
     */
    @PostMapping("/{id}/respond")
    public ResponseEntity<Map<String, Object>> respond(
            @PathVariable("id") String id, @RequestBody ApprovalRespondRequest body) {
        // 将请求体中的 wire 格式决定转换为内部枚举
        ApprovalDecision decision = ApprovalDecision.fromWire(body.decision());
        // 若审批不存在或已失效，则返回 404
        if (!approvalService.respond(id, decision)) {
            return ResponseEntity.notFound().build();
        }
        // 返回审批 ID 以及最终记录的决定
        return ResponseEntity.ok(Map.of("approval_id", id, "decision", decision.wireValue()));
    }

    /**
     * 审批响应请求体 —— 用于表示用户对审批请求作出的决定。
     * <p>
     * 通过 Spring 的自动 JSON 反序列化绑定，字段名与前端协议约定保持一致。
     *
     * @param decision 用户作出的决定（wire 格式字符串，见 {@link ApprovalDecision#fromWire}）
     */
    public record ApprovalRespondRequest(String decision) {}
}
