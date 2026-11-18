/**
 * utils/dict.js — 业务字典与展示映射
 *
 * 原则：所有「枚举 → 文案 / 语义色」的映射集中在此，页面不写死中文字符串，
 * 便于后续调整文案或增加状态时只改一处。
 *
 * v3.0：取消角色字典与报名状态字典（系统已无角色分类、无报名流程）。
 */

// ==================== 赛制 ====================

export const FORMATS = [
  {
    value: 'SINGLE_ELIM',
    label: '单淘汰赛',
    short: '单淘汰',
    desc: '输一场即出局，赛程最短，适合人数多、时间紧'
  },
  {
    value: 'GROUP_KNOCKOUT',
    label: '小组循环 + 交叉淘汰',
    short: '小组+淘汰',
    desc: '先分组循环，各组出线后交叉淘汰，兼顾参与度与观赏性'
  }
];

/**
 * 已下线的历史赛制：只用于展示旧赛事数据。
 * 单循环赛已从创建入口移除，但库里可能存在历史赛事，仍要给出正常文案。
 */
const LEGACY_FORMATS = {
  ROUND_ROBIN: { label: '单循环赛', short: '单循环' }
};

function findFormat(v) {
  const key = String(v || '').toUpperCase();
  return FORMATS.find((x) => x.value === key) || LEGACY_FORMATS[key] || null;
}

export function formatLabel(v) {
  const f = findFormat(v);
  return f ? f.label : v || '-';
}

export function formatShort(v) {
  const f = findFormat(v);
  return f ? f.short : v || '-';
}

// ==================== 赛事状态 ====================

export const TOURNAMENT_STATUS = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  READY: { label: '待编排', tone: 'info' },
  DRAWN: { label: '已编排', tone: 'warning' },
  ONGOING: { label: '进行中', tone: 'warning' },
  FINISHED: { label: '已结束', tone: 'success' }
};

export function tournamentStatusLabel(v) {
  const s = TOURNAMENT_STATUS[v];
  return s ? s.label : v || '-';
}

export function tournamentStatusTone(v) {
  const s = TOURNAMENT_STATUS[v];
  return s ? s.tone : 'neutral';
}

/** 赛事状态筛选项（页面筛选用） */
export const TOURNAMENT_STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'READY', label: '待编排' },
  { value: 'DRAWN', label: '已编排' },
  { value: 'ONGOING', label: '进行中' },
  { value: 'FINISHED', label: '已结束' },
  { value: 'DRAFT', label: '草稿' }
];

// ==================== 场次状态 ====================

export const MATCH_STATUS = {
  PENDING: { label: '待定', tone: 'neutral' },
  READY: { label: '待比赛', tone: 'info' },
  BYE: { label: '轮空', tone: 'neutral' },
  DONE: { label: '已结束', tone: 'success' },
  WALKOVER: { label: '弃权', tone: 'warning' }
};

export function matchStatusLabel(v) {
  const s = MATCH_STATUS[v];
  return s ? s.label : v || '-';
}

export function matchStatusTone(v) {
  const s = MATCH_STATUS[v];
  return s ? s.tone : 'neutral';
}

// ==================== 阶段 ====================

export const STAGES = {
  KNOCKOUT: { label: '淘汰赛', short: '淘汰' },
  THIRD_PLACE: { label: '季军赛', short: '季军' },
  ROUND_ROBIN: { label: '循环赛', short: '循环' },
  GROUP: { label: '小组赛', short: '小组' }
};

export function stageLabel(v) {
  const s = STAGES[v];
  return s ? s.label : v || '-';
}

export function stageShort(v) {
  const s = STAGES[v];
  return s ? s.short : v || '-';
}

// ==================== 项目类型 ====================

export const EVENT_TYPES = [
  { value: 'MS', label: '男子单打' },
  { value: 'WS', label: '女子单打' },
  { value: 'MD', label: '男子双打' },
  { value: 'WD', label: '女子双打' },
  { value: 'XD', label: '混合双打' }
];

export function eventTypeLabel(v) {
  const e = EVENT_TYPES.find((x) => x.value === v);
  return e ? e.label : v || '-';
}

// ==================== 性别 ====================

export function genderLabel(v) {
  return String(v).toUpperCase() === 'F' ? '女' : '男';
}

// ==================== 选手库项目分支 ====================

/**
 * 选手库按项目类型分为 5 个分支，取值与赛事 event_type 完全一致，
 * 因此赛事可以直接用 event_type 去匹配对应分支的选手。
 */
export const LIBRARY_CATEGORIES = [
  { value: 'MS', label: '男单', doubles: false },
  { value: 'WS', label: '女单', doubles: false },
  { value: 'MD', label: '男双', doubles: true },
  { value: 'WD', label: '女双', doubles: true },
  { value: 'XD', label: '混双', doubles: true }
];

export function categoryLabel(v) {
  const c = LIBRARY_CATEGORIES.find((x) => x.value === String(v || '').toUpperCase());
  return c ? c.label : v || '—';
}

/** 是否双打分支（双打的一条记录是一个组合，需要组队信息） */
export function isDoublesCategory(v) {
  const c = LIBRARY_CATEGORIES.find((x) => x.value === String(v || '').toUpperCase());
  return !!(c && c.doubles);
}

/**
 * 项目分支隐含的性别：MS/MD 男，WS/WD 女，XD 不限（空串）。
 *
 * 用于参赛名单页按赛事项目预置性别筛选 —— 男子单打只列男生，
 * 从源头上避免「把女生选进男单」这类常识性错误。
 */
export function genderOfCategory(v) {
  const c = String(v || '').toUpperCase();
  if (c === 'MS' || c === 'MD') return 'M';
  if (c === 'WS' || c === 'WD') return 'F';
  return '';
}

/** 双打分支的导入列说明 */
export function doublesHint() {
  return '双打按「组合」导入：一行一个组合。表头建议为 学号,姓名,搭档姓名,性别,单位,水平分；'
    + '也可以把组合写在同一格里，如 姓名=张三/李四、学号=2023110001/2023110002。';
}

// ==================== 通用工具 ====================

/** 日期字符串 → 友好显示（今天 14:30 / 10月5日 14:30） */
export function friendlyTime(value) {
  if (!value) return '';
  const d = new Date(String(value).replace(/-/g, '/'));
  if (isNaN(d.getTime())) return String(value);
  const now = new Date();
  const hm = pad(d.getHours()) + ':' + pad(d.getMinutes());
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate();
  if (sameDay) return '今天 ' + hm;
  return d.getMonth() + 1 + '月' + d.getDate() + '日 ' + hm;
}

export function formatDate(value) {
  if (!value) return '';
  const d = new Date(String(value).replace(/-/g, '/'));
  if (isNaN(d.getTime())) return String(value);
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
}

export function formatDateTime(value) {
  if (!value) return '';
  const d = new Date(String(value).replace(/-/g, '/'));
  if (isNaN(d.getTime())) return String(value);
  return (
    d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
    ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
  );
}

/** 报名/参赛人数的简写 */
export function peopleText(n) {
  return (n || 0) + ' 人';
}

function pad(n) {
  return n < 10 ? '0' + n : String(n);
}

/** 数组按字段分组 */
export function groupBy(list, keyFn) {
  const map = {};
  (list || []).forEach((item) => {
    const k = keyFn(item);
    if (!map[k]) map[k] = [];
    map[k].push(item);
  });
  return map;
}
