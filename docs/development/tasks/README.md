# Active Task Artifacts

本目录保存非简单任务的任务契约、当前状态和轻量交接，避免把未验收过程写入项目级完成记录。

推荐每个任务使用独立子目录：

```text
docs/development/tasks/<TASK-ID>/
├── TASK_CONTRACT.md
├── TASK_STATE.md
├── HANDOFF.md
└── INDEPENDENT_VERIFICATION.md
```

规则：

- 实现阶段只更新本目录中的任务局部状态。
- `SELF_CHECKED` 不等于项目级验收通过。
- 独立验收允许交付后，才由 `qta-delivery-finalization` 更新 `AI_HANDOFF`、开发日志、验收日志和建设看板。
- 任务结束后可保留最终契约与证据；不要存放密钥、长日志或完整聊天记录。
