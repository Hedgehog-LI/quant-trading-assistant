export function trimResults(answer, maxItems = 10) {
    if (!answer.data || typeof answer.data !== 'object')
        return answer;
    const data = answer.data;
    const trimmed = {};
    for (const [key, value] of Object.entries(data)) {
        if (Array.isArray(value) && value.length > maxItems) {
            trimmed[key] = value.slice(0, maxItems);
            trimmed[`${key}Total`] = value.length;
            trimmed[`${key}Truncated`] = true;
        }
        else {
            trimmed[key] = value;
        }
    }
    return { ...answer, data: trimmed };
}
export function formatConclusion(answer) {
    const lines = [answer.conclusion];
    if (answer.dataAsOf) {
        lines.push(`数据时间: ${answer.dataAsOf}`);
    }
    lines.push(`新鲜度: ${answer.freshnessStatus}`);
    if (answer.warnings && answer.warnings.length > 0) {
        lines.push('⚠️ ' + answer.warnings.join('; '));
    }
    return lines.join('\n');
}
