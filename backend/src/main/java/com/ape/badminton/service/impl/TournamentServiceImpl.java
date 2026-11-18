package com.ape.badminton.service.impl;

import com.ape.badminton.common.AuthContext;
import com.ape.badminton.entity.Tournament;
import com.ape.badminton.mapper.DrawLogMapper;
import com.ape.badminton.mapper.EntryMapper;
import com.ape.badminton.mapper.GroupMapper;
import com.ape.badminton.mapper.MatchMapper;
import com.ape.badminton.mapper.TournamentMapper;
import com.ape.badminton.service.TournamentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TournamentServiceImpl implements TournamentService {

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private EntryMapper entryMapper;

    @Autowired
    private GroupMapper groupMapper;

    @Autowired
    private MatchMapper matchMapper;

    @Autowired
    private DrawLogMapper drawLogMapper;

    @Override
    public List<Tournament> list(String status, String keyword, boolean onlyMine) {
        QueryWrapper<Tournament> w = new QueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            w.eq("status", status.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            w.like("title", keyword.trim());
        }
        if (onlyMine) {
            String me = AuthContext.userId();
            if (me == null) {
                // 未登录时返回空集合，避免越权看到他人赛事
                return java.util.Collections.emptyList();
            }
            w.eq("creator_id", me);
        }
        w.orderByDesc("create_time");
        return tournamentMapper.selectList(w);
    }

    @Override
    public Tournament getById(String tournamentId) {
        return tournamentId == null ? null : tournamentMapper.selectById(tournamentId);
    }

    @Override
    public boolean add(Tournament tournament) {
        return tournamentMapper.insert(tournament) > 0;
    }

    @Override
    public boolean update(Tournament tournament) {
        return tournamentMapper.updateById(tournament) > 0;
    }

    /**
     * 删除赛事。
     *
     * 注意：赛事是「根」实体，参赛名单、分组、场次、抽签日志都以 tournament_id 挂在它下面。
     * 这些子表没有数据库外键约束（历史原因），所以必须在业务层显式级联清理，
     * 否则会在库里留下永远查不到、也永远删不掉的孤儿行，
     * 并且会污染后续统计（例如按 tournament_id 关联时的笛卡尔积）。
     *
     * 顺序：先删叶子，再删根。整个过程放在同一事务里，避免删一半失败留下脏数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String tournamentId) {
        if (tournamentId == null) {
            return false;
        }
        // 1) 抽签审计日志
        drawLogMapper.delete(new QueryWrapper<com.ape.badminton.entity.DrawLog>()
                .eq("tournament_id", tournamentId));
        // 2) 场次（对阵）
        matchMapper.delete(new QueryWrapper<com.ape.badminton.entity.Match>()
                .eq("tournament_id", tournamentId));
        // 3) 小组分组
        groupMapper.delete(new QueryWrapper<com.ape.badminton.entity.GroupEntry>()
                .eq("tournament_id", tournamentId));
        // 4) 参赛名单
        entryMapper.delete(new QueryWrapper<com.ape.badminton.entity.Entry>()
                .eq("tournament_id", tournamentId));
        // 5) 赛事本体
        return tournamentMapper.deleteById(tournamentId) > 0;
    }

    @Override
    public long count() {
        return tournamentMapper.selectCount(null);
    }

    @Override
    public long countByStatus(String status) {
        QueryWrapper<Tournament> w = new QueryWrapper<>();
        w.eq("status", status);
        return tournamentMapper.selectCount(w);
    }

    @Override
    public long countByCreator(String creatorId) {
        if (creatorId == null) return 0;
        QueryWrapper<Tournament> w = new QueryWrapper<>();
        w.eq("creator_id", creatorId);
        return tournamentMapper.selectCount(w);
    }

    @Override
    public long countByCreatorAndStatus(String creatorId, String status) {
        if (creatorId == null) return 0;
        QueryWrapper<Tournament> w = new QueryWrapper<>();
        w.eq("creator_id", creatorId).eq("status", status);
        return tournamentMapper.selectCount(w);
    }
}
