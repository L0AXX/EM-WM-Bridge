package com.emwbridge.ai;

/**
 * AI 行为模式（GreyZone 需求3 据点守卫）。
 * - FREE：默认，自由游荡/巡逻（现有玩家交战路径不受影响）。
 * - GUARD：据点死守——驻守 home，敌入 aggroRadius 即猎杀，超出 leashDistance 回防，禁用撤退。
 */
public enum AiBehavior {
    FREE,
    GUARD
}
