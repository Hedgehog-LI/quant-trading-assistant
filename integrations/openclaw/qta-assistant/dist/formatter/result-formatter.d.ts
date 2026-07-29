/**
 * 结果裁剪和格式化 — 默认最多 10 条，最大 50 条。
 */
import type { TrustedAnswer } from '../client/qta-client.js';
export declare function trimResults(answer: TrustedAnswer, maxItems?: number): TrustedAnswer;
export declare function formatConclusion(answer: TrustedAnswer): string;
