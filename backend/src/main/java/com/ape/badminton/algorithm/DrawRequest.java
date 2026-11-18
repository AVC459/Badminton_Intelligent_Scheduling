package com.ape.badminton.algorithm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽签请求参数
 */
@Data
public class DrawRequest {

    /** 参与抽签的选手 */
    private List<PlayerView> players = new ArrayList<>();

    /** 是否启用同单位规避 */
    private boolean avoidSameTeam = true;

    /** 随机种子；固定后抽签结果可复现，便于审计与测试 */
    private Long randomSeed;

    /**
     * 引擎内部使用的选手视图（与数据库实体解耦，便于单元测试）
     */
    @Data
    public static class PlayerView {
        private String playerId;
        private String name;
        /** 所属单位，同单位规避依据 */
        private String team;
        /** 水平分，用于公平性度量 */
        private int level;
        /** 是否种子选手 */
        private boolean seed;
        /** 种子顺位，1 为最高 */
        private int seedRank;

        public PlayerView() {
        }

        public PlayerView(String playerId, String name, String team, int level, boolean seed, int seedRank) {
            this.playerId = playerId;
            this.name = name;
            this.team = team;
            this.level = level;
            this.seed = seed;
            this.seedRank = seedRank;
        }
    }
}
