/** 构建词库数据文件：合并网页词对 + 手工精选词对 → data/pairs.js */
var fs = require('fs');
var path = require('path');
var DIR = __dirname;

var web = JSON.parse(fs.readFileSync(path.join(DIR, 'typo_pairs_web.json'), 'utf8'));

// 手工精选（工作场景高频，优先级高于网页来源）
var curated = [
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
  ['检杳', '检查'], ['宁缺勿滥', '宁缺毋滥']
];

// 合并：curated 优先，web 去重
var map = {};
web.forEach(function (p) { map[p[0]] = p[1]; });
curated.forEach(function (p) { map[p[0]] = p[1]; });
var list = Object.keys(map).sort().map(function (k) { return [k, map[k]]; });

// 安全校验：错词不得等于正词，且无重复
var seen = new Set();
var clean = list.filter(function (p) {
  if (p[0] === p[1] || seen.has(p[0])) return false;
  seen.add(p[0]);
  return true;
});

fs.writeFileSync(path.join(DIR, 'pairs.js'),
  fs.readFileSync(path.join(DIR, 'pairs.template.js'), 'utf8')
    .replace('__PAIRS_PLACEHOLDER__', JSON.stringify(clean)),
  'utf8');
console.log('pairs.js 生成完成，词对总数:', clean.length, '（网页', web.length, '+ 手工', curated.length, '，去重后', clean.length, '）');
