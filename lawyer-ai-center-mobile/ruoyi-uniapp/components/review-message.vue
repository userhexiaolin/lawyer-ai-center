<template>
  <view><risk-result-card v-if="result" :result="result"/><template v-else><view :class="{'answer-collapsed':canCollapse&&!expanded}"><zero-markdown-view :markdown="answer.markdown"/></view><view v-if="answer.citations.length"><legal-citation v-for="(citation,index) in answer.citations" :key="index" :citation="citation"/></view><button v-if="canCollapse" class="answer-toggle" :aria-expanded="expanded" size="mini" @click="expanded=!expanded">{{expanded?'收起长回答':'展开完整回答'}}</button></template></view>
</template>
<script>
import RiskResultCard from './risk-result-card.vue';
import LegalCitation from './legal-citation.vue';
import {splitLegalContent} from '@/common/legal-citation';
import {parseReviewResult,looksLikeReview} from '@/common/review-result';
import {presentMessage} from '@/common/message-presentation';
export default {watch:{streaming(value){if(value)this.expanded=true}},components:{RiskResultCard,LegalCitation},props:{content:{type:String,default:''},streaming:{type:Boolean,default:false},initiallyExpanded:{type:Boolean,default:false}},data(){return{expanded:this.streaming||this.initiallyExpanded}},computed:{answer(){const source=splitLegalContent(this.content);const display=presentMessage(source.markdown,{streaming:this.streaming});return{...source,markdown:display.markdown,text:display.text}},readableLength(){return this.answer.text.length},canCollapse(){return !this.streaming&&this.readableLength>4000},result(){return parseReviewResult(this.content)},reviewLike(){return looksLikeReview(this.content)||this.streaming&&/^\s*(?:```json\s*)?\{\s*"summary"\s*:/.test(this.content)}}};
</script>
<style scoped>.answer-collapsed{max-height:760rpx;overflow:hidden}.answer-toggle{min-height:44px;color:var(--brand-link);background:transparent}.answer-toggle::after{border:0}.answer-toggle:focus-visible{outline:2px solid var(--brand-link)}</style>
