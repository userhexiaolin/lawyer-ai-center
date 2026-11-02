<template>
  <view class="review-card">
    <text :class="['badge',result.riskLevel]">{{ levels[result.riskLevel].label }}</text>
    <text v-if="result.contractType">合同类型：{{ result.contractType }}</text>
    <text class="summary">{{ result.summary }}</text>
    <view v-if="result.basicInfo.title || result.basicInfo.parties.length || result.basicInfo.signDate" class="basic">
      <text v-if="result.basicInfo.title">合同：{{ result.basicInfo.title }}</text>
      <text v-if="result.basicInfo.parties.length">当事人：{{ result.basicInfo.parties.join('、') }}</text>
      <text v-if="result.basicInfo.signDate">签署日期：{{ result.basicInfo.signDate }}</text>
    </view>
    <text class="section-title">风险点（{{ result.issues.length }}）</text>
    <view v-for="(risk,index) in shownRisks" :key="index" class="risk">
      <view class="risk-heading" @click="toggleRisk(index)"><text :class="['badge',risk.riskLevel]">{{ levels[risk.riskLevel].label }}</text><text class="risk-title">{{ risk.clauseTitle }}</text><text>{{ openRisks[index] ? '收起 −' : '展开 +' }}</text></view>
      <view v-if="openRisks[index]" class="details">
        <view v-for="field in detailFields" :key="field.key"><view v-if="risk[field.key]" class="field"><text class="label">{{ field.label }}</text><text>{{ short(risk[field.key],index+'-'+field.key) }}</text><text v-if="risk[field.key].length>320" class="toggle" @click="toggleText(index+'-'+field.key)">{{ openText[index+'-'+field.key] ? '收起文字' : '展开全文' }}</text></view></view>
        <legal-citation v-for="(citation,c) in risk.citations" :key="c" :citation="citation" />
        <view v-if="risk.legalBasis.length" class="field"><text class="label">法律依据</text><text v-for="(basis,b) in risk.legalBasis" :key="b" class="basis">{{ b+1 }}. {{ basis }}</text></view>
      </view>
    </view>
    <button v-if="result.issues.length>3" size="mini" @click="showAll=!showAll">{{ showAll ? '仅看前3条' : '展开全部风险（'+result.issues.length+'）' }}</button>
    <view v-if="result.missingClauses.length" class="field"><text class="section-title">缺失条款</text><text v-for="(clause,index) in result.missingClauses" :key="index" class="basis">{{ index+1 }}. {{ clause }}</text></view>
    <view v-if="result.overallSuggestion" class="field"><text class="section-title">整体建议</text><text>{{ short(result.overallSuggestion,'overall') }}</text><text v-if="result.overallSuggestion.length>320" class="toggle" @click="toggleText('overall')">{{ openText.overall ? '收起文字' : '展开全文' }}</text></view>
  </view>
</template>
<script>
import { riskPresentation } from '@/common/review-result';
import LegalCitation from './legal-citation.vue';
export default {
  components:{LegalCitation},
  props:{result:{type:Object,required:true}},
  data(){return {levels:riskPresentation,showAll:false,openRisks:{},openText:{},detailFields:[{key:'position',label:'条款位置'},{key:'quote',label:'原文摘录'},{key:'problem',label:'风险分析'},{key:'suggestion',label:'修改建议'}]};},
  computed:{shownRisks(){return this.showAll?this.result.issues:this.result.issues.slice(0,3);}},
  watch:{result(){this.openRisks={};this.openText={};this.showAll=false;}},
  methods:{toggleRisk(index){this.$set(this.openRisks,index,!this.openRisks[index]);},toggleText(key){this.$set(this.openText,key,!this.openText[key]);},short(value,key){return value.length>320&&!this.openText[key]?value.slice(0,320)+'…':value;}}
};
</script>
<style scoped>
.review-card{color:#303133;line-height:1.7;font-size:16px;word-break:break-word}.summary{display:block;font-weight:600;font-size:16px;margin:18rpx 0;white-space:pre-wrap}.basic{padding:18rpx;background:#f5f7fa;border-radius:12rpx}.basic text,.field>text,.basis{display:block;white-space:pre-wrap}.section-title,.label{display:block;font-weight:600;margin:18rpx 0 8rpx}.badge{display:inline-block;padding:2rpx 12rpx;border-radius:8rpx;background:#f4f4f5;color:var(--brand-muted);font-size:14px;flex-shrink:0}.badge.high{color:#b42318;background:#fef3f2}.badge.medium{color:#9a6700;background:#fff7e6}.badge.low{color:#337ecc;background:#ecf5ff}.risk{border-top:1px solid #e4e7ed;margin-top:16rpx;padding-top:16rpx}.risk-heading{display:flex;gap:12rpx;align-items:flex-start}.risk-title{flex:1;font-weight:600}.risk-heading>text:last-child{color:var(--brand-muted);font-size:14px;flex-shrink:0}.details{padding:0 8rpx}.toggle{color:#337ecc;font-size:14px;margin-top:8rpx}.basis{margin:8rpx 0}.review-card button{margin-top:20rpx;background:#f5f7fa;color:#303133}
</style>

<style scoped>.badge.high{color:var(--brand-danger);background:#FEF0F0}.badge.medium{color:var(--brand-warning);background:#FDF6EC}.badge.low{color:var(--brand-low);background:#EBF2FE}.toggle{color:var(--brand-link)}.basic{background:var(--brand-background)}.review-card{color:var(--brand-text)}</style>
