/**
 * 离线中文文字检查引擎 v1.0
 * 用途：工作交接、日常沟通场景的明显错字、标点、句子错误检查
 * 原则：只报硬错误，不做风格改写；高置信度=红色问题，模糊=灰色提示
 * 运行环境：浏览器 / Node.js 双端
 */
(function (root, factory) {
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = factory();
  } else {
    root.TextChecker = factory();
  }
}(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var CJK = '\\u4e00-\\u9fff';

  /* ============ 1. 错别字对（错→对） ============
   * 优先使用外部注入的大词库（TEXT_CHECKER_PAIRS，949 对，词典校验过）；
   * 未注入时退回内置精选 50 对，保证单文件可独立工作 */
  var BUILTIN_PAIRS = [
    ['以经', '已经'], ['已后', '以后'], ['一后', '以后'], ['后在', '以后'],
    ['在见', '再见'], ['再此', '在此'], ['致此', '至此'],
    ['做为', '作为'], ['既使', '即使'], ['即然', '既然'], ['立既', '立即'],
    ['一如继往', '一如既往'], ['一如即往', '一如既往'],
    ['甘败下风', '甘拜下风'], ['迫不急待', '迫不及待'],
    ['走头无路', '走投无路'], ['一愁莫展', '一筹莫展'],
    ['竭泽而鱼', '竭泽而渔'], ['世外桃园', '世外桃源'],
    ['鬼鬼崇崇', '鬼鬼祟祟'], ['声名狼籍', '声名狼藉'],
    ['装璜', '装潢'], ['峻工', '竣工'], ['按装', '安装'],
    ['布署', '部署'], ['侯选', '候选'], ['渡假村', '度假村'],
    ['名信片', '明信片'], ['天燃气', '天然气'],
    ['签定', '签订'], ['按排', '安排'], ['坐位', '座位'],
    ['帐号', '账号'], ['帐户', '账户'], ['帐单', '账单'], ['帐目', '账目'],
    ['转帐', '转账'], ['记帐', '记账'], ['付帐', '付账'], ['冲帐', '冲账'],
    ['查帐', '查账'], ['对帐', '对账'], ['结帐', '结账'],
    ['导至', '导致'], ['历害', '厉害'], ['年令', '年龄'],
    ['会仪', '会议'], ['告之', '告知'], ['领异', '领导'],
    ['检杳', '检查']
  ];
  var TYPO_PAIRS = (typeof TEXT_CHECKER_PAIRS !== 'undefined' &&
    TEXT_CHECKER_PAIRS && TEXT_CHECKER_PAIRS.length > 100)
    ? TEXT_CHECKER_PAIRS : BUILTIN_PAIRS;

  /* "以+动词"误写为"已"的缺失：以收到→已收到（这些动词前"以"必错） */
  var YI_VERBS = '收到|完成|发送|安排|处理|回复|确认|到场|到货|下单|付款|发货|签字|交付|验收|上线|下班|上班|到达|出发|提交|上报|传达|传达到|通知|做完|看完|做好|检查|核实|核对|补充|修改|更新|回复完毕|开会|离开|回来|备案|报备|归档|入库存|入库|出库|报废|领用|归还|借出|排产|投产|下线|调试|试制|定型|封样|整改|关闭';
  var YI_RE = new RegExp('以(' + YI_VERBS + ')', 'g');

  /* ============ 2. 重复字词 ============ */
  var DUP_RED = /(的的|了了|是是|在在)/g;
  var DUP_GRAY = new RegExp('([' + CJK + ']{2,})\\1', 'g');

  /* ============ 3. 标点规则 ============ */
  // 3.1 相邻重复标点（。，、；：？！相同且连续）
  var REPEAT_PUNCT = /([，。、；：？！])\1{1,}/g;
  // 3.2 中文字符旁的半角标点（排除数字小数点场景）
  var HALF_PUNCT_AFTER_CJK = new RegExp('([' + CJK + '])\\s*([,;:?!])', 'g');
  // 3.3 英文省略号
  var THREE_DOTS = /\.{3,}|。{3,}/g;
  // 3.4 标点前多余空格
  var SPACE_BEFORE_PUNCT = /[ \t]+([，。、；：？！）」』》,.:;?!])/g;
  // 3.5 汉字之间连续空格
  var MULTI_SPACE = new RegExp('([' + CJK + ']) {2,}([' + CJK + '])', 'g');

  /* ============ 主检查函数 ============ */
  function checkText(text) {
    var issues = [];
    if (!text || !text.trim()) {
      return { issues: [], corrected: text || '', stats: { red: 0, gray: 0 } };
    }

    function push(i, len, orig, sug, level, type, msg, fixable) {
      issues.push({ index: i, length: len, original: orig, suggestion: sug,
        level: level, type: type, message: msg, fixable: fixable });
    }

    var m, re;

    // A. 错别字对
    TYPO_PAIRS.forEach(function (pair) {
      if (pair[0] === pair[1]) return; // 跳过占位
      var idx = 0;
      while ((idx = text.indexOf(pair[0], idx)) !== -1) {
        push(idx, pair[0].length, pair[0], pair[1], 'red', '错别字',
          '「' + pair[0] + '」应为「' + pair[1] + '」', true);
        idx += pair[0].length;
      }
    });

    // B. 以→已（动词前）
    re = new RegExp(YI_RE.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index, m[0].length, m[0], '已' + m[1], 'red', '错别字',
        '「' + m[0] + '」应为「已' + m[1] + '」', true);
    }

    // C. 重复字词（红级）
    re = new RegExp(DUP_RED.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index, m[0].length, m[0], m[0][0] + (m[0].length > 2 ? m[0][1] : ''), 'red', '重复用字',
        '「' + m[0] + '」疑似多打了一个「' + m[0][0] + '」', true);
    }

    // D. 重复标点
    re = new RegExp(REPEAT_PUNCT.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index, m[0].length, m[0], m[0][0], 'gray', '标点重复',
        '「' + m[0] + '」重复标点，建议保留一个', true);
    }

    // E. 半角标点（中文语境）
    re = new RegExp(HALF_PUNCT_AFTER_CJK.source, 'g');
    var FULLMAP = { ',': '，', ';': '；', ':': '：', '?': '？', '!': '！' };
    while ((m = re.exec(text)) !== null) {
      push(m.index + m[1].length, m[2].length, m[2], FULLMAP[m[2]], 'gray', '标点混用',
        '中文语境建议使用全角「' + FULLMAP[m[2]] + '」', true);
    }

    // F. 英文省略号
    re = new RegExp(THREE_DOTS.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index, m[0].length, m[0], '……', 'gray', '标点规范',
        '省略号应使用「……」', true);
    }

    // G. 标点前空格
    re = new RegExp(SPACE_BEFORE_PUNCT.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index, m[0].length, m[0], m[1], 'gray', '多余空格',
        '标点前不应有空格', true);
    }

    // H. 汉字间连续空格
    re = new RegExp(MULTI_SPACE.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index + 1, m[0].length - 2, m[0].slice(1, -1), '', 'gray', '多余空格',
        '汉字之间连续空格，建议删除', true);
    }

    // I. 重复二字词（灰级，可能为叠词修辞，不自动修）
    re = new RegExp(DUP_GRAY.source, 'g');
    while ((m = re.exec(text)) !== null) {
      push(m.index, m[0].length, m[0], m[0].slice(0, m[0].length / 2), 'gray', '疑似重复',
        '「' + m[0] + '」疑似重复（如为刻意叠词可忽略）', false);
    }

    // J. 成对标点不闭合（灰级提示）
    var pairs = [['“', '”', '引号'], ['（', '）', '括号'], ['《', '》', '书名号'], ['（', '）', '括号']];
    var seen = {};
    pairs.forEach(function (p) {
      if (seen[p[0]]) return;
      seen[p[0]] = true;
      var open = (text.match(new RegExp('\\' + p[0], 'g')) || []).length;
      var close = (text.match(new RegExp('\\' + p[1], 'g')) || []).length;
      if (open !== close) {
        push(0, 0, p[0] + p[1], '', 'gray', '标点不闭合',
          p[2] + p[0] + p[1] + ' 数量不匹配（' + open + ' 个' + p[0] + '，' + close + ' 个' + p[1] + '），请检查', false);
      }
    });

    // 排序 + 去重叠（红优先）
    issues.sort(function (a, b) { return a.index - b.index || (a.level === 'red' ? -1 : 1); });
    var accepted = [], occupied = [];
    issues.forEach(function (iss) {
      if (iss.type === '标点不闭合') { accepted.push(iss); return; }
      var overlap = occupied.some(function (r) {
        return iss.index < r[1] && r[0] < iss.index + iss.length;
      });
      if (!overlap) {
        accepted.push(iss);
        if (iss.fixable) occupied.push([iss.index, iss.index + iss.length]);
      }
    });

    // 生成修正文本：从后往前应用可自动修复项
    var corrected = text;
    accepted.filter(function (i) { return i.fixable; })
      .sort(function (a, b) { return b.index - a.index; })
      .forEach(function (iss) {
        corrected = corrected.slice(0, iss.index) + iss.suggestion + corrected.slice(iss.index + iss.length);
      });

    var stats = {
      red: accepted.filter(function (i) { return i.level === 'red'; }).length,
      gray: accepted.filter(function (i) { return i.level === 'gray'; }).length
    };
    return { issues: accepted, corrected: corrected, stats: stats };
  }

  /* 暴露给测试的规则表（用于单测统计） */
  function getStats() {
    return { typoPairs: TYPO_PAIRS.filter(function (p) { return p[0] !== p[1]; }).length };
  }

  return { checkText: checkText, getStats: getStats };
}));
