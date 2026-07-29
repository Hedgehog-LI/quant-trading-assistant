/**
 * QTA OpenClaw 远程只读助手 — 原生 Tool Plugin 入口
 *
 * Uses defineToolPlugin with factory mode.
 * Factory receives ToolPluginFactoryContext which contains toolContext.requesterSenderId.
 * Returns AnyAgentTool[] with official execute(toolCallId, params, signal, onUpdate) signature.
 * Returns AgentToolResult via jsonResult().
 */
import { Type, type TSchema, type Static } from 'typebox';
import { defineToolPlugin } from 'openclaw/plugin-sdk/tool-plugin';
import { jsonResult, type AnyAgentTool } from 'openclaw/plugin-sdk/core';
import { QtaClient, type TrustedAnswer } from './client/qta-client.js';
import { readTools } from './tools/read-tools.js';
import { trimResults, formatConclusion } from './formatter/result-formatter.js';
import { isSenderAllowed } from './policy/sender-policy.js';

// Plugin config type derived from schema
interface QtaPluginConfig {
  baseUrl: string;
  token: string;
  allowedOpenIds?: string[];
  connectTimeoutMs?: number;
  totalTimeoutMs?: number;
  maxResults?: number;
}

export default defineToolPlugin({
  id: 'qta-assistant',
  name: 'QTA Read-Only Assistant',
  description: 'Quant Trading Assistant 远程只读工具集 — 系统健康、今日待办、持仓摘要、行情采集、失败任务、数据质量、板块排行、单证券摘要。禁止写操作和自动交易。',
  configSchema: Type.Object({
    baseUrl: Type.String({ description: 'QTA API base URL, e.g. http://127.0.0.1:8080' }),
    token: Type.String({ description: 'Agent Bearer Token (from env, never in manifest)' }),
    allowedOpenIds: Type.Optional(Type.Array(Type.String(), { description: 'QQ OpenID allowlist; empty = deny all' })),
    connectTimeoutMs: Type.Optional(Type.Number({ description: 'Connect timeout ms, default 2000', default: 2000 })),
    totalTimeoutMs: Type.Optional(Type.Number({ description: 'Total timeout ms, default 10000', default: 10000 })),
    maxResults: Type.Optional(Type.Number({ description: 'Default max results, default 10', default: 10 })),
  }),
  tools: (tool) => {
    // Declare static metadata for all 8 tools
    return readTools.map((def) => {
      return tool({
        name: def.name,
        label: def.label,
        description: def.description,
        parameters: def.paramsSchema as TSchema,
        // Use factory to build AnyAgentTool at runtime with sender auth
        factory: (factoryContext): AnyAgentTool => {
          const config = factoryContext.config as QtaPluginConfig;
          const toolCtx = factoryContext.toolContext;

          return {
            name: def.name,
            label: def.label,
            description: def.description,
            parameters: def.paramsSchema as TSchema,
            // Official execute signature: (toolCallId, params, signal, onUpdate) => Promise<AgentToolResult>
            execute: async (toolCallId: string, params: unknown, signal?: AbortSignal, onUpdate?: (partialResult: { content: { type: "text"; text: string }[]; details: unknown }) => void) => {
              if (signal?.aborted) throw new Error('Aborted');

              // Sender auth — always fail-closed:
              // allowedOpenIds missing/empty/undefined → reject all
              // requesterSenderId missing → reject
              // requesterSenderId not in allowlist → reject
              const allowedOpenIds = config.allowedOpenIds;
              if (!allowedOpenIds || !Array.isArray(allowedOpenIds) || allowedOpenIds.length === 0) {
                throw new Error('Allowlist not configured or empty; all senders rejected');
              }
              const senderId = toolCtx?.requesterSenderId;
              if (!senderId) {
                throw new Error('Sender identity unavailable (requesterSenderId missing)');
              }
              if (!isSenderAllowed(senderId, { allowedOpenIds })) {
                throw new Error('Sender not in allowlist: ' + senderId);
              }

              // Build QTA client and execute
              const client = new QtaClient({
                baseUrl: config.baseUrl,
                token: config.token,
                connectTimeoutMs: config.connectTimeoutMs,
                totalTimeoutMs: config.totalTimeoutMs,
              });

              const maxResults = config.maxResults ?? 10;
              const typedParams = (params ?? {}) as Record<string, unknown>;
              const answer = await def.execute(client, typedParams, maxResults, signal) as TrustedAnswer;

              // Return AgentToolResult via jsonResult
              return jsonResult({
                conclusion: formatConclusion(answer),
                freshnessStatus: answer.freshnessStatus,
                dataAsOf: answer.dataAsOf,
                data: trimResults(answer, maxResults).data,
              });
            },
          };
        },
      });
    });
  },
});
