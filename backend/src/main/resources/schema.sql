-- ============================================================================
--  羽毛球智能抽签编排系统 · 数据库初始化脚本（v3.0）
--  项目编号：S202510722098
--
--  设计要点：
--    1. 全系统只有一种身份：比赛发起者
--       —— 注册账号后即可创建赛事、维护自己的选手库，无需任何鉴权分级。
--    2. 选手由发起者从自己的选手库直接选入赛事。
--    3. 选手库、赛事、场地均归属发起者（creator_id），实现完全的数据隔离：
--       「我的选手库 / 我创建的赛事 / 我的场地」，新账号首次进入各项均为 0。
--    4. 参赛名单统一落在 biz_local_entries，作为抽签池的唯一数据来源。
--
--  说明：本脚本会 **删除并重建** 业务表（DROP TABLE IF EXISTS），
--        仅用于开发/演示环境的可重复初始化。生产环境请使用增量迁移脚本。
--
--  执行方式（任选其一）：
--    1) MySQL 命令行：mysql -uroot -p < schema.sql
--    2) Navicat 中直接运行本文件
--
--  字符集：utf8mb4（支持 emoji 与生僻字）
-- ============================================================================

CREATE DATABASE IF NOT EXISTS badminton DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE badminton;

-- 按外键依赖倒序删除
DROP TABLE IF EXISTS biz_local_draw_logs;
DROP TABLE IF EXISTS biz_local_matches;
DROP TABLE IF EXISTS biz_local_groups;
DROP TABLE IF EXISTS biz_local_entries;

DROP TABLE IF EXISTS biz_local_tournaments;
DROP TABLE IF EXISTS biz_local_players;
DROP TABLE IF EXISTS sys_local_users;

