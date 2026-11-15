package com.manga.agent.collaboration;

/** 修订确认接口接受的用户命令，不代表批准业务变更。 */
public enum RepairAction {
    /** 授权下一轮修订及评审。 */ CONFIRM_REPAIR,
    /** 结束本次任务并保留候选。 */ STOP
}
