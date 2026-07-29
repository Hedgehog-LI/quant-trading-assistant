/**
 * QTA Agent API 客户端 — 独立连接超时、总超时、重试和错误翻译。
 *
 * connectTimeoutMs: 仅约束"收到响应头"之前的连接阶段。
 *   fetch 一旦返回（响应头到达），连接计时器立即清除，
 *   不会因响应体较大/较慢而误杀正常请求。
 * totalTimeoutMs: 覆盖整条请求链路，包括响应头 + 响应体解析(resp.json)。
 *   只有当响应体解析完成、或抛出后才清除总计时器，
 *   确保总超时真正覆盖 body 解析阶段。
 * 重试：仅连接超时、总超时、网络瞬断(ENOTFOUND/ECONNRESET)、502/503 重试一次；
 *   401/403/429/500 不重试。
 * 外部 AbortSignal：若调用方传入 externalSignal，其 abort 会立即终止请求且不重试。
 * 所有计时器在 finally 中清理，避免泄漏。
 */
export interface QtaConfig {
    baseUrl: string;
    token: string;
    connectTimeoutMs?: number;
    totalTimeoutMs?: number;
}
export interface TrustedAnswer {
    conclusion: string;
    generatedAt: string;
    dataAsOf: string | null;
    freshnessStatus: 'FRESH' | 'DELAYED' | 'STALE' | 'UNKNOWN';
    evidence: Array<{
        type: string;
        id: string;
        observedAt: string;
    }>;
    warnings: string[];
    data: unknown;
}
/**
 * 判定某错误/状态码是否可重试。
 * 外部 signal 主动 abort 不可重试（调用方意图终止）。
 */
export declare function isRetryableError(status: number | undefined, error: Error | undefined): boolean;
export declare class QtaClient {
    private baseUrl;
    private token;
    private connectTimeoutMs;
    private totalTimeoutMs;
    constructor(config: QtaConfig);
    /**
     * Build URL with proper encoding using URLSearchParams for query params
     * and encodeURIComponent for path segments.
     */
    private buildUrl;
    /**
     * 组合多个 AbortSignal：任一 abort 即生效。
     * 优先使用 AbortSignal.any(Node 20.3+)；否则手动桥接。
     */
    private composeSignals;
    get(path: string, queryParams?: Record<string, unknown>, externalSignal?: AbortSignal): Promise<TrustedAnswer>;
}
