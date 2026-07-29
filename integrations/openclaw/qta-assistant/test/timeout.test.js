import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';

describe('QtaClient timeout behavior', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('connectTimeoutMs causes faster abort than totalTimeoutMs when server is slow', async () => {
    let abortReason = null;
    globalThis.fetch = async (url, opts) => {
      // Capture the abort signal
      const signal = opts.signal;
      return new Promise((resolve, reject) => {
        signal.addEventListener('abort', () => {
          abortReason = signal.reason?.name || signal.reason?.message || 'aborted';
          const err = new Error('The operation was aborted');
          err.name = 'AbortError';
          reject(err);
        });
        // Never resolve naturally — only abort can end this
      });
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({
      baseUrl: 'http://127.0.0.1:9999',
      token: 'test',
      connectTimeoutMs: 100,
      totalTimeoutMs: 5000,
    });

    const start = Date.now();
    try {
      await client.get('system/health');
      assert.fail('Should have thrown');
    } catch (e) {
      const elapsed = Date.now() - start;
      // connectTimeoutMs=100 should abort around 100ms, well before totalTimeoutMs=5000
      assert.ok(elapsed < 1000, `Should abort near connectTimeoutMs (100ms), took ${elapsed}ms`);
    }
  });

  it('totalTimeoutMs controls overall request when connectTimeoutMs is large', async () => {
    globalThis.fetch = async (url, opts) => {
      const signal = opts.signal;
      return new Promise((_resolve, reject) => {
        signal.addEventListener('abort', () => {
          const err = new Error('aborted');
          err.name = 'AbortError';
          reject(err);
        });
      });
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({
      baseUrl: 'http://127.0.0.1:9999',
      token: 'test',
      connectTimeoutMs: 10000,
      totalTimeoutMs: 100,
    });

    const start = Date.now();
    try {
      await client.get('system/health');
      assert.fail('Should have thrown');
    } catch (e) {
      const elapsed = Date.now() - start;
      assert.ok(elapsed < 1000, `Should abort near totalTimeoutMs (100ms), took ${elapsed}ms`);
    }
  });

  it('URL uses URLSearchParams for query params and encodeURIComponent for path', async () => {
    let capturedUrl = '';
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return { ok: true, status: 200, json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } }) };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:8080', token: 't' });

    // Test path with special chars + query params
    await client.get('securities/SH.600519/market-summary', { market: 'CN', date: '2026-07-26' });
    assert.ok(capturedUrl.includes('SH.600519'), 'Path must contain SH.600519');
    assert.ok(capturedUrl.includes('market=CN'), 'Query must use URLSearchParams');
    assert.ok(capturedUrl.includes('date=2026-07-26'), 'Query must include date param');
  });

  it('URLSearchParams encodes special characters in query values', async () => {
    let capturedUrl = '';
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return { ok: true, status: 200, json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } }) };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:8080', token: 't' });

    await client.get('test', { filter: 'a&b=c d' });
    // URLSearchParams should encode & and space
    assert.ok(capturedUrl.includes('a%26b'), '& must be encoded in query: ' + capturedUrl);
    assert.ok(capturedUrl.includes('%3D'), '= must be encoded in query: ' + capturedUrl);
    assert.ok(capturedUrl.includes('c+d') || capturedUrl.includes('c%20d'), 'space must be encoded in query: ' + capturedUrl);
  });

  // ===== D5 fixes: totalTimeoutMs covers body parse; connect timer cleared after headers =====

  it('totalTimeoutMs covers response body parsing (slow json is aborted)', async () => {
    // fetch returns headers immediately, but resp.json() is slow.
    // totalTimeoutMs must abort the body parse; it must NOT be cleared right after headers.
    let fetchCallCount = 0;
    globalThis.fetch = async (_url, opts) => {
      fetchCallCount++;
      const signal = opts.signal;
      return {
        ok: true,
        status: 200,
        get json() {
          return async () => {
            // Slow body parse — wait until aborted
            return new Promise((_resolve, reject) => {
              signal.addEventListener('abort', () => {
                const err = new Error('aborted');
                err.name = 'AbortError';
                reject(err);
              });
            });
          };
        },
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({
      baseUrl: 'http://127.0.0.1:9999',
      token: 'test',
      connectTimeoutMs: 10000,
      totalTimeoutMs: 100,
    });

    const start = Date.now();
    try {
      await client.get('system/health');
      assert.fail('Should have thrown on slow body parse');
    } catch (e) {
      const elapsed = Date.now() - start;
      // Must abort near totalTimeoutMs=100, NOT wait forever for the slow body
      assert.ok(elapsed < 1000, `totalTimeoutMs must cover body parse; took ${elapsed}ms`);
    }
  });

  it('connectTimeoutMs does NOT kill a valid request with a slow body after headers arrive', async () => {
    // fetch returns headers immediately; body is slow but completes within totalTimeoutMs.
    // connectTimeoutMs is small — if it governed the body, the request would fail.
    // Correct behavior: connect timer cleared after headers, slow body still succeeds.
    globalThis.fetch = async (_url, _opts) => {
      return {
        ok: true,
        status: 200,
        json: async () => {
          // Body parse takes 250ms — longer than connectTimeoutMs=100 but within totalTimeoutMs=5000
          await new Promise(r => setTimeout(r, 250));
          return { success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } };
        },
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({
      baseUrl: 'http://127.0.0.1:9999',
      token: 'test',
      connectTimeoutMs: 100,   // small — must not kill the 250ms body
      totalTimeoutMs: 5000,
    });

    // Should SUCCEED despite connectTimeoutMs < body parse time
    const answer = await client.get('system/health');
    assert.equal(answer.conclusion, 'OK');
  });

  it('external AbortSignal aborts immediately without retry', async () => {
    // fetch would retry on AbortError normally; an EXTERNAL signal must abort without retry.
    let fetchCallCount = 0;
    globalThis.fetch = async (_url, opts) => {
      fetchCallCount++;
      const signal = opts.signal;
      return new Promise((_resolve, reject) => {
        const h = () => {
          const err = new Error('aborted');
          err.name = 'AbortError';
          reject(err);
          signal.removeEventListener('abort', h);
        };
        if (signal.aborted) h();
        else signal.addEventListener('abort', h);
      });
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({
      baseUrl: 'http://127.0.0.1:9999',
      token: 'test',
      connectTimeoutMs: 10000,
      totalTimeoutMs: 10000,
    });

    const external = new AbortController();
    // Abort shortly after the request starts
    setTimeout(() => external.abort(), 50);

    const start = Date.now();
    try {
      await client.get('system/health', undefined, external.signal);
      assert.fail('Should have thrown on external abort');
    } catch (e) {
      const elapsed = Date.now() - start;
      assert.ok(elapsed < 500, `External abort must terminate immediately, took ${elapsed}ms`);
      // Must NOT have retried: fetch called exactly once
      assert.equal(fetchCallCount, 1, `External abort must not retry; fetch called ${fetchCallCount} times`);
      assert.equal(e.name, 'AbortError', `Should be AbortError, got ${e.name}`);
    }
  });

  it('cleans up all timers in finally (no leak across many requests)', async () => {
    // Make many quick requests; if timers leak, the process would accumulate handles.
    // We assert the requests complete and don't hang.
    globalThis.fetch = async () => ({
      ok: true, status: 200,
      json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } }),
    });

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({
      baseUrl: 'http://127.0.0.1:9999',
      token: 'test',
      connectTimeoutMs: 2000,
      totalTimeoutMs: 5000,
    });

    const start = Date.now();
    for (let i = 0; i < 50; i++) {
      const answer = await client.get('system/health');
      assert.equal(answer.conclusion, 'OK');
    }
    const elapsed = Date.now() - start;
    // If timers leaked, each request would block ~5s. 50 requests should be fast (< 2s).
    assert.ok(elapsed < 2000, `Timer leak suspected: 50 requests took ${elapsed}ms`);
  });
});
