/**
 * 结果裁剪和格式化 — 默认最多 10 条，最大 50 条。
 */
import type { TrustedAnswer } from '../client/qta-client.js';

export function trimResults(answer: TrustedAnswer, maxItems: number = 10): TrustedAnswer {
  if (!answer.data || typeof answer.data !== 'object') return answer;
  const data = answer.data as Record<string, unknown>;
  const trimmed: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(data)) {
    if (Array.isArray(value) && value.length > maxItems) {
      trimmed[key] = value.slice(0, maxItems);
      trimmed[`${key}Total`] = value.length;
      trimmed[`${key}Truncated`] = true;
    } else {
      trimmed[key] = value;
    }
  }

  return { ...answer, data: trimmed };
}

export function formatConclusion(answer: TrustedAnswer): string {
  const lines: string[] = [answer.conclusion];

  if (answer.dataAsOf) {
    lines.push(`数据时间: ${answer.dataAsOf}`);
  }
  lines.push(`新鲜度: ${answer.freshnessStatus}`);

  if (answer.warnings && answer.warnings.length > 0) {
    lines.push('⚠️ ' + answer.warnings.join('; '));
  }

  return lines.join('\n');
}
