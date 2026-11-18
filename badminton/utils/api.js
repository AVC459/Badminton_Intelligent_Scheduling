/**
 * utils/api.js — 业务接口层（全项目唯一接口清单）
 *
 * 页面只调用本文件的方法，不直接拼 URL。
 * 所有请求经由 utils/request.js 统一携带 token、解析 { code, message, data }、统一报错。
 *
 * v3.0 接口结构：
 *   用户        只有一种身份「比赛发起者」，无角色分级
 *   选手库      按发起者隔离，支持 Excel / 文本 + 本地 Ollama 智能识别导入
 *   赛事        按发起者隔离（我创建的赛事）
 *   参赛名单    取代原「报名」链路，发起者从选手库直接选人
 */
import { request, uploadFile, setToken } from './request';
import { setCurrentUser } from './session';

// ==================== 比赛发起者 ====================

export function login(username, password) {
  return request({ url: '/user/login', method: 'POST', data: { username, password } })
    .then((res) => {
      // 后端返回 { token, user }
      if (res && res.token) {
        setToken(res.token);
      }
      if (res && res.user) {
        setCurrentUser(res.user);
      }
      return res;
    });
}

export function registerUser(user) {
  return request({ url: '/user/register', method: 'POST', data: user });
}

/** 我的数据概览：选手库规模、我创建的赛事、权限清单 */
export function getMyProfile() {
  return request({ url: '/user/profile' });
}

export function logout() {
  return request({ url: '/user/logout', method: 'POST' });
}

// ==================== 我的选手库 ====================

/**
 * 选手列表
 * @param {{keyword?:string, team?:string, seedOnly?:boolean, category?:string}} [params]
 *        category 为项目分支 MS/WS/MD/WD/XD，不传表示全部
 */
export function getPlayers(params) {
  return request({ url: '/player/getAll', data: params || {} });
}

export function getPlayerById(playerId) {
  return request({ url: '/player/getById/' + playerId });
}

export function addPlayer(player) {
  return request({ url: '/player/add', method: 'POST', data: player });
}

export function updatePlayer(player) {
  return request({ url: '/player/update', method: 'PUT', data: player });
}

export function deletePlayer(playerId) {
  return request({ url: '/player/delete/' + playerId, method: 'DELETE' });
}

/** 预览确认后的批量落库 */
export function batchImportPlayers(players) {
  return request({ url: '/player/batchImport', method: 'POST', data: players });
}

export function getTeams() {
  return request({ url: '/player/teams' });
}

/** 本地 Ollama 智能识别是否就绪 */
export function getImportAiStatus() {
  return request({ url: '/player/import/aiStatus' });
}

/**
 * 解析 Excel 名单（不落库，返回预览）
 * @param {string} filePath uni.chooseMessageFile / chooseFile 得到的临时路径
 * @param {boolean} useAi 是否调用本地 Ollama 智能识别
 * @param {string} category 项目分支 MS/WS/MD/WD/XD（双打分支会识别组队信息）
 */
export function parsePlayersFromExcel(filePath, useAi, category) {
  return uploadFile({
    url: '/player/import/excel',
    filePath,
    name: 'file',
    formData: {
      useAi: useAi === false ? 'false' : 'true',
      category: category || 'MS'
    },
    // 后端只让模型做「列映射」，总预算 = 一次推理（≤ ollama.timeout-seconds）
    // + 规则解析兜底。前端留到 120s 足够；真超时说明后端异常，
    // 直接报错也比无限转圈好，且页面上随时可以点「跳过」。
    timeout: 120000
  });
}

/**
 * 解析粘贴文本名单（不落库，返回预览）
 * @param {string} text 每行一位选手（双打为一行一个组合）
 * @param {boolean} useAi 是否调用本地 Ollama 智能识别
 * @param {string} category 项目分支 MS/WS/MD/WD/XD
 */
export function parsePlayersFromText(text, useAi, category) {
  return request({
    url: '/player/import/text',
    method: 'POST',
    data: { text, use_ai: useAi !== false, category: category || 'MS' },
    // 同 parsePlayersFromExcel：模型只做列映射，120s 已是充足余量
    timeout: 120000
  });
}

// ==================== 赛事 ====================

