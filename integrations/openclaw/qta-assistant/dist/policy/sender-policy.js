/**
 * QQ 发送者策略 — OpenID 白名单和 toolsBySender。
 */
export function isSenderAllowed(openId, policy) {
    if (!openId)
        return false;
    if (policy.allowedOpenIds.length === 0)
        return false;
    return policy.allowedOpenIds.includes(openId);
}
export function getAllowedToolsForSender(openId, policy) {
    if (!openId || !policy.toolsBySender)
        return null;
    return policy.toolsBySender[openId] ?? null;
}
