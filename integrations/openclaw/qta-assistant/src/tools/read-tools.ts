/**
 * QTA 只读工具定义。
 * 所有 URL 查询参数通过 QtaClient.get(path, queryParams) 使用 URLSearchParams 编码。
 */
import { Type } from 'typebox';
import type { QtaClient } from '../client/qta-client.js';
import type { TrustedAnswer } from '../client/qta-client.js';
import { trimResults, formatConclusion } from '../formatter/result-formatter.js';

export interface ToolDef {
  name: string;
  label: string;
  description: string;
  paramsSchema: unknown;
  execute: (client: QtaClient, params: Record<string, unknown>, maxResults: number, signal?: AbortSignal) => Promise<TrustedAnswer>;
}

const PaginationParams = {
  limit: Type.Optional(Type.Number({ description: '最大返回条数', minimum: 1, maximum: 50, default: 10 })),
};

export const readTools: ToolDef[] = [
  {
    name: 'qta_system_health',
    label: '系统健康',
    description: '查询 QTA 系统健康状态、Provider 可达性和数据计数',
    paramsSchema: Type.Object({}),
    async execute(client, _params, _maxResults, signal) {
      return client.get('system/health', undefined, signal);
    },
  },
  {
    name: 'qta_today_overview',
    label: '今日待办',
    description: '查询今日工作台统计、风险提醒和待办列表',
    paramsSchema: Type.Object({
      date: Type.Optional(Type.String({ description: '日期 YYYY-MM-DD' })),
    }),
    async execute(client, params, _maxResults, signal) {
      return client.get('trading/today', { date: params.date }, signal);
    },
  },
  {
    name: 'qta_portfolio_summary',
    label: '持仓摘要',
    description: '查询持仓汇总（已实现/未实现盈亏、持仓数量）。不构成投资建议。',
    paramsSchema: Type.Object({}),
    async execute(client, _params, _maxResults, signal) {
      return client.get('portfolio/summary', undefined, signal);
    },
  },
  {
    name: 'qta_collection_overview',
    label: '行情采集概览',
    description: '查询行情采集计划、任务和水位状态',
    paramsSchema: Type.Object({
      market: Type.Optional(Type.String({ description: '市场 CN/HK/US' })),
      date: Type.Optional(Type.String({ description: '日期 YYYY-MM-DD' })),
    }),
    async execute(client, params, _maxResults, signal) {
      return client.get('market-data/collection-overview', { market: params.market, date: params.date }, signal);
    },
  },
  {
    name: 'qta_collection_failures',
    label: '采集失败',
    description: '查询最近失败的采集任务及原因',
    paramsSchema: Type.Object({
      market: Type.Optional(Type.String()),
      since: Type.Optional(Type.String()),
      ...PaginationParams,
    }),
    async execute(client, params, maxResults, signal) {
      const limit = Math.min(params.limit as number ?? maxResults, 50);
      return client.get('market-data/failures', { market: params.market, since: params.since, limit }, signal);
    },
  },
  {
    name: 'qta_data_quality_alerts',
    label: '数据质量提醒',
    description: '查询数据质量异常提醒',
    paramsSchema: Type.Object({
      status: Type.Optional(Type.String({ description: 'resolved/unresolved' })),
      since: Type.Optional(Type.String()),
      ...PaginationParams,
    }),
    async execute(client, params, maxResults, signal) {
      const limit = Math.min(params.limit as number ?? maxResults, 50);
      return client.get('market-data/alerts', { status: params.status, since: params.since, limit }, signal);
    },
  },
  {
    name: 'qta_sector_ranking_summary',
    label: '板块排行摘要',
    description: '查询最新领涨领跌板块排行',
    paramsSchema: Type.Object({
      market: Type.Optional(Type.String({ default: 'CN' })),
      ...PaginationParams,
    }),
    async execute(client, params, maxResults, signal) {
      const market = params.market as string ?? 'CN';
      const limit = Math.min(params.limit as number ?? maxResults, 50);
      return client.get('market-sectors/ranking-summary', { market, limit }, signal);
    },
  },
  {
    name: 'qta_security_market_summary',
    label: '单证券行情摘要',
    description: '查询指定证券的最新价、来源和数据时间',
    paramsSchema: Type.Object({
      canonicalSymbol: Type.String({ description: '证券代码 SH.600519' }),
    }),
    async execute(client, params, _maxResults, signal) {
      // canonicalSymbol goes in path, encoded by QtaClient.buildUrl via encodeURIComponent
      return client.get(`securities/${params.canonicalSymbol}/market-summary`, undefined, signal);
    },
  },
];
