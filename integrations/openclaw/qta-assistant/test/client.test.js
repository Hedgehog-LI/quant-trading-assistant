import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';

describe('QtaClient retry logic', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('succeeds on first try (200)', async () => {
    let calls = 0;
    globalThis.fetch = async (url, opts) => {
      calls++;
      return {
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          data: {
            conclusion: 'OK',
            generatedAt: '2026-01-01T00:00:00Z',
            dataAsOf: null,
            freshnessStatus: 'FRESH',
            evidence: [],
            warnings: [],
            data: { test: true },
          }
        })
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    const result = await client.get('system/health');
    assert.equal(calls, 1, 'should only call once');
    assert.equal(result.conclusion, 'OK');
  });

  it('retries on 502 then succeeds', async () => {
    let calls = 0;
    globalThis.fetch = async (url, opts) => {
      calls++;
      if (calls === 1) {
        return { ok: false, status: 502, text: async () => 'Bad Gateway' };
      }
      return {
        ok: true,
        status: 200,
        json: async () => ({
          success: true,
          data: {
            conclusion: 'OK after retry',
            generatedAt: '2026-01-01T00:00:00Z',
            dataAsOf: null,
            freshnessStatus: 'FRESH',
            evidence: [],
            warnings: [],
            data: {},
          }
        })
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    const result = await client.get('system/health');
    assert.equal(calls, 2, 'should retry once on 502');
    assert.equal(result.conclusion, 'OK after retry');
  });

  it('retries on 503 then succeeds', async () => {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      if (calls === 1) return { ok: false, status: 503, text: async () => 'Unavailable' };
      return {
        ok: true, status: 200,
        json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } })
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    const result = await client.get('system/health');
    assert.equal(calls, 2);
    assert.equal(result.conclusion, 'OK');
  });

  it('does NOT retry on 401', async () => {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      return { ok: false, status: 401, text: async () => 'Unauthorized' };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'wrong' });
    await assert.rejects(async () => client.get('system/health'), /401/);
    assert.equal(calls, 1, 'should NOT retry on 401');
  });

  it('does NOT retry on 403', async () => {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      return { ok: false, status: 403, text: async () => 'Forbidden' };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    await assert.rejects(async () => client.get('system/health'), /403/);
    assert.equal(calls, 1, 'should NOT retry on 403');
  });

  it('does NOT retry on 429', async () => {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      return { ok: false, status: 429, text: async () => 'Too Many Requests' };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    await assert.rejects(async () => client.get('system/health'), /429/);
    assert.equal(calls, 1, 'should NOT retry on 429');
  });

  it('does NOT retry on 500', async () => {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      return { ok: false, status: 500, text: async () => 'Internal Server Error' };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    await assert.rejects(async () => client.get('system/health'), /500/);
    assert.equal(calls, 1, 'should NOT retry on 500');
  });

  it('retries on network error then succeeds', async () => {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      if (calls === 1) {
        const err = new TypeError('fetch failed: ENOTFOUND');
        throw err;
      }
      return {
        ok: true, status: 200,
        json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } })
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'test' });
    const result = await client.get('system/health');
    assert.equal(calls, 2, 'should retry on network error');
    assert.equal(result.conclusion, 'OK');
  });

  it('passes Authorization header with Bearer token', async () => {
    let capturedHeaders = null;
    globalThis.fetch = async (url, opts) => {
      capturedHeaders = opts.headers;
      return {
        ok: true, status: 200,
        json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } })
      };
    };

    const { QtaClient } = await import('../dist/client/qta-client.js');
    const client = new QtaClient({ baseUrl: 'http://localhost:9999', token: 'my-secret-token' });
    await client.get('system/health');
    assert.equal(capturedHeaders['Authorization'], 'Bearer my-secret-token');
  });
});

describe('isRetryableError', () => {
  it('returns true for 502', async () => {
    const { isRetryableError } = await import('../dist/client/qta-client.js');
    assert.equal(isRetryableError(502, undefined), true);
  });

  it('returns true for 503', async () => {
    const { isRetryableError } = await import('../dist/client/qta-client.js');
    assert.equal(isRetryableError(503, undefined), true);
  });

  it('returns false for 401', async () => {
    const { isRetryableError } = await import('../dist/client/qta-client.js');
    assert.equal(isRetryableError(401, undefined), false);
  });

  it('returns false for 500', async () => {
    const { isRetryableError } = await import('../dist/client/qta-client.js');
    assert.equal(isRetryableError(500, undefined), false);
  });

  it('returns true for AbortError (timeout)', async () => {
    const { isRetryableError } = await import('../dist/client/qta-client.js');
    assert.equal(isRetryableError(undefined, new Error('timed out')), false); // base Error, not AbortError
    const abortErr = new Error('timed out');
    abortErr.name = 'AbortError';
    assert.equal(isRetryableError(undefined, abortErr), true);
  });
});