-- ---------------------------------------------------------------------------
-- 1. 用户表：比赛发起者（唯一身份，无角色区分）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_local_users (
    user_id     VARCHAR(50)  PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT '密码（演示环境明文，生产需 BCrypt）',
    real_name   VARCHAR(50)  NULL COMMENT '姓名',
    phone       VARCHAR(20)  NULL COMMENT '手机号',
    create_time DATETIME     NOT NULL COMMENT '注册时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='比赛发起者账号';

-- ---------------------------------------------------------------------------
-- 2. 选手库（归属发起者，可编辑；抽签取此表中的档案）
-- ---------------------------------------------------------------------------
CREATE TABLE biz_local_players (
    player_id   VARCHAR(50)  PRIMARY KEY COMMENT '选手ID',
    creator_id  VARCHAR(50)  NOT NULL COMMENT '所属发起者（选手库归属）',
    category    VARCHAR(20)  NOT NULL DEFAULT 'MS' COMMENT '项目类型分支：MS男单/WS女单/MD男双/WD女双/XD混双',
    student_id  VARCHAR(50)  NOT NULL COMMENT '学号/工号（双打为主选手学号）',
    name        VARCHAR(50)  NOT NULL COMMENT '姓名（双打为「主选手/搭档」组合名）',
    gender      VARCHAR(10)  NOT NULL DEFAULT 'M' COMMENT 'M 男 / F 女（双打为该组合主选手性别）',
    team        VARCHAR(100) NULL COMMENT '所属单位/学院（同单位规避依据）',
    phone       VARCHAR(20)  NULL COMMENT '联系方式',
    level       INT          NOT NULL DEFAULT 0 COMMENT '水平分/积分，用于公平性度量',
    is_seed     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否种子选手',
    seed_rank   INT          NOT NULL DEFAULT 0 COMMENT '种子顺位，1 为最高',
    partner_name       VARCHAR(50) NULL COMMENT '双打搭档姓名（单打为空）',
    partner_student_id VARCHAR(50) NULL COMMENT '双打搭档学号（单打为空）',
    partner_team       VARCHAR(100) NULL COMMENT '双打搭档单位（混双/跨院组队时与 team 不同，单打为空）',
    create_time DATETIME     NOT NULL COMMENT '录入时间',
    KEY idx_creator_category (creator_id, category),
    KEY idx_team (team),
    KEY idx_seed (is_seed, seed_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选手库（按项目类型分 5 个分支）';

-- ---------------------------------------------------------------------------
-- 3. 赛事表（creator_id 标识发起者）
-- ---------------------------------------------------------------------------
CREATE TABLE biz_local_tournaments (
    tournament_id       VARCHAR(50)  PRIMARY KEY COMMENT '赛事ID',
    creator_id          VARCHAR(50)  NOT NULL COMMENT '发起者ID',
    title               VARCHAR(120) NOT NULL COMMENT '赛事名称',
    event_type          VARCHAR(20)  NOT NULL DEFAULT 'MS' COMMENT 'MS男单/WS女单/MD男双/WD女双/XD混双',
    format              VARCHAR(30)  NOT NULL DEFAULT 'SINGLE_ELIM' COMMENT 'SINGLE_ELIM淘汰/ROUND_ROBIN循环/GROUP_KNOCKOUT小组+淘汰',
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/READY待编排/DRAWN已编排/ONGOING进行中/FINISHED已结束',
    actual_participants INT          NOT NULL DEFAULT 0 COMMENT '实际参赛人数',
    bracket_size        INT          NOT NULL DEFAULT 0 COMMENT '签位规模 2^n',
    bye_count           INT          NOT NULL DEFAULT 0 COMMENT '轮空数',
    seed_count          INT          NOT NULL DEFAULT 4 COMMENT '种子数量',
    group_count         INT          NOT NULL DEFAULT 0 COMMENT '小组数（小组赛制）',
    group_advance       INT          NOT NULL DEFAULT 2 COMMENT '每组出线人数',
    best_of             INT          NOT NULL DEFAULT 3 COMMENT '几局几胜（3=三局两胜）',
    points_per_game     INT          NOT NULL DEFAULT 21 COMMENT '每局分数',
    points_cap          INT          NOT NULL DEFAULT 30 COMMENT '封顶分',
    avoid_same_team     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用同单位规避',
    champion_id         VARCHAR(50)  NULL COMMENT '冠军',
    runner_up_id        VARCHAR(50)  NULL COMMENT '亚军',
    third_id            VARCHAR(50)  NULL COMMENT '季军',
    remark              VARCHAR(255) NULL COMMENT '备注',
    create_time         DATETIME     NOT NULL COMMENT '创建时间',
    KEY idx_creator (creator_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事';

-- ---------------------------------------------------------------------------
-- 4. 参赛名单（替代原报名表：由发起者从选手库直接选入，无审核状态）
-- ---------------------------------------------------------------------------
CREATE TABLE biz_local_entries (
    entry_id      VARCHAR(50) PRIMARY KEY COMMENT '参赛记录ID',
    tournament_id VARCHAR(50) NOT NULL COMMENT '赛事ID',
    player_id     VARCHAR(50) NOT NULL COMMENT '选手ID',
    seed_flag     TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '本赛事中是否作为种子',
    seed_rank     INT         NOT NULL DEFAULT 0 COMMENT '本赛事种子顺位',
    create_time   DATETIME    NOT NULL COMMENT '加入时间',
    UNIQUE KEY uk_tournament_player (tournament_id, player_id),
    KEY idx_tournament (tournament_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参赛名单';

-- ---------------------------------------------------------------------------
-- 5. 小组表（小组赛制使用）
-- ---------------------------------------------------------------------------
CREATE TABLE biz_local_groups (
    group_id      VARCHAR(50) PRIMARY KEY COMMENT '小组记录ID',
    tournament_id VARCHAR(50) NOT NULL COMMENT '赛事ID',
    group_no      INT         NOT NULL COMMENT '组序号，从 1 开始',
    player_id     VARCHAR(50) NOT NULL COMMENT '选手ID',
    seed_position INT         NOT NULL DEFAULT 0 COMMENT '组内位置（蛇形落位）',
    KEY idx_tournament_group (tournament_id, group_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小组分组';

-- ---------------------------------------------------------------------------
-- 6. 对阵/场次表
-- ---------------------------------------------------------------------------
CREATE TABLE biz_local_matches (
    match_id        VARCHAR(60) PRIMARY KEY COMMENT '场次ID',
    tournament_id   VARCHAR(50) NOT NULL COMMENT '赛事ID',
    stage           VARCHAR(20) NOT NULL DEFAULT 'KNOCKOUT' COMMENT 'GROUP小组/KNOCKOUT淘汰/ROUND_ROBIN循环/THIRD_PLACE季军赛',
    round           INT         NOT NULL DEFAULT 1 COMMENT '轮次，从 1 开始',
    round_name      VARCHAR(20) NULL COMMENT '轮次名称：1/4决赛等',
    group_no        INT         NOT NULL DEFAULT 0 COMMENT '所属小组，0 表示非小组赛',
    match_order     INT         NOT NULL DEFAULT 1 COMMENT '轮内场次序号',
    player_a_id     VARCHAR(50) NULL COMMENT 'A 方选手，BYE 表示轮空',
    player_b_id     VARCHAR(50) NULL COMMENT 'B 方选手，BYE 表示轮空',
    winner_id       VARCHAR(50) NULL COMMENT '胜方',
    loser_id        VARCHAR(50) NULL COMMENT '负方（用于季军赛/附加赛）',
    games_json      VARCHAR(500) NULL COMMENT '各局比分 JSON：[{"a":21,"b":18}]',
    score_a         INT         NOT NULL DEFAULT 0 COMMENT 'A 方总得分',
    score_b         INT         NOT NULL DEFAULT 0 COMMENT 'B 方总得分',
    games_a         INT         NOT NULL DEFAULT 0 COMMENT 'A 方胜局数',
    games_b         INT         NOT NULL DEFAULT 0 COMMENT 'B 方胜局数',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待就绪/READY可开赛/DONE已完成/BYE轮空/WALKOVER弃权',
    next_match_id   VARCHAR(60) NULL COMMENT '胜方晋级目标场次',
    next_slot       VARCHAR(2)  NULL COMMENT '晋级到目标场次的位置：A/B',
    loser_next_id   VARCHAR(60) NULL COMMENT '负方去往场次（季军赛）',
    loser_slot      VARCHAR(2)  NULL COMMENT '负方去往位置：A/B',
    remark          VARCHAR(255) NULL COMMENT '备注',
    KEY idx_tournament (tournament_id, round, stage),
    KEY idx_next (next_match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对阵与场次';

-- ---------------------------------------------------------------------------
-- 8. 抽签审计日志（透明性保障：完整记录抽签每一步）
-- ---------------------------------------------------------------------------
CREATE TABLE biz_local_draw_logs (
    log_id        VARCHAR(60) PRIMARY KEY COMMENT '日志ID',
    tournament_id VARCHAR(50) NOT NULL COMMENT '赛事ID',
    seq           INT         NOT NULL COMMENT '步骤序号',
    step          VARCHAR(30) NOT NULL COMMENT 'SEED/BYE/TEAM/FILL/REPAIR/VERIFY 等',
    message       VARCHAR(500) NOT NULL COMMENT '人类可读说明',
    snapshot      TEXT        NULL COMMENT '该步骤后的签位快照 JSON',
    create_time   DATETIME    NOT NULL COMMENT '记录时间',
    KEY idx_tournament_seq (tournament_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抽签审计日志';

-- ============================================================================
--  演示数据（贴近真实校园赛事）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 账号：3 位比赛发起者（密码统一 123456）
-- ---------------------------------------------------------------------------
INSERT INTO sys_local_users (user_id, username, password, real_name, phone, create_time) VALUES
('u_1001', 'wenchenyao',  '123456', '文晨尧', '13800138001', '2025-11-03 09:12:00'),
('u_1002', 'liuyangyang', '123456', '刘阳阳', '13800138002', '2025-12-18 14:36:00'),
('u_1003', 'huangyixuan', '123456', '黄翊轩', '13800138003', '2026-03-02 20:05:00');

-- ---------------------------------------------------------------------------
-- 选手库：u_1001 拥有 18 人（12 男 6 女），u_1002 拥有 8 人，u_1003 刚注册
-- ---------------------------------------------------------------------------
INSERT INTO biz_local_players
(player_id, creator_id, student_id, name, gender, team, phone, level, is_seed, seed_rank, create_time) VALUES
-- ===== u_1001 · 男子组 =====
('p_1001', 'u_1001', '2310044118', '文晨尧', 'M', '计算机学院',   '13901000001', 95, 1, 1, '2025-11-03 09:20:00'),
('p_1002', 'u_1001', '2310044119', '刘阳阳', 'M', '体育学院',     '13901000002', 93, 1, 2, '2025-11-03 09:21:00'),
('p_1003', 'u_1001', '2310024232', '黄翊轩', 'M', '计算机学院',   '13901000003', 91, 1, 3, '2025-11-03 09:22:00'),
('p_1004', 'u_1001', '2310024235', '陈致远', 'M', '电子信息学院', '13901000004', 89, 1, 4, '2025-11-03 09:23:00'),
('p_1005', 'u_1001', '2310044105', '李明哲', 'M', '计算机学院',   '13901000005', 86, 0, 0, '2025-11-03 09:24:00'),
('p_1006', 'u_1001', '2310044106', '王思远', 'M', '体育学院',     '13901000006', 85, 0, 0, '2025-11-03 09:25:00'),
('p_1007', 'u_1001', '2310044107', '张一鸣', 'M', '经济管理学院', '13901000007', 84, 0, 0, '2025-11-03 09:26:00'),
('p_1008', 'u_1001', '2310044108', '赵梓晨', 'M', '数学与统计学院','13901000008', 82, 0, 0, '2025-11-03 09:27:00'),
('p_1009', 'u_1001', '2310044109', '孙浩然', 'M', '计算机学院',   '13901000009', 80, 0, 0, '2025-11-03 09:28:00'),
('p_1010', 'u_1001', '2310044110', '周子墨', 'M', '机械工程学院', '13901000010', 78, 0, 0, '2025-11-03 09:29:00'),
('p_1011', 'u_1001', '2310024236', '吴嘉林', 'M', '经济管理学院', '13901000011', 76, 0, 0, '2025-11-03 09:30:00'),
('p_1012', 'u_1001', '2310024237', '郑昊然', 'M', '电子信息学院', '13901000012', 74, 0, 0, '2025-11-03 09:31:00'),
-- ===== u_1001 · 女子组 =====
('p_1013', 'u_1001', '2310044111', '韩雪晴', 'F', '外国语学院',    '13901000013', 90, 1, 1, '2025-11-05 10:02:00'),
('p_1014', 'u_1001', '2310044112', '沈雨薇', 'F', '数学与统计学院','13901000014', 86, 1, 2, '2025-11-05 10:03:00'),
('p_1015', 'u_1001', '2310044113', '苏婉清', 'F', '体育学院',      '13901000015', 83, 0, 0, '2025-11-05 10:04:00'),
('p_1016', 'u_1001', '2310044114', '林诗涵', 'F', '外国语学院',    '13901000016', 79, 0, 0, '2025-11-05 10:05:00'),
('p_1017', 'u_1001', '2310044115', '高梦洁', 'F', '经济管理学院',  '13901000017', 75, 0, 0, '2025-11-05 10:06:00'),
('p_1018', 'u_1001', '2310044116', '何佳颖', 'F', '化学化工学院',  '13901000018', 71, 0, 0, '2025-11-05 10:07:00'),
-- ===== u_1002 · 男子组 =====
('p_2002', 'u_1002', '2310055102', '李承泽', 'M', '电子信息学院', '13902000002', 90, 1, 2, '2025-12-18 14:41:00'),
('p_2003', 'u_1002', '2310055103', '王浩宇', 'M', '机械工程学院', '13902000003', 87, 0, 0, '2025-12-18 14:42:00'),
('p_2004', 'u_1002', '2310055104', '刘俊熙', 'M', '计算机学院',   '13902000004', 85, 0, 0, '2025-12-18 14:43:00'),
('p_2005', 'u_1002', '2310055105', '陈嘉豪', 'M', '经济管理学院', '13902000005', 81, 0, 0, '2025-12-18 14:44:00'),
('p_2006', 'u_1002', '2310055106', '杨启航', 'M', '体育学院',     '13902000006', 78, 0, 0, '2025-12-18 14:45:00'),
('p_2007', 'u_1002', '2310055107', '周天佑', 'M', '数学与统计学院','13902000007', 75, 0, 0, '2025-12-18 14:46:00'),
('p_2008', 'u_1002', '2310055108', '徐鹏飞', 'M', '化学化工学院', '13902000008', 72, 0, 0, '2025-12-18 14:47:00');

-- 女子选手归入「女单」分支（category 默认 MS，按性别纠正）
UPDATE biz_local_players SET category = 'WS' WHERE gender = 'F';

-- ---------------------------------------------------------------------------
-- 双打分支演示数据：一行 = 一个组合，搭档信息存在 partner_name / partner_student_id / partner_team
-- 注意 partner_team：混双、跨院组队时两人学院并不相同（如 文晨尧 计算机学院 + 韩雪晴 外国语学院），
-- 因此搭档单位必须独立存储，不能默认沿用主选手的 team
-- ---------------------------------------------------------------------------
INSERT INTO biz_local_players
(player_id, creator_id, category, student_id, name, gender, team, phone, level, is_seed, seed_rank,
 partner_name, partner_student_id, partner_team, create_time) VALUES
-- u_1001 · 男双 (MD)
('p_1101', 'u_1001', 'MD', '2310044118', '文晨尧/李明哲', 'M', '计算机学院',    '13901000051', 93, 1, 1, '李明哲', '2310044105', '数学与统计学院', '2026-09-01 10:00:00'),
('p_1102', 'u_1001', 'MD', '2310044119', '刘阳阳/王思远', 'M', '体育学院',      '13901000052', 90, 1, 2, '王思远', '2310044106', '计算机学院',     '2026-09-01 10:01:00'),
('p_1103', 'u_1001', 'MD', '2310024232', '黄翊轩/孙浩然', 'M', '计算机学院',    '13901000053', 86, 0, 0, '孙浩然', '2310044109', '电子信息学院',   '2026-09-01 10:02:00'),
-- u_1001 · 女双 (WD)
('p_1111', 'u_1001', 'WD', '2310044111', '韩雪晴/苏婉清', 'F', '外国语学院',    '13901000054', 89, 1, 1, '苏婉清', '2310044113', '外国语学院',     '2026-09-01 10:03:00'),
('p_1112', 'u_1001', 'WD', '2310044112', '沈雨薇/林诗涵', 'F', '数学与统计学院','13901000055', 84, 1, 2, '林诗涵', '2310044114', '体育学院',       '2026-09-01 10:04:00'),
-- u_1001 · 混双 (XD)
('p_1121', 'u_1001', 'XD', '2310044118', '文晨尧/韩雪晴', 'M', '计算机学院',    '13901000056', 92, 1, 1, '韩雪晴', '2310044111', '外国语学院',     '2026-09-01 10:05:00'),
('p_1122', 'u_1001', 'XD', '2310024235', '陈致远/沈雨薇', 'M', '电子信息学院',  '13901000057', 87, 1, 2, '沈雨薇', '2310044112', '数学与统计学院', '2026-09-01 10:06:00');

-- ---------------------------------------------------------------------------
-- 赛事：4 场，覆盖 已结束 / 进行中 / 待编排 / 草稿
-- ---------------------------------------------------------------------------
INSERT INTO biz_local_tournaments (tournament_id, creator_id, title, event_type, format, status, actual_participants, bracket_size, bye_count, seed_count, group_count, group_advance, best_of, points_per_game, points_cap, avoid_same_team, champion_id, runner_up_id, third_id, remark, create_time) VALUES
('t_2026001', 'u_1001', '2026 校园羽毛球公开赛 · 男子单打', 'MS', 'SINGLE_ELIM', 'FINISHED', 8, 8, 0, 4, 0, 2, 3, 21, 30, 1, 'p_1001', 'p_1002', 'p_1003', '校级公开赛，八强单淘汰，设季军赛', '2026-04-20 10:15:00'),
('t_2026002', 'u_1001', '计算机学院院内选拔赛 · 男子单打', 'MS', 'GROUP_KNOCKOUT', 'ONGOING', 12, 0, 0, 4, 4, 2, 3, 21, 30, 1, NULL, NULL, NULL, '四个小组单循环，每组前两名交叉淘汰', '2026-05-28 16:40:00'),
('t_2026004', 'u_1002', '体育学院对抗赛 · 男子单打', 'MS', 'SINGLE_ELIM', 'READY', 8, 0, 0, 4, 0, 2, 3, 21, 30, 1, NULL, NULL, NULL, '八人名单已定，等待抽签编排', '2026-09-10 19:22:00'),
('t_2026005', 'u_1001', '2026 新生邀请赛 · 混合双打', 'XD', 'GROUP_KNOCKOUT', 'DRAFT', 0, 0, 0, 4, 4, 2, 3, 21, 30, 1, NULL, NULL, NULL, '新生邀请赛，待确定参赛名单', '2026-09-20 09:48:00');

-- ---------------------------------------------------------------------------
-- 参赛名单
-- ---------------------------------------------------------------------------
-- t_2026001：8 人单淘汰
INSERT INTO biz_local_entries (entry_id, tournament_id, player_id, seed_flag, seed_rank, create_time) VALUES
('e_1001', 't_2026001', 'p_1001', 1, 1, '2026-04-20 10:20:00'),
('e_1002', 't_2026001', 'p_1002', 1, 2, '2026-04-20 10:20:00'),
('e_1003', 't_2026001', 'p_1003', 1, 3, '2026-04-20 10:20:00'),
('e_1004', 't_2026001', 'p_1004', 1, 4, '2026-04-20 10:20:00'),
('e_1005', 't_2026001', 'p_1005', 0, 0, '2026-04-20 10:21:00'),
('e_1006', 't_2026001', 'p_1006', 0, 0, '2026-04-20 10:21:00'),
('e_1007', 't_2026001', 'p_1007', 0, 0, '2026-04-20 10:21:00'),
('e_1008', 't_2026001', 'p_1008', 0, 0, '2026-04-20 10:21:00');

-- t_2026002：12 人小组+淘汰
INSERT INTO biz_local_entries (entry_id, tournament_id, player_id, seed_flag, seed_rank, create_time) VALUES
('e_2001', 't_2026002', 'p_1001', 1, 1, '2026-05-28 16:45:00'),
('e_2002', 't_2026002', 'p_1002', 1, 2, '2026-05-28 16:45:00'),
('e_2003', 't_2026002', 'p_1003', 1, 3, '2026-05-28 16:45:00'),
('e_2004', 't_2026002', 'p_1004', 1, 4, '2026-05-28 16:45:00'),
('e_2005', 't_2026002', 'p_1005', 0, 0, '2026-05-28 16:46:00'),
('e_2006', 't_2026002', 'p_1006', 0, 0, '2026-05-28 16:46:00'),
('e_2007', 't_2026002', 'p_1007', 0, 0, '2026-05-28 16:46:00'),
('e_2008', 't_2026002', 'p_1008', 0, 0, '2026-05-28 16:46:00'),
('e_2009', 't_2026002', 'p_1009', 0, 0, '2026-05-28 16:47:00'),
('e_2010', 't_2026002', 'p_1010', 0, 0, '2026-05-28 16:47:00'),
('e_2011', 't_2026002', 'p_1011', 0, 0, '2026-05-28 16:47:00'),
('e_2012', 't_2026002', 'p_1012', 0, 0, '2026-05-28 16:47:00');

-- t_2026004：8 人待抽签
INSERT INTO biz_local_entries (entry_id, tournament_id, player_id, seed_flag, seed_rank, create_time) VALUES
('e_4001', 't_2026004', 'p_2001', 1, 1, '2026-09-10 19:30:00'),
('e_4002', 't_2026004', 'p_2002', 1, 2, '2026-09-10 19:30:00'),
('e_4003', 't_2026004', 'p_2003', 0, 0, '2026-09-10 19:31:00'),
('e_4004', 't_2026004', 'p_2004', 0, 0, '2026-09-10 19:31:00'),
('e_4005', 't_2026004', 'p_2005', 0, 0, '2026-09-10 19:32:00'),
('e_4006', 't_2026004', 'p_2006', 0, 0, '2026-09-10 19:32:00'),
('e_4007', 't_2026004', 'p_2007', 0, 0, '2026-09-10 19:33:00'),
('e_4008', 't_2026004', 'p_2008', 0, 0, '2026-09-10 19:33:00');

-- ---------------------------------------------------------------------------
-- 小组分组（t_2026002：4 组 × 3 人，蛇形落位）
-- ---------------------------------------------------------------------------
INSERT INTO biz_local_groups (group_id, tournament_id, group_no, player_id, seed_position) VALUES
('g_2001', 't_2026002', 1, 'p_1001', 1),
('g_2002', 't_2026002', 1, 'p_1005', 2),
('g_2003', 't_2026002', 1, 'p_1009', 3),
('g_2004', 't_2026002', 2, 'p_1002', 1),
('g_2005', 't_2026002', 2, 'p_1006', 2),
('g_2006', 't_2026002', 2, 'p_1010', 3),
('g_2007', 't_2026002', 3, 'p_1003', 1),
('g_2008', 't_2026002', 3, 'p_1007', 2),
('g_2009', 't_2026002', 3, 'p_1011', 3),
('g_2010', 't_2026002', 4, 'p_1004', 1),
('g_2011', 't_2026002', 4, 'p_1008', 2),
('g_2012', 't_2026002', 4, 'p_1012', 3);

-- ---------------------------------------------------------------------------
-- 场次
-- ---------------------------------------------------------------------------
-- ===== t_2026001：8 人单淘汰（已全部完赛，含季军赛）=====
INSERT INTO biz_local_matches (match_id, tournament_id, stage, round, round_name, group_no, match_order, player_a_id, player_b_id, winner_id, loser_id, games_json, score_a, score_b, games_a, games_b, status, next_match_id, next_slot, loser_next_id, loser_slot, remark) VALUES
('mt2026001_K1_1', 't_2026001', 'KNOCKOUT', 1, '1/4决赛', 0, 1, 'p_1001', 'p_1008', 'p_1001', 'p_1008', '[{"a":21,"b":12},{"a":21,"b":15}]', 42, 27, 2, 0, 'DONE', 'mt2026001_K2_1', 'A', NULL, NULL, NULL),
('mt2026001_K1_2', 't_2026001', 'KNOCKOUT', 1, '1/4决赛', 0, 2, 'p_1004', 'p_1005', 'p_1004', 'p_1005', '[{"a":21,"b":18},{"a":19,"b":21},{"a":21,"b":17}]', 61, 56, 2, 1, 'DONE', 'mt2026001_K2_1', 'B', NULL, NULL, NULL),
('mt2026001_K1_3', 't_2026001', 'KNOCKOUT', 1, '1/4决赛', 0, 3, 'p_1002', 'p_1007', 'p_1002', 'p_1007', '[{"a":21,"b":10},{"a":21,"b":13}]', 42, 23, 2, 0, 'DONE', 'mt2026001_K2_2', 'A', NULL, NULL, NULL),
('mt2026001_K1_4', 't_2026001', 'KNOCKOUT', 1, '1/4决赛', 0, 4, 'p_1003', 'p_1006', 'p_1003', 'p_1006', '[{"a":21,"b":16},{"a":21,"b":14}]', 42, 30, 2, 0, 'DONE', 'mt2026001_K2_2', 'B', NULL, NULL, NULL),
('mt2026001_K2_1', 't_2026001', 'KNOCKOUT', 2, '半决赛', 0, 1, 'p_1001', 'p_1004', 'p_1001', 'p_1004', '[{"a":21,"b":15},{"a":21,"b":17}]', 42, 32, 2, 0, 'DONE', 'mt2026001_K3_1', 'A', 'mt2026001_K3_2', 'A', NULL),
('mt2026001_K2_2', 't_2026001', 'KNOCKOUT', 2, '半决赛', 0, 2, 'p_1002', 'p_1003', 'p_1002', 'p_1003', '[{"a":18,"b":21},{"a":21,"b":19},{"a":21,"b":16}]', 60, 56, 2, 1, 'DONE', 'mt2026001_K3_1', 'B', 'mt2026001_K3_2', 'B', NULL),
('mt2026001_K3_1', 't_2026001', 'KNOCKOUT', 3, '决赛', 0, 1, 'p_1001', 'p_1002', 'p_1001', 'p_1002', '[{"a":21,"b":19},{"a":19,"b":21},{"a":21,"b":18}]', 61, 58, 2, 1, 'DONE', NULL, NULL, NULL, NULL, '决赛：文晨尧 2-1 刘阳阳'),
('mt2026001_K3_2', 't_2026001', 'THIRD_PLACE', 3, '季军赛', 0, 1, 'p_1004', 'p_1003', 'p_1003', 'p_1004', '[{"a":17,"b":21},{"a":19,"b":21}]', 36, 42, 0, 2, 'DONE', NULL, NULL, NULL, NULL, '季军：黄翊轩'),
('mt2026002_g1_1', 't_2026002', 'GROUP', 1, '小组赛第1轮', 1, 1, 'p_1005', 'p_1009', 'p_1005', 'p_1009', '[{"a":21,"b":18},{"a":21,"b":16}]', 42, 34, 2, 0, 'DONE', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g1_2', 't_2026002', 'GROUP', 2, '小组赛第2轮', 1, 1, 'p_1001', 'p_1009', 'p_1001', 'p_1009', '[{"a":21,"b":9},{"a":21,"b":11}]', 42, 20, 2, 0, 'DONE', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g1_3', 't_2026002', 'GROUP', 3, '小组赛第3轮', 1, 1, 'p_1001', 'p_1005', 'p_1001', 'p_1005', '[{"a":21,"b":15},{"a":21,"b":19}]', 42, 34, 2, 0, 'DONE', NULL, NULL, NULL, NULL, 'A 组：文晨尧第一、李明哲第二'),
('mt2026002_g2_1', 't_2026002', 'GROUP', 1, '小组赛第1轮', 2, 1, 'p_1006', 'p_1010', 'p_1006', 'p_1010', '[{"a":21,"b":17},{"a":21,"b":19}]', 42, 36, 2, 0, 'DONE', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g2_2', 't_2026002', 'GROUP', 2, '小组赛第2轮', 2, 1, 'p_1002', 'p_1010', 'p_1002', 'p_1010', '[{"a":21,"b":8},{"a":21,"b":13}]', 42, 21, 2, 0, 'DONE', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g2_3', 't_2026002', 'GROUP', 3, '小组赛第3轮', 2, 1, 'p_1002', 'p_1006', 'p_1002', 'p_1006', '[{"a":21,"b":14},{"a":20,"b":22},{"a":21,"b":18}]', 62, 54, 2, 1, 'DONE', NULL, NULL, NULL, NULL, 'B 组：刘阳阳第一、王思远第二'),
('mt2026002_g3_1', 't_2026002', 'GROUP', 1, '小组赛第1轮', 3, 1, 'p_1007', 'p_1011', 'p_1007', 'p_1011', '[{"a":21,"b":19},{"a":21,"b":17}]', 42, 36, 2, 0, 'DONE', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g3_2', 't_2026002', 'GROUP', 2, '小组赛第2轮', 3, 1, 'p_1003', 'p_1011', NULL, NULL, NULL, 0, 0, 0, 0, 'READY', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g3_3', 't_2026002', 'GROUP', 3, '小组赛第3轮', 3, 1, 'p_1003', 'p_1007', NULL, NULL, NULL, 0, 0, 0, 0, 'PENDING', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g4_1', 't_2026002', 'GROUP', 1, '小组赛第1轮', 4, 1, 'p_1008', 'p_1012', 'p_1008', 'p_1012', '[{"a":21,"b":15},{"a":22,"b":20}]', 43, 35, 2, 0, 'DONE', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g4_2', 't_2026002', 'GROUP', 2, '小组赛第2轮', 4, 1, 'p_1004', 'p_1012', NULL, NULL, NULL, 0, 0, 0, 0, 'READY', NULL, NULL, NULL, NULL, NULL),
('mt2026002_g4_3', 't_2026002', 'GROUP', 3, '小组赛第3轮', 4, 1, 'p_1004', 'p_1008', NULL, NULL, NULL, 0, 0, 0, 0, 'PENDING', NULL, NULL, NULL, NULL, NULL);

-- ---------------------------------------------------------------------------
-- 抽签留痕
-- ---------------------------------------------------------------------------
INSERT INTO biz_local_draw_logs (log_id, tournament_id, seq, step, message, snapshot, create_time) VALUES
('dl_1001', 't_2026001', 1, 'SEED',
 '确认 4 名种子选手：文晨尧（1号）、刘阳阳（2号）、黄翊轩（3号）、陈致远（4号）',
 NULL, '2026-04-21 09:00:00'),
('dl_1002', 't_2026001', 2, 'BYE',
 '参赛 8 人恰好填满 8 签位，无轮空', NULL, '2026-04-21 09:00:01'),
('dl_1003', 't_2026001', 3, 'TEAM',
 '同单位冲突检测：计算机学院 3 人，已分散至第 1/3/5 签位', NULL, '2026-04-21 09:00:02'),
('dl_1004', 't_2026001', 4, 'FILL',
 '其余 4 名选手随机落位，签位表生成完成，共 8 场对阵',
 'p_1001|p_1008|p_1004|p_1005|p_1003|p_1006|p_1002|p_1007', '2026-04-21 09:00:03'),
('dl_1005', 't_2026001', 5, 'VERIFY',
 '校验通过：4 个 1/4 区实力分差 ≤ 6，无同单位首轮相遇', NULL, '2026-04-21 09:00:04'),

('dl_2001', 't_2026002', 1, 'SEED',
 '确认 4 名种子选手，按蛇形落位分入 4 个小组', NULL, '2026-05-29 10:12:00'),
('dl_2002', 't_2026002', 2, 'GROUP',
 '蛇形分组：第 1 组共 3 人 —— 文晨尧、李明哲、孙浩然', NULL, '2026-05-29 10:12:01'),
('dl_2003', 't_2026002', 3, 'GROUP',
 '蛇形分组：第 2 组共 3 人 —— 刘阳阳、王思远、周子墨', NULL, '2026-05-29 10:12:01'),
('dl_2004', 't_2026002', 4, 'GROUP',
 '蛇形分组：第 3 组共 3 人 —— 黄翊轩、张一鸣、吴嘉林', NULL, '2026-05-29 10:12:02'),
('dl_2005', 't_2026002', 5, 'GROUP',
 '蛇形分组：第 4 组共 3 人 —— 陈致远、赵梓晨、郑昊然', NULL, '2026-05-29 10:12:02'),
('dl_2006', 't_2026002', 6, 'GROUP_LOCK',
 '共 4 组，每组前 2 名出线，进入交叉淘汰赛（A1-B2、B1-A2 交叉对阵）', NULL, '2026-05-29 10:12:03');
