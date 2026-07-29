/**
 * QQ 发送者策略 — OpenID 白名单和 toolsBySender。
 */
export interface SenderPolicy {
    allowedOpenIds: string[];
    toolsBySender?: Record<string, string[]>;
}
export declare function isSenderAllowed(openId: string | undefined, policy: SenderPolicy): boolean;
export declare function getAllowedToolsForSender(openId: string | undefined, policy: SenderPolicy): string[] | null;
