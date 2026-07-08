package com.emwbridge.ai.squad;

import com.emwbridge.EMWMBridge;
import com.emwbridge.ai.personality.PersonalityType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 需求2 角色编制小队：命名编制（squadName）直接编队、max-size 覆盖、roles 配额定角色、captain 修复。
 * 使用 Mockito 模拟 LivingEntity（命名编制路径不需 world/location）。
 */
class SquadManagerTest {

    private SquadManager sm;

    @BeforeEach
    void setUp() {
        sm = new SquadManager(mock(EMWMBridge.class));
        sm.reload(new YamlConfiguration());
    }

    private LivingEntity mob() {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        return e;
    }

    @Test
    @DisplayName("需求2.2：同名 squadName 的实体加入同一编制")
    void namedSquadJoinsSameSquad() {
        LivingEntity a = mob();
        LivingEntity b = mob();
        sm.tryJoin(a, "pmc", PersonalityType.RECKLESS, "alpha");
        sm.tryJoin(b, "pmc", PersonalityType.CAUTIOUS, "alpha");

        List<UUID> squadA = sm.getSquad(a.getUniqueId());
        List<UUID> squadB = sm.getSquad(b.getUniqueId());
        assertEquals(2, squadA.size(), "同编制应有 2 人");
        assertEquals(2, squadB.size());
        assertTrue(squadA.contains(b.getUniqueId()), "a 的编制应包含 b");
        assertTrue(squadB.contains(a.getUniqueId()), "b 的编制应包含 a");
    }

    @Test
    @DisplayName("需求2.1：命名编制 max-size 覆盖，满员后拒绝加入")
    void namedSquadMaxSizeOverride() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("squad.max-size", 8);
        ConfigurationSection squads = cfg.createSection("squad.squads");
        ConfigurationSection alpha = squads.createSection("alpha");
        alpha.set("max-size", 1);
        sm.reload(cfg);

        LivingEntity a = mob();
        LivingEntity b = mob();
        sm.tryJoin(a, "pmc", PersonalityType.RECKLESS, "alpha");
        sm.tryJoin(b, "pmc", PersonalityType.CAUTIOUS, "alpha");

        // a 占满编制（max-size=1），b 应未被编入
        assertEquals(1, sm.getSquad(a.getUniqueId()).size(), "a 的编制满员");
        assertEquals(1, sm.getSquad(b.getUniqueId()).size(), "b 未被编入（仅自身）");
        assertNotEquals(sm.getSquad(a.getUniqueId()), sm.getSquad(b.getUniqueId()));
    }

    @Test
    @DisplayName("需求2.3：roles 配额定角色（ASSAULT:1 + SNIPER:1）")
    void roleQuotaAssigned() {
        YamlConfiguration cfg = new YamlConfiguration();
        ConfigurationSection squads = cfg.createSection("squad.squads");
        ConfigurationSection ft = squads.createSection("fireteam");
        ConfigurationSection roles = ft.createSection("roles");
        roles.set("ASSAULT", 1);
        roles.set("SNIPER", 1);
        sm.reload(cfg);

        LivingEntity a = mob();
        LivingEntity b = mob();
        sm.tryJoin(a, "pmc", PersonalityType.RECKLESS, "fireteam");
        sm.tryJoin(b, "pmc", PersonalityType.CAUTIOUS, "fireteam");

        SquadRole ra = sm.getRole(a.getUniqueId());
        SquadRole rb = sm.getRole(b.getUniqueId());
        assertNotNull(ra);
        assertNotNull(rb);
        // 配额 ASSAULT:1 + SNIPER:1 → 两人角色应互不相同且覆盖配额
        assertNotEquals(ra, rb, "两角色应来自配额且互不相同");
        assertTrue((ra == SquadRole.ASSAULT && rb == SquadRole.SNIPER)
                || (ra == SquadRole.SNIPER && rb == SquadRole.ASSAULT), "应分配 ASSAULT+SNIPER");
    }

    @Test
    @DisplayName("需求2.3 修复：CAPTAIN 性格实体成为编制队长")
    void captainAssigned() {
        LivingEntity cap = mob();
        sm.tryJoin(cap, "pmc", PersonalityType.CAPTAIN, "cap_squad");
        assertTrue(sm.isCaptain(cap.getUniqueId()), "CAPTAIN 应为队长");
    }

    @Test
    @DisplayName("需求2.1：getNamedSquadMaxSize 满员覆盖回退全局")
    void namedMaxSizeFallsBackToGlobal() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("squad.max-size", 3);
        sm.reload(cfg);
        // 未定义的命名编制应回退全局 max-size=3
        assertEquals(3, sm.getNamedSquadMaxSize("undefined_squad"));
    }
}
