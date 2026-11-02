<template>
  <view>
    <button :disabled="disabled || picking" @click="choose">选择并上传模板原件</button>
    <view v-if="picking" class="file-wait"><text>正在选择或上传文件，当前编辑保留…</text><button size="mini" @click="cancel">停止等待</button></view>
    <!-- #ifdef APP-PLUS -->
    <view :upload-request="bridgeRequest" :change:upload-request="fileBridge.pick" />
    <!-- #endif -->
  </view>
</template>
<script>
import { validateTemplateFile, uploadTemplateFile, nativeUploadOptions, parseTemplateUpload } from '@/common/document-files'
export default {
  props: { type: {type:String,default:''}, disabled: Boolean },
  data() { return { picking:false, bridgeRequest:null } },
  watch: { picking(value) { this.$emit('busy',value) } },
  beforeDestroy() { this.cancel() },
  methods: {
    choose() {
      if (this.disabled || this.picking || !this.type) return
      this._owner = uni.getStorageSync('token'); this._selection = (this._selection || 0) + 1
      // #ifdef APP-PLUS
      try { this.bridgeRequest = { ...nativeUploadOptions(this.type), nonce: this._selection }; this.picking=true } catch(error) { this.$emit('error',error.message) }
      return
      // #endif
      const chooser = typeof uni.chooseMessageFile === 'function' ? uni.chooseMessageFile : uni.chooseFile
      if (typeof chooser !== 'function') { this.$emit('error','当前平台不支持选择文档，请使用已保存的模板或新增正文模板'); return }
      chooser({ count:1, type:'file', extension:['docx','doc','rtf','odt','pdf','txt','md'],
        success: result => { const file=(result.tempFiles || [])[0]; if(file) this.upload(file) },
        fail: error => { if(!/cancel/i.test(error.errMsg || '')) this.$emit('error','文件选择失败，请重试') } })
    },
    async upload(file) {
      const version=this._selection, owner=this._owner
      try {
        validateTemplateFile(file); this.picking=true
        const result=await uploadTemplateFile(file,this.type,task=>{this._task=task})
        if(version===this._selection && owner===uni.getStorageSync('token')) this.$emit('uploaded',result)
      } catch(error) { if(version===this._selection && owner===uni.getStorageSync('token')) this.$emit('error',error.message) }
      finally { if(version===this._selection) this.picking=false }
    },
    bridgeDone(response) {
      if(response.nonce!==this._selection || this._owner!==uni.getStorageSync('token')) return
      this.picking=false
      if(response.cancelled) return
      try { if(response.error) throw new Error(response.error); this.$emit('uploaded',parseTemplateUpload(response.data,response.status,this._owner)) }
      catch(error) { this.$emit('error',error.message) }
    },
    cancel() { this._selection=(this._selection || 0)+1; if(this._task && this._task.abort) this._task.abort(); this.picking=false; this.bridgeRequest={cancel:true,nonce:this._selection} }
  }
}
</script>
<!-- #ifdef APP-PLUS -->
<script module="fileBridge" lang="renderjs">
export default {
  methods: {
    pick(options, previous, owner) {
      if(this._controller) this._controller.abort()
      if(this._input) this._input.remove()
      if(!options || options.cancel) return
      const input=document.createElement('input'); this._input=input
      input.type='file'; input.accept='.docx,.doc,.rtf,.odt,.pdf,.txt,.md'; input.style.display='none'; document.body.appendChild(input)
      const finish=result=>{ input.remove(); owner.callMethod('bridgeDone',{nonce:options.nonce,...result}) }
      input.oncancel=()=>finish({cancelled:true})
      input.onchange=async()=>{
        const file=input.files && input.files[0]
        if(!file) { finish({cancelled:true}); return }
        if(!/\.(docx|doc|rtf|odt|pdf|txt|md)$/i.test(file.name) || file.size>options.maxBytes) { finish({error:'请选择不超过10MB的Word、PDF或正文文件'}); return }
        const body=new FormData(); body.append('file',file,file.name); body.append('type',options.type); body.append('parse','true')
        const controller=typeof AbortController==='function'?new AbortController():null; this._controller=controller
        const timer=controller?setTimeout(()=>controller.abort(),660000):null
        try { const response=await fetch(options.url,{method:'POST',headers:options.header,body,signal:controller?controller.signal:undefined}); finish({status:response.status,data:await response.text()}) }
        catch(error) { finish({error:'上传未完成，原编辑已保留，请重试'}) }
        finally { if(timer)clearTimeout(timer) }
      }
      input.click()
    }
  }
}
</script>
<!-- #endif -->
<style scoped>.file-wait{font-size:14px;color:var(--brand-muted);padding:18rpx 0}.file-wait button{margin-top:12rpx}</style>
