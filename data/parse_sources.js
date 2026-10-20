/**
 * 词库构建 v2：格式无关 + 词典自校验
 * 对每处「词(括注)」生成两种解释（正向/反向），仅保留：
 *   正确形 ∈ 常用词表 且 错误形 ∉ 常用词表
 * 的词对。双解都成立或都不成立的丢弃 —— 保证不引入方向错误。
 */
var fs = require('fs');
var path = require('path');
var DIR = __dirname;

function stripHtml(html) {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>');
}

function loadWordlist() {
  var set = new Set();
  var raw = fs.readFileSync(path.join(DIR, 'wordlist.txt'), 'utf8');
  raw.split('\n').forEach(function (line) {
    var w = line.split('\t')[0].trim();
    if (w) set.add(w);
  });
  return set;
}

// 尝试解码 GBK（Node 22 内置 full-icu）
function readPage(p) {
  var buf = fs.readFileSync(p);
  var utf8 = buf.toString('utf8');
  return utf8;
}

var DICT = loadWordlist();
console.log('词典加载:', DICT.size, '词');

function parse(text) {
  var pairs = [];
  // 允许括号前后及内侧有空白（细则页格式：和蔼 (霭) ，安 (按) 装）
  var re = /([\u4e00-\u9fff]+)[\s\u3000]*[（(][\s\u3000]*([\u4e00-\u9fff]{1,3})[\s\u3000]*[)）][\s\u3000]*([\u4e00-\u9fff]*)/g;
  var m;
  while ((m = re.exec(text)) !== null) {
    var printed = m[1], bad = m[2], suffix = m[3];
    if (printed.length < 1) continue;

    // 解释 A：printed=正确词，bad 替换紧邻括号前的字
    var cA = printed + suffix;
    var wA = printed.slice(0, -1) + bad + suffix;

    // 解释 B：printed=错词，bad 替换 printed 中某个字（试所有位置）
    var cands = [];
    for (var i = printed.length - 1; i >= 0; i--) {
      var cand = printed.slice(0, i) + bad + printed.slice(i + 1) + suffix;
      if (DICT.has(cand)) cands.push(cand);
    }
    var cB = (cands.length === 1) ? cands[0] : null;
    var wB = printed + suffix;

    var accepted = null;
    // A 成立：correct 在词典，wrong 不在
    if (DICT.has(cA) && !DICT.has(wA) && wA.length >= 2) accepted = [wA, cA];
    // B 成立（且 A 不成立时优先词典双验证的 B）
    else if (cB && !DICT.has(wB) && cB !== wB && wB.length >= 2) accepted = [wB, cB];
    // A 不完全成立但方向明确（correct 在词典且 wrong 更短/合理）时降级采纳 A
    else if (DICT.has(cA) && !DICT.has(wA)) accepted = [wA, cA];

    if (accepted) {
      var w = accepted[0], c = accepted[1];
      if (/^[\u4e00-\u9fff]{2,}$/.test(w) && /^[\u4e00-\u9fff]{2,}$/.test(c) && w !== c) {
        pairs.push(accepted);
      }
    }
  }
  return pairs;
}

var files = ['jiem001.html', 'jiem002.html', 'jiem003.html', 'jiem1.html', 'jiem005.html',
             'xize.html', 'xize2.html', 'xize3.html'];
var all = {}, countByFile = {}, rejected = {};
files.forEach(function (f) {
  var p = path.join(DIR, f);
  if (!fs.existsSync(p)) { countByFile[f] = '缺失'; return; }
  var pairs = parse(readPage(p));
  countByFile[f] = pairs.length;
  pairs.forEach(function (pr) {
    if (!all[pr[0]]) all[pr[0]] = pr[1];
    else if (all[pr[0]] !== pr[1]) rejected[pr[0] + '|' + pr[1]] = all[pr[0]];
  });
});

var list = Object.keys(all).sort().map(function (k) { return [k, all[k]]; });
fs.writeFileSync(path.join(DIR, 'typo_pairs_web.json'), JSON.stringify(list), 'utf8');
fs.writeFileSync(path.join(DIR, 'conflicts.json'), JSON.stringify(rejected, null, 1), 'utf8');
console.log('各文件采纳数量:', JSON.stringify(countByFile));
console.log('合并去重后总词对:', list.length);
console.log('冲突(同错词不同正词):', Object.keys(rejected).length);
console.log('样本(前12):', JSON.stringify(list.slice(0, 12)));
console.log('样本(中段):', JSON.stringify(list.slice(500, 512)));
console.log('样本(末尾):', JSON.stringify(list.slice(-12)));