/**
 * 我创建的赛事列表
 *
 * 数据按账号隔离：后端只返回当前登录发起者自己的赛事，不存在查看他人赛事的口子。
 * @param {{status?:string, keyword?:string}} [params]
 */
export function getTournaments(params) {
  return request({ url: '/tournament/getAll', data: params || {} });
}

export function getTournamentById(id) {
  return request({ url: '/tournament/getById/' + id });
}

/** 赛事详情聚合：赛事主体 + 参赛名单 + 场次进度 + 抽签留痕 */
export function getTournamentDetail(id) {
  return request({ url: '/tournament/detail/' + id });
}

export function addTournament(tournament) {
  return request({ url: '/tournament/add', method: 'POST', data: tournament });
}

export function updateTournament(tournament) {
  return request({ url: '/tournament/update', method: 'PUT', data: tournament });
}

export function deleteTournament(id) {
  return request({ url: '/tournament/delete/' + id, method: 'DELETE' });
}

// ==================== 参赛名单 ====================

/** 参赛名单（含选手档案与赛事级种子信息） */
export function getEntries(tournamentId) {
  return request({ url: '/entry/list/' + tournamentId });
}

/** 参赛选手档案（抽签池） */
export function getEntryPlayers(tournamentId) {
  return request({ url: '/entry/players/' + tournamentId });
}

/** 从选手库批量选入参赛名单 */
export function addEntries(tournamentId, playerIds) {
  return request({
    url: '/entry/add',
    method: 'POST',
    data: { tournament_id: tournamentId, player_ids: playerIds }
  });
}

/** 移出参赛名单 */
export function removeEntry(entryId) {
  return request({ url: '/entry/remove/' + entryId, method: 'DELETE' });
}

/** 设置 / 取消赛事级种子 */
export function setEntrySeed(entryId, seedFlag, seedRank) {
  const qs = [];
  if (seedFlag != null) qs.push('seedFlag=' + seedFlag);
  if (seedRank != null) qs.push('seedRank=' + seedRank);
  return request({
    url: '/entry/seed/' + entryId + (qs.length ? '?' + qs.join('&') : ''),
    method: 'PUT'
  });
}

// ==================== 抽签编排 ====================

/** 试算抽签（不落库），用于配置页实时预览规模 */
export function previewDraw(tournamentId, config) {
  return request({ url: '/draw/preview/' + tournamentId, method: 'POST', data: config || {} });
}

/** 正式执行抽签编排 */
export function executeDraw(tournamentId, config) {
  return request({ url: '/draw/execute/' + tournamentId, method: 'POST', data: config || {} });
}

/** 抽签过程留痕 */
export function getDrawLogs(tournamentId) {
  return request({ url: '/draw/logs/' + tournamentId });
}

/** 公平性报告 */
export function getFairness(tournamentId) {
  return request({ url: '/draw/fairness/' + tournamentId });
}

/** 小组赛结束后生成交叉淘汰赛程 */
export function generateKnockout(tournamentId) {
  return request({ url: '/draw/generateKnockout/' + tournamentId, method: 'POST' });
}

/** 撤销抽签 */
export function resetDraw(tournamentId) {
  return request({ url: '/draw/reset/' + tournamentId, method: 'POST' });
}

// ==================== 场次 ====================

export function getMatches(tournamentId) {
  return request({ url: '/match/list/' + tournamentId });
}

export function getMatchById(matchId) {
  return request({ url: '/match/getById/' + matchId });
}

/**
 * 录入比分
 * @param {{matchId:string, games:Array<{a:number,b:number}>, walkover?:boolean, retiredPlayerId?:string, remark?:string}} dto
 */
export function saveScore(dto) {
  return request({ url: '/match/score', method: 'POST', data: dto });
}

/** 整体排名（循环赛制即最终名次） */
export function getStandings(tournamentId) {
  return request({ url: '/match/standings/' + tournamentId });
}

/** 小组积分榜 */
export function getGroupStandings(tournamentId) {
  return request({ url: '/match/groups/' + tournamentId });
}

// ==================== 统计 ====================

export function getOverview() {
  return request({ url: '/stats/overview' });
}

export function getReport(tournamentId) {
  return request({ url: '/stats/report/' + tournamentId });
}
