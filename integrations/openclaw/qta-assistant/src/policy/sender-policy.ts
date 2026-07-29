/**
 * QQ 发送者策略 — OpenID 白名单和 toolsBySender。
 */

export interface SenderPolicy {
  allowedOpenIds: string[];
  toolsBySender?: Record<string, string[]>;
}

export function isSenderAllowed(openId: string | undefined, policy: SenderPolicy): boolean {
  if (!openId) return false;
  if (policy.allowedOpenIds.length === 0) return false;
  return policy.allowedOpenIds.includes(openId);
}

export function getAllowedToolsForSender(openId: string | undefined, policy: SenderPolicy): string[] | null {
  if (!openId || !policy.toolsBySender) return null;
  return policy.toolsBySender[openId] ?? null;
}
