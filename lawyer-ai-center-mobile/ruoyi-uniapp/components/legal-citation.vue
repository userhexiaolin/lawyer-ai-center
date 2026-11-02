<template>
  <view v-if="reference" class="legal-reference">
    <view class="citation-link" @click="open"><text>{{ label(reference) }}</text><text v-if="warning(reference)" :class="['status',reference.status]">{{ warning(reference) }}</text></view>
    <view v-if="visible" class="citation-overlay" @click.self="visible=false">
      <view class="citation-panel"><button size="mini" @click="visible=false">关闭原文</button><scroll-view scroll-y class="citation-scroll"><text class="heading">{{ label(reference) }}</text><text v-if="warning(reference)" :class="['status',reference.status]">{{ warning(reference) }}</text><text v-if="loading">正在加载…</text><text v-if="error" class="status repealed">{{ error }}</text><button v-if="error" size="mini" @click="open">重试</button><template v-if="detail"><text class="heading">{{ detail.sourceName }}</text><text v-if="detail.reference && warning(detail.reference)" :class="['status',detail.reference.status]">{{ warning(detail.reference) }}</text><text class="source-content">{{ detail.content || '暂无原文，仅展示引用信息' }}</text></template></scroll-view></view>
    </view>
  </view>
  <text v-else>引用信息不完整</text>
</template>
<script>
import {normalizeCitation,citationLabel,citationWarning,citationTarget} from '@/common/legal-citation';
export default {
  props:{citation:{type:Object,required:true}},
  data(){return {visible:false,loading:false,error:'',detail:null};},
  computed:{reference(){return normalizeCitation(this.citation);}},
  methods:{label:citationLabel,warning:citationWarning,async open(){
    if(!this.reference)return;
    const target=citationTarget(this.reference);
    if(target.kind==='missing'){uni.showToast({title:'暂无原文，仅展示引用信息',icon:'none'});return;}
    if(target.kind==='source'){
      // #ifdef H5
      window.open(target.value,'_blank','noopener,noreferrer');
      return;
      // #endif
      // #ifndef H5
      uni.navigateTo({url:'/pages/legal/source?url='+encodeURIComponent(target.value)});
      return;
      // #endif
    }
    this.visible=true;this.loading=true;this.error='';this.detail=null;
    try{const result=await this.$request('/legal/citations/fragments/'+encodeURIComponent(target.value),'GET',{}, {'content-type':'application/json'});if(Number(result.code)!==200)throw Error(result.msg||'原文加载失败');this.detail=result.data;}
    catch(e){this.error=e.message||'原文加载失败，请重试';}finally{this.loading=false;}
  }}
};
</script>
<style scoped>
.legal-reference{display:inline-block;max-width:100%;margin:8rpx 8rpx 8rpx 0}.citation-link{padding:8rpx 12rpx;border:1px solid #b3cde5;background:#edf5ff;color:#245d91;border-radius:10rpx;line-height:1.7;font-size:16px;word-break:break-word}.status{display:inline-block;padding:0 8rpx;margin:4rpx;color:#964500;background:#fff0d6;font-weight:600}.repealed{color:#b42318;background:#fee4e2}.citation-overlay{position:fixed;inset:0;background:rgba(0,0,0,.4);z-index:9999;display:flex;align-items:flex-end}.citation-panel{box-sizing:border-box;width:100%;max-height:85vh;background:white;color:#303133;padding:24rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));border-radius:20rpx 20rpx 0 0}.citation-scroll{max-height:65vh}.heading{display:block;font-weight:600;margin:16rpx 0}.source-content{display:block;white-space:pre-wrap;word-break:break-word;font-size:16px;line-height:1.8}
</style>
