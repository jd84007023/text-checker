global.TEXT_CHECKER_PAIRS = require('./data/pairs.js');
var TC = require('./checker.js');

var pass = 0, fail = 0;
function t(name, text, expectRed, expectGrayMin, checkCorrected) {
  var r = TC.checkText(text);
  var okRed = r.stats.red === expectRed;
  var okGray = expectGrayMin === undefined ? true : r.stats.gray >= expectGrayMin;
  var okFix = checkCorrected === undefined ? true : r.corrected === checkCorrected;
  if (okRed && okGray && okFix) { pass++; console.log('PASS  ' + name); }
  else {
    fail++;
    console.log('FAIL  ' + name + '  red=' + r.stats.red + '/' + expectRed +
      ' gray=' + r.stats.gray + ' corrected="' + r.corrected + '"' +
      (checkCorrected !== undefined ? ' expected="' + checkCorrected + '"' : ''));
    r.issues.forEach(function (i) { console.log('      [' + i.level + '] ' + i.type + ' @' + i.index + ' "' + i.original + '" -> "' + i.suggestion + '"'); });
  }
}

console.log('词库错别字对数量:', TC.getStats().typoPairs);

// 大词库新增词对验证
t('词库:默守成规', '这就是典型的默守成规', 1, 0, '这就是典型的墨守成规');
t('词库:迫不急待', '大家迫不急待想看结果', 1, 0, '大家迫不及待想看结果');
t('词库:水笼头', '卫生间的水笼头坏了', 1, 0, '卫生间的水龙头坏了');
t('词库:宁缺勿滥', '宁缺勿滥', 1, 0, '宁缺毋滥') ;

// 红级：错别字
t('以经→已经', '报告以经交了', 1, 0, '报告已经交了');
t('以+动词→已', '货以收到，请确认', 1, 0, '货已收到，请确认');
t('做为→作为', '做为参考', 1, 0, '作为参考');
t('帐号→账号', '我的帐号出问题了', 1, 0, '我的账号出问题了');
t('在见→再见', '明天在见', 1, 0, '明天再见');
t('按装→安装', '设备按装完成', 1, 0, '设备安装完成');

// 红级：重复字
t('的的', '我说的的意思', 1, 0, '我说的意思');
t('了了', '我吃了了饭', 1, 0, '我吃了饭');

// 灰级：标点
t('重复标点', '好的。。收到', 0, 1, '好的。收到');
t('半角逗号', '今天开会,请准时', 0, 1, '今天开会，请准时');
t('省略号', '等一下...', 0, 1, '等一下……');
t('标点前空格', '请查收 ！', 0, 1, '请查收！');

// 灰级：疑似重复（不自动修）
t('疑似叠词提示', '这个需要检查检查', 0, 0); // 检查检查 是合法叠词，不报
t('真重复词', '我们我们明天出发', 0, 1, '我们我们明天出发');

// 混合场景
t('混合错误', '零件以经按装好了,图纸在见附件。。', 3, 2, '零件已经安装好了，图纸再见附件。');

// 正确文本：零红零灰
t('干净文本', '今天的生产计划已经安排好了，请各组按时到岗，有问题随时反馈。', 0, 0);
t('数字小数点不受影响', '扭矩设置为3.5N·m，温度25度。', 0, 0);

console.log('\n结果: ' + pass + ' 通过, ' + fail + ' 失败');
process.exit(fail > 0 ? 1 : 0);
