<template><view><web-view v-if="sourceUrl" :src="sourceUrl" /><text v-else>来源地址不可用</text></view></template>
<script>
export default {
  // uni-app injects route query keys (including url) as page props on H5.
  data() { return { sourceUrl: '' }; },
  onLoad(options = {}) {
    this.sourceUrl = '';
    let value = options.url || '';
    if (typeof value !== 'string') return;
    try { value = decodeURIComponent(value); } catch (_) { return; }
    if (/^https?:\/\/[^/\s?#]+(?:[/?#]|$)/i.test(value)) this.sourceUrl = value;
  }
};
</script>