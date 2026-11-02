<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Alert, Button, Card, Radio, RadioGroup, Spin, Statistic } from 'ant-design-vue';
import { requestClient } from '#/api/request';
interface Metrics { from:string; until:string; timezone:string; newUsers:number; activeUsers:number; consultations:number; reviews:number; paidOrders:number; paidAmount:string; }
const days=ref(1), loading=ref(false), error=ref(''), metrics=ref<Metrics>();
let revision=0;
async function load(){const version=++revision;loading.value=true;error.value='';metrics.value=undefined;try{const result=await requestClient.get<Metrics>('/lawyer/metrics',{params:{days:days.value}});if(version===revision)metrics.value=result;}catch(e){if(version===revision)error.value=(e as Error).message||'统计暂不可用，仅管理员可以查看';}finally{if(version===revision)loading.value=false;}}
onMounted(load);
</script>
<template><main class="p-5"><Card title="产品使用统计"><RadioGroup v-model:value="days" button-style="solid" @change="load"><Radio :value="1">今日</Radio><Radio :value="7">近7日</Radio><Radio :value="30">近30日</Radio></RadioGroup><Button class="ml-4" :loading="loading" @click="load">刷新</Button><Alert v-if="error" class="mt-4" :message="error" type="warning" show-icon/><Spin :spinning="loading"><template v-if="metrics"><p class="my-4">{{metrics.from}} 至 {{metrics.until}} · 北京时间 · 当前租户</p><div class="metric-grid"><Card><Statistic title="新增用户" :value="metrics.newUsers"/></Card><Card><Statistic title="活跃用户" :value="metrics.activeUsers"/></Card><Card><Statistic title="咨询会话" :value="metrics.consultations"/></Card><Card><Statistic title="合同审查提交" :value="metrics.reviews"/></Card><Card><Statistic title="已付订单" :value="metrics.paidOrders"/></Card><Card><Statistic title="实付金额" :value="Number(metrics.paidAmount)" :precision="2" prefix="¥"/></Card></div><p class="mt-4 text-sm text-gray-500">活跃用户按发出消息或创建会话的用户去重；咨询按新建咨询会话计；审查按审查会话中用户提交消息计，含追问，不代表审查成功次数。支付指标按付款时间计，已退款订单排除；支付未开放时显示实际的零值。</p></template></Spin></Card></main></template>
<style scoped>.metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px}</style>
