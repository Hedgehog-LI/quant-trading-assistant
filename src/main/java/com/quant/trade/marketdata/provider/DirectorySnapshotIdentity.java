package com.quant.trade.marketdata.provider;

/** 目录快照内容身份：snapshotId 由 snapshotHash 派生，不引入读次数/时间戳/路径，保证幂等以内容为准。 */
public record DirectorySnapshotIdentity(String snapshotId, String snapshotHash, String sourceDescription) {
}
