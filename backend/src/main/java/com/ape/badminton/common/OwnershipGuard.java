package com.ape.badminton.common;

import com.ape.badminton.entity.Match;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.MatchMapper;
import com.ape.badminton.mapper.TournamentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 数据归属校验
 *
 * 系统按账号隔离数据（我的赛事 / 我的选手库 / 我的场地），
 * 因此所有以赛事为入口的接口都必须先确认：该赛事属于当前登录的比赛发起者。
 *
 * 统一在此校验，避免各 Controller 各自实现导致漏判
 * —— 传入他人赛事 ID 时一律按「不存在」处理（404），不回显任何业务信息。
 */
@Component
public class OwnershipGuard {

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private MatchMapper matchMapper;

    /** 校验赛事存在且属于当前发起者 */
    public void requireTournament(String tournamentId) {
        if (tournamentId == null || tournamentId.trim().isEmpty()) {
            throw new BizException(40001, "缺少赛事ID");
        }
        Tournament t = tournamentMapper.selectById(tournamentId);
        String me = AuthContext.userId();
        if (t == null || me == null || !me.equals(t.getCreator_id())) {
            throw new BizException(40400, "赛事不存在");
        }
    }

    /** 校验场次存在，且其所属赛事属于当前发起者 */
    public void requireMatch(String matchId) {
        if (matchId == null || matchId.trim().isEmpty()) {
            throw new BizException(40001, "缺少场次ID");
        }
        Match m = matchMapper.selectById(matchId);
        if (m == null) {
            throw new BizException(40400, "场次不存在");
        }
        requireTournament(m.getTournament_id());
    }
}
