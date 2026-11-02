<template>
  <view class="consultation-model-select">
    <button ref="trigger" class="consultation-model-trigger" tabindex="0" aria-label="切换模型" aria-haspopup="dialog" :aria-expanded="opened?'true':'false'" :disabled="disabled" @click="open">
      <text class="consultation-model-name">{{modelLabel(value)}}</text><text aria-hidden="true">⌄</text>
    </button>
    <view v-if="opened" class="consultation-model-layer">
      <view class="consultation-model-shade" aria-label="关闭模型选择" @click="close" @touchmove.stop.prevent/>
      <view class="consultation-model-panel" role="dialog" aria-modal="true" aria-label="选择咨询模型" tabindex="-1">
        <view class="consultation-model-heading"><text>选择模型</text><button class="consultation-model-close" tabindex="0" aria-label="关闭模型选择" @click="close">×</button></view>
        <text class="consultation-model-hint">切换本次对话使用的模型，不会清空已输入的问题。</text>
        <scroll-view class="consultation-model-options" scroll-y>
          <view v-if="loading" class="consultation-model-status" role="status">正在加载可用模型…</view>
          <view v-else-if="error" class="consultation-model-status consultation-model-error" role="alert"><text>{{error}}</text><button tabindex="0" @click="loadModels">重试加载</button></view>
          <template v-else>
            <view v-if="value&&!selectedAvailable" class="consultation-model-status consultation-model-error" role="alert">所选模型已不可用，请重新选择。</view>
            <button v-for="model in models" :key="model.id" class="consultation-model-option" tabindex="0" :aria-pressed="value&&value.id===model.id?'true':'false'" @click="choose(model)"><view class="consultation-model-details"><text>{{modelLabel(model)}}</text><text class="consultation-model-detail">{{model.modelName}}<template v-if="model.providerCode"> · {{model.providerCode}}</template></text></view><text v-if="value&&value.id===model.id" class="consultation-model-selected">已选择</text></button>
            <view v-if="!models.length" class="consultation-model-status"><text>暂无可用模型，请刷新列表或联系管理员。</text><button tabindex="0" @click="loadModels">刷新列表</button></view>
          </template>
        </scroll-view>
      </view>
    </view>
  </view>
