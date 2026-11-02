<script setup lang="ts">
import { computed,ref,watch } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores';
const user=useUserStore(),router=useRouter(),open=ref(false),step=ref(0);
const steps=[{title:'开始法律咨询',body:'从应用市场选择法律咨询助手，描述案情、日期和诉求；同一会话可继续补充事实。',path:'/app-market'},{title:'上传并审查合同',body:'进入合同审查，上传PDF、Word或图片，核对正文后查看风险、原文摘录和修改建议。',path:'/contract-review'},{title:'建立个人案件台账',body:'在我的案件创建案件，关联咨询和审查，设置关键日期并记录工作时间。',path:'/my-cases'}];
const item=computed(()=>steps[step.value]!);
const owner=computed(()=>user.token&&user.userInfo?.userId?String(user.userInfo.userId):'');
const key=()=>`lawyer:onboarding:v1:${owner.value}`;
watch(owner,value=>{open.value=false;step.value=0;if(value)try{open.value=localStorage.getItem(key())!=='done';}catch{open.value=true;}},{immediate:true});
function done(){if(owner.value)try{localStorage.setItem(key(),'done');}catch{}open.value=false;}
function next(){if(step.value<2)step.value++;else done();}
function go(){const path=item.value.path;done();void router.push(path);}
</script>
<template><el-dialog v-model="open" title="欢迎使用与律行" width="min(500px, 92vw)" :close-on-click-modal="false" :before-close="done"><p>第 {{step+1}} / 3 步</p><h3>{{item.title}}</h3><p class="guide-body">{{item.body}}</p><template #footer><el-button @click="done">跳过</el-button><el-button @click="go">前往体验</el-button><el-button type="primary" @click="next">{{step===2?'开始使用':'下一步'}}</el-button></template></el-dialog></template>
<style scoped>.guide-body{line-height:1.9;font-size:14px}</style>
