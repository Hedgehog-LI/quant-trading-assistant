---
description: Verify that the installed QTA user-level ZCode Hook is active in this real task.
disable-noninteractive: false
---

QTA_GOVERNANCE_DOCTOR

Run exactly this command before doing anything else:

```bash
node scripts/qta-governance-doctor.mjs --runtime
```

Return only the command verdict and its remediation if it fails. Do not edit files, dispatch Agents, create
synthetic Hook input, or invoke `scripts/zcode-governance-hook.mjs` manually. This command validates a real
ZCode `UserPromptSubmit -> PreToolUse` event sequence and does not install or use a Stop Hook.
