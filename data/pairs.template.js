/**
 * 错别字词对数据（构建产物，勿手改）
 * 来源：cuobiezi.net 词表 5 页 + 图书编校质量差错认定细则，经 56462 词词典双向校验
 * 格式：[错词, 正词]
 */
(function (root, factory) {
  var data = factory();
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = data;
  } else {
    root.TEXT_CHECKER_PAIRS = data;
  }
}(typeof self !== 'undefined' ? self : this, function () {
  return __PAIRS_PLACEHOLDER__;
}));
