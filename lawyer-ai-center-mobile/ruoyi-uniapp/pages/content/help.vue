<template><view class="help"><text class="title">与律行使用指南</text><view v-for="item in items" :key="item.title" class="card"><text class="heading">{{ item.title }}</text><text>{{ item.content }}</text></view></view></template>
<script>
export default { data() { return { items:[
  {title:'法律咨询',content:'从“咨询”开始，选择快捷问题或描述案情。咨询会话会保存，可从最近会话或“我的”继续。'},
  {title:'合同快审',content:'从“快审”进入拍照或相册选择，上传后核对识别文字。风险摘要支持展开建议，完整审查报告可在电脑端通过同一账号查看并下载。'},
  {title:'失败与重试',content:'上传或审查失败时按页面提示重试，检查网络和文件大小。没有收到完整结果时，可先从历史检查会话，避免重复提交。'},
  {title:'帮助与反馈',content:'遇到问题，请记录发生时间、页面名称与提示内容，联系项目管理员反馈。'}
] }; } };
</script>
<style scoped>.help{padding:30rpx;background:#f5f7f6;min-height:100vh;color:#203738}.title{display:block;font-size:20px;font-weight:600}.card{padding:28rpx;margin-top:24rpx;background:white;border-radius:18rpx;font-size:16px;line-height:1.9}.heading{display:block;font-size:16px;font-weight:600;margin-bottom:14rpx}</style>
