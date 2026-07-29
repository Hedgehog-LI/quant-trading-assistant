/**
 * Real tool.execute tests: register plugin → factory → AnyAgentTool.execute → assert.
 *
 * NO fallback branches. If we cannot get real tools, tests MUST fail.
 * Uses official register() → registerTool(toolContext) → AnyAgentTool.execute chain.
 */
import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';

/**
 * Get real AnyAgentTool objects by simulating OpenClaw's register() call.
 * The plugin entry has a `register(api)` function that calls `api.registerTool(factory, opts)`.
 * We mock `api` to capture the factory functions, then invoke them with toolContext.
 */
async function getRealTools(configOverrides = {}) {
  const mod = await import('../dist/index.js');
  const entry = mod.default;

  assert.ok(entry.register, 'Plugin entry must have register()');

  const config = {
    baseUrl: 'http://127.0.0.1:9999',
    token: 'test-token-0123456789abcdef',
    ...configOverrides,
  };

  // Capture registered tool factories
  const registeredFactories = [];

  const mockApi = {
    pluginConfig: config,
    registerTool(factoryOrTool, opts) {
      // If it's a factory function (from factory mode), call it with toolContext
      if (typeof factoryOrTool === 'function') {
        registeredFactories.push({ factory: factoryOrTool, opts });
      } else {
        // Direct tool object
        registeredFactories.push({ tool: factoryOrTool, opts });
      }
    },
  };

  // Invoke register — this calls registerTool for each tool
  entry.register(mockApi);

  assert.ok(registeredFactories.length === 8, `Must register exactly 8 tools, got ${registeredFactories.length}`);

  // Invoke each factory with toolContext to get real AnyAgentTool
  const realTools = registeredFactories.map((reg, i) => {
    let tool;
    if (reg.factory) {
      tool = reg.factory({ requesterSenderId: undefined }); // default: no sender
    } else {
      tool = reg.tool;
    }
    assert.ok(tool, `Tool ${i} factory must return a tool object`);
    assert.equal(typeof tool.execute, 'function', `Tool ${i} must have execute function`);
    return tool;
  });

  return { realTools, config, registeredFactories };
}

/**
 * Get real tools with a specific toolContext (e.g. with requesterSenderId set)
 */
async function getRealToolsWithContext(toolContext, configOverrides = {}) {
  const mod = await import('../dist/index.js');
  const entry = mod.default;

  const config = {
    baseUrl: 'http://127.0.0.1:9999',
    token: 'test-token-0123456789abcdef',
    ...configOverrides,
  };

  const registeredFactories = [];
  const mockApi = {
    pluginConfig: config,
    registerTool(factoryOrTool, opts) {
      registeredFactories.push({ factory: factoryOrTool, opts });
    },
  };

  entry.register(mockApi);

  const realTools = registeredFactories.map(reg => {
    if (reg.factory) {
      return reg.factory(toolContext);
    }
    return reg.tool;
  });

  return { realTools, config };
}

