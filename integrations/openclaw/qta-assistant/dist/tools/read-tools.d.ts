import type { QtaClient } from '../client/qta-client.js';
import type { TrustedAnswer } from '../client/qta-client.js';
export interface ToolDef {
    name: string;
    label: string;
    description: string;
    paramsSchema: unknown;
    execute: (client: QtaClient, params: Record<string, unknown>, maxResults: number, signal?: AbortSignal) => Promise<TrustedAnswer>;
}
export declare const readTools: ToolDef[];