</template>
<script>
import { consultationModelLabel, getConsultationModels } from '@/common/api/consultation-model'
export default {
  props:{value:{type:Object,default:null},disabled:{type:Boolean,default:false},active:{type:Boolean,default:true}},
  data(){return{opened:false,models:[],loading:false,error:''}},
  computed:{selectedAvailable(){return !this.value||this.models.some(item=>item.id===this.value.id)}},
  watch:{active(value){if(!value)this.close()},disabled(value){if(value)this.close()}},
  mounted(){
    // #ifdef H5
    this._keydown=event=>this.keydown(event);document.addEventListener('keydown',this._keydown)
    // #endif
  },
  beforeDestroy(){this._disposed=true;this.close()},
  methods:{
    modelLabel:consultationModelLabel,
    async open(){
      if(this.disabled||!this.active||this.opened)return
      if(!uni.getStorageSync('token')||!(uni.getStorageSync('userInfo')||{}).userId){uni.navigateTo({url:'/pages/h5/login'});return}
      this.opened=true;this.models=[];this.error=''
      // #ifdef H5
      this._previousFocus=document.activeElement;this._oldOverflow=document.body.style.overflow;document.body.style.overflow='hidden'
      this.$nextTick(()=>{const panel=this.$el.querySelector('.consultation-model-panel');if(panel)panel.focus()})
      // #endif
      await this.loadModels()
    },
    close(){
      this.opened=false;this._version=(this._version||0)+1;this.loading=false
      // #ifdef H5
      if(this._oldOverflow!==undefined){document.body.style.overflow=this._oldOverflow;this._oldOverflow=undefined}
      const focus=this._previousFocus;this._previousFocus=null
      if(!this._disposed)this.$nextTick(()=>{if(focus&&focus.isConnected)focus.focus()})
      if(this._disposed)document.removeEventListener('keydown',this._keydown)
      // #endif
    },
    async loadModels(){
      if(this.loading||!this.opened)return
      const version=this._version=(this._version||0)+1,token=uni.getStorageSync('token'),owner=String((uni.getStorageSync('userInfo')||{}).userId||'')
      const current=()=>!this._disposed&&this.opened&&version===this._version&&token===uni.getStorageSync('token')&&owner===String((uni.getStorageSync('userInfo')||{}).userId||'')
      this.loading=true;this.error='';this.models=[]
      try{const models=await getConsultationModels(this.$request);if(current()){this.models=models;if(!this.value&&models.length)this.$emit('input',models[0])}}
      catch(error){if(current())this.error=Number(error.code)===403?'当前账号无权选择模型，请联系管理员。':error.message||'模型列表加载失败，请检查网络后重试'}
      finally{if(current())this.loading=false}
    },
    choose(model){
      if(this.disabled||!this.opened)return
      if(!model||this.loading||this.error||!this.models.some(item=>item.id===model.id))return
      this.$emit('input',model);this.close()
    },
    keydown(event){
      // #ifdef H5
      if(!this.$el.contains(event.target))return
      if(this.opened&&event.key==='Escape'){event.preventDefault();this.close();return}
      if(this.opened&&event.key==='Tab'){
        const items=Array.from(this.$el.querySelectorAll('.consultation-model-panel [tabindex="0"]')).filter(el=>!el.hasAttribute('disabled')&&el.getClientRects().length)
        const at=items.indexOf(document.activeElement)
        if(event.shiftKey&&at<=0){event.preventDefault();if(items.length)items[items.length-1].focus()}
        else if(!event.shiftKey&&(at<0||at===items.length-1)){event.preventDefault();if(items.length)items[0].focus()}
      }
      // mobile-shell handles Enter/Space for uni-button to avoid duplicate activation.
      // #endif
    }
  }
}
</script>
<style lang="scss">
.consultation-model-select { min-width:0; max-width:100%; }
.consultation-model-trigger { display:flex; align-items:center; gap:8px; box-sizing:border-box; max-width:100%; min-height:44px; margin:0; padding:8px 10px; font-size:14px; line-height:1.5; text-align:left; color:var(--brand-text); background:transparent; border-radius:12px; }
.consultation-model-trigger::after,.consultation-model-panel button::after { border:0; }
.consultation-model-name { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.consultation-model-trigger[disabled] { color:var(--brand-muted); }
.consultation-model-layer { position:fixed; inset:0; z-index:10030; }
.consultation-model-shade { position:absolute; inset:0; background:rgba(0,0,0,.32); }
.consultation-model-panel { box-sizing:border-box; position:absolute; bottom:0; left:0; right:0; display:flex; flex-direction:column; width:100%; max-width:600px; max-height:75vh; margin:0 auto; padding:12px 16px calc(16px + env(safe-area-inset-bottom)); border-radius:20px 20px 0 0; color:var(--brand-text); background:var(--brand-surface); }
.consultation-model-heading { display:flex; align-items:center; justify-content:space-between; flex-shrink:0; font-size:18px; font-weight:600; }
.consultation-model-close { display:flex; align-items:center; justify-content:center; width:44px; height:44px; margin:0; padding:0; font-size:28px; line-height:1; background:transparent; }
.consultation-model-hint { flex-shrink:0; padding:0 0 12px; font-size:14px; line-height:1.5; color:var(--brand-muted); }
.consultation-model-options { flex:1; min-height:0; overflow:auto; }
.consultation-model-option { display:flex; align-items:center; justify-content:space-between; gap:12px; width:100%; min-height:56px; margin:4px 0; padding:12px; font-size:16px; line-height:1.5; text-align:left; background:transparent; border-radius:12px; }
.consultation-model-option[aria-pressed=true] { background:var(--brand-soft); }
.consultation-model-details { flex:1; min-width:0; overflow-wrap:anywhere; word-break:break-word; }
.consultation-model-details>text { display:block; }
.consultation-model-detail,.consultation-model-selected { font-size:14px; color:var(--brand-muted); }
.consultation-model-selected { flex-shrink:0; color:var(--brand-link); }
.consultation-model-status { padding:12px; font-size:14px; line-height:1.6; color:var(--brand-muted); }
.consultation-model-error { color:var(--brand-danger-text); }
.consultation-model-status button { width:100%; min-height:44px; margin:12px 0 0; padding:8px 12px; font-size:14px; line-height:1.5; background:var(--brand-background); }
/* #ifdef H5 */
.consultation-model-panel { max-height:75dvh; }
.consultation-model-select button:focus-visible { outline:2px solid var(--brand-link); outline-offset:2px; }
/* #endif */
</style>