describe('Real tool.execute — sender fail-closed (NO FALLBACK)', () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it('throws when sender missing and allowlist configured (fetch count = 0)', async () => {
    let fetchCount = 0;
    globalThis.fetch = async () => { fetchCount++; return { ok: true, status: 200, json: async () => ({ success: true, data: {} }) }; };

    const { realTools } = await getRealTools({ allowedOpenIds: ['authorized_openid'] });
    const tool = realTools[0]; // qta_system_health

    await assert.rejects(
      async () => tool.execute('call-1', {}, new AbortController().signal),
      /Sender not in allowlist|sender identity unavailable/i,
    );
    assert.equal(fetchCount, 0, 'fetch must NOT be called when sender is rejected');
  });

  it('throws when sender is impostor (fetch count = 0)', async () => {
    let fetchCount = 0;
    globalThis.fetch = async () => { fetchCount++; return { ok: true, status: 200, json: async () => ({ success: true, data: {} }) }; };

    const { realTools } = await getRealToolsWithContext(
      { requesterSenderId: 'impostor' },
      { allowedOpenIds: ['authorized_openid'] },
    );
    const tool = realTools[0];

    await assert.rejects(
      async () => tool.execute('call-2', {}, new AbortController().signal),
      /Sender not in allowlist/i,
    );
    assert.equal(fetchCount, 0, 'fetch must NOT be called for impostor');
  });

  it('passes when sender is authorized (fetch called, AgentToolResult returned)', async () => {
    let fetchCount = 0;
    globalThis.fetch = async () => {
      fetchCount++;
      return {
        ok: true, status: 200,
        json: async () => ({
          success: true,
          data: {
            conclusion: '系统正常',
            generatedAt: '2026-01-01T00:00:00Z',
            dataAsOf: '2026-01-01T00:00:00+08:00',
            freshnessStatus: 'FRESH',
            evidence: [],
            warnings: [],
            data: { providerReachable: true },
          }
        })
      };
    };

    const { realTools } = await getRealToolsWithContext(
      { requesterSenderId: 'authorized_openid' },
      { allowedOpenIds: ['authorized_openid'] },
    );
    const tool = realTools[0];

    const result = await tool.execute('call-3', {}, new AbortController().signal);

    assert.ok(fetchCount > 0, 'fetch must be called for authorized sender');
    assert.ok(result.content, 'must return AgentToolResult with content');
    assert.ok(Array.isArray(result.content), 'content must be an array');
    assert.ok(result.content.length > 0, 'content must have items');
    assert.ok(result.details, 'must have details');
  });

  it('rejects all when allowlist is empty (fetch=0)', async () => {
    let fetchCount = 0;
    globalThis.fetch = async () => { fetchCount++; return { ok: true, status: 200, json: async () => ({ success: true, data: {} }) }; };

    const { realTools } = await getRealTools({ allowedOpenIds: [] });
    await assert.rejects(
      async () => realTools[0].execute('call-empty', {}, new AbortController().signal),
      /Allowlist not configured or empty/i,
    );
    assert.equal(fetchCount, 0, 'fetch must NOT be called when allowlist is empty');
  });

  it('rejects all when allowlist is undefined (fetch=0)', async () => {
    let fetchCount = 0;
    globalThis.fetch = async () => { fetchCount++; return { ok: true, status: 200, json: async () => ({ success: true, data: {} }) }; };

    const { realTools } = await getRealTools({});
    await assert.rejects(
      async () => realTools[0].execute('call-undefined', {}, new AbortController().signal),
      /Allowlist not configured or empty/i,
    );
    assert.equal(fetchCount, 0, 'fetch must NOT be called when allowlist is undefined');
  });
});

describe('Eight tools real registration chain (all must succeed)', () => {
  beforeEach(() => {
    globalThis.fetch = async () => ({
      ok: true, status: 200,
      json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } })
    });
  });

  it('all 8 tools return AgentToolResult with content array', async () => {
    const { realTools } = await getRealToolsWithContext(
      { requesterSenderId: 'test-openid' },
      { allowedOpenIds: ['test-openid'] },
    );
    assert.equal(realTools.length, 8, 'Must have exactly 8 tools');

    for (const tool of realTools) {
      const result = await tool.execute('smoke-test', {}, new AbortController().signal);
      assert.ok(result, `${tool.name} must return a result`);
      assert.ok(Array.isArray(result.content), `${tool.name} content must be array`);
      assert.ok(result.content.length > 0, `${tool.name} must have content items`);
      assert.ok(result.details !== undefined, `${tool.name} must have details`);
    }
  });

  it('qta_security_market_summary passes SH.600519 correctly in URL (no undefined)', async () => {
    let capturedUrl = '';
    globalThis.fetch = async (url) => {
      capturedUrl = url;
      return { ok: true, status: 200, json: async () => ({ success: true, data: { conclusion: 'OK', generatedAt: '', dataAsOf: null, freshnessStatus: 'FRESH', evidence: [], warnings: [], data: {} } }) };
    };

    const { realTools } = await getRealToolsWithContext(
      { requesterSenderId: 'test-openid' },
      { allowedOpenIds: ['test-openid'] },
    );
    const securityTool = realTools.find(t => t.name === 'qta_security_market_summary');
    assert.ok(securityTool, 'must have qta_security_market_summary tool');

    await securityTool.execute('url-test', { canonicalSymbol: 'SH.600519' }, new AbortController().signal);
    assert.ok(capturedUrl.includes('SH.600519'), `URL must contain SH.600519, got: ${capturedUrl}`);
    assert.ok(!capturedUrl.includes('undefined'), 'URL must not contain undefined');
    assert.ok(!capturedUrl.includes('%20'), 'URL must not contain unencoded spaces');
  });
});
