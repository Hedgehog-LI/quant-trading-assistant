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
/**
 * 判定某错误/状态码是否可重试。
 * 外部 signal 主动 abort 不可重试（调用方意图终止）。
 */
export function isRetryableError(status, error) {
    if (error) {
        const name = error.name;
        const msg = error.message || '';
        if (name === 'AbortError' || name === 'TimeoutError')
            return true;
        if (name === 'TypeError' && (msg.includes('fetch failed') || msg.includes('ENOTFOUND') || msg.includes('ECONNRESET')))
            return true;
        return false;
    }
    if (status === 502 || status === 503)
        return true;
    return false;
}
export class QtaClient {
    baseUrl;
    token;
    connectTimeoutMs;
    totalTimeoutMs;
    constructor(config) {
        this.baseUrl = config.baseUrl.replace(/\/$/, '');
        this.token = config.token;
        this.connectTimeoutMs = config.connectTimeoutMs ?? 2000;
        this.totalTimeoutMs = config.totalTimeoutMs ?? 10000;
    }
    /**
     * Build URL with proper encoding using URLSearchParams for query params
     * and encodeURIComponent for path segments.
     */
    buildUrl(path, params) {
        const encodedPath = path.split('/').map(seg => encodeURIComponent(seg)).join('/');
        const url = `${this.baseUrl}/api/v1/agent/${encodedPath}`;
        if (!params)
            return url;
        const searchParams = new URLSearchParams();
        for (const [key, value] of Object.entries(params)) {
            if (value !== undefined && value !== null) {
                searchParams.set(key, String(value));
            }
        }
        const qs = searchParams.toString();
        return qs ? `${url}?${qs}` : url;
    }
    /**
     * 组合多个 AbortSignal：任一 abort 即生效。
     * 优先使用 AbortSignal.any(Node 20.3+)；否则手动桥接。
     */
    composeSignals(signals) {
        if (typeof AbortSignal.any === 'function') {
            return { signal: AbortSignal.any(signals), cleanup: () => { } };
        }
        const controller = new AbortController();
        const handlers = [];
        for (const s of signals) {
            if (s.aborted) {
                controller.abort(s.reason);
                return { signal: controller.signal, cleanup: () => { } };
            }
            const h = () => controller.abort(s.reason);
            s.addEventListener('abort', h);
            handlers.push({ s, h });
        }
        return {
            signal: controller.signal,
            cleanup: () => handlers.forEach(({ s, h }) => s.removeEventListener('abort', h)),
        };
    }
    async get(path, queryParams, externalSignal) {
        const url = this.buildUrl(path, queryParams);
        let lastError = null;
        for (let attempt = 0; attempt < 2; attempt++) {
            // 外部 signal 已 abort → 立即终止，不重试
            if (externalSignal?.aborted) {
                const err = new Error('Aborted by external signal');
                err.name = 'AbortError';
                throw err;
            }
            // 总超时控制器：覆盖头 + body 解析全链路
            const totalController = new AbortController();
            const totalTimer = setTimeout(() => totalController.abort(), this.totalTimeoutMs);
            // 连接超时控制器：仅约束到响应头到达为止
            const connectController = new AbortController();
            const connectTimer = setTimeout(() => connectController.abort(), this.connectTimeoutMs);
            // 组合信号：连接 / 总超时 / 外部 signal 任一触发即 abort
            const signals = [totalController.signal, connectController.signal];
            if (externalSignal)
                signals.push(externalSignal);
            const { signal: combined, cleanup: cleanupCompose } = this.composeSignals(signals);
            // 记录 abort 来源，用于决定是否重试
            let abortedByExternal = false;
            const externalHandler = () => { abortedByExternal = true; };
            if (externalSignal) {
                if (externalSignal.aborted)
                    abortedByExternal = true;
                else
                    externalSignal.addEventListener('abort', externalHandler);
            }
            try {
                const resp = await fetch(url, {
                    method: 'GET',
                    headers: {
                        'Authorization': `Bearer ${this.token}`,
                        'Accept': 'application/json',
                    },
                    signal: combined,
                });
                // 响应头已到达：连接阶段结束，清除连接计时器，
                // 避免大响应体被 connectTimeoutMs 误杀。totalTimer 保留以覆盖 body 解析。
                clearTimeout(connectTimer);
                if (resp.ok) {
                    // totalTimeoutMs 必须覆盖 body 解析：在 resp.json() 完成前不清除 totalTimer
                    const body = await resp.json();
                    // body 解析完成，现在才能安全清除总计时器
                    clearTimeout(totalTimer);
                    if (!body.success || !body.data) {
                        throw new Error(body.message ?? 'QTA API returned failure');
                    }
                    return body.data;
                }
                if (isRetryableError(resp.status, undefined)) {
                    lastError = new Error(`QTA API ${resp.status}`);
                    if (attempt === 0) {
                        await new Promise(r => setTimeout(r, 500));
                        continue;
                    }
                    const text = await resp.text().catch(() => '');
                    clearTimeout(totalTimer);
                    throw new Error(`QTA API ${resp.status}: ${text.substring(0, 200)}`);
                }
                // Non-retryable
                const text = await resp.text().catch(() => '');
                clearTimeout(totalTimer);
                throw new Error(`QTA API ${resp.status}: ${text.substring(0, 200)}`);
            }
            catch (e) {
                // 外部 signal 触发的 abort：立即抛出，不重试
                if (abortedByExternal || (externalSignal?.aborted && e instanceof Error && (e.name === 'AbortError' || e.name === 'TimeoutError'))) {
                    const err = new Error('Aborted by external signal');
                    err.name = 'AbortError';
                    throw err;
                }
                if (e instanceof Error) {
                    if (e.message.startsWith('QTA API ') && !isRetryableError(undefined, e)) {
                        throw e;
                    }
                    if (isRetryableError(undefined, e)) {
                        lastError = e;
                        if (attempt === 0) {
                            await new Promise(r => setTimeout(r, 500));
                            continue;
                        }
                    }
                    throw e;
                }
                throw new Error(String(e));
            }
            finally {
                // 始终清理所有计时器与监听器，避免泄漏
                clearTimeout(totalTimer);
                clearTimeout(connectTimer);
                cleanupCompose();
                if (externalSignal)
                    externalSignal.removeEventListener('abort', externalHandler);
            }
        }
        throw lastError ?? new Error('Unknown error after retries');
    }
}
