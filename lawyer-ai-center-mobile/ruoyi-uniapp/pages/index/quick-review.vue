<template>
    <view class="page">
        <text class="title">合同快速审查</text>
        <case-association :key="$store.state.token" v-model="caseId" :session-id="sessionId" :disabled="busy" /><text class="hint">上传合同后核对识别文字，再查看风险摘要。</text>
        <view class="actions"><button :disabled="busy" @click="choose('camera')">拍照合同</button><button :disabled="busy" @click="choose('album')">从相册选择</button></view>
        <image v-if="filePath" class="preview" :src="filePath" mode="aspectFit" />
        <text v-if="contractText" class="hint">请逐字核对识别正文，可直接修改。确认后点击下方「开始审查」。</text><button v-if="filePath && !contractText" :disabled="busy" @click="recognize">识别合同文字</button><textarea v-model="contractText" :disabled="busy" :maxlength="60000" placeholder="也可粘贴合同正文；识别失败时请在此补充" class="input" placeholder-style="color:#606266;font-size:16px;" />
        <input v-model="notes" :disabled="busy" :maxlength="300" placeholder="重点关注：违约责任、管辖、付款、解除等" class="notes" placeholder-style="color:#606266;font-size:16px;" />
        <text v-if="error" class="error">{{ error }}</text>
        <button :disabled="busy || !contractText.trim()" :loading="busy" type="primary" @click="start">{{ labels[stage] || '开始审查' }}</button>
        <view v-if="stage === 'complete'" class="result"><review-message :content="raw" /></view>
        <button v-if="sessionId && !busy" @click="openChat">结果已保存在会话 · 继续追问</button>
        <text class="footer">完整审查报告与修改建议请在电脑端查看</text>
    </view>
</template>
<script>
import ReviewMessage from '@/components/review-message.vue';
import { createQuickReview } from '@/common/quick-review';
import { riskPresentation } from '@/common/review-result';
export default {
  components: { ReviewMessage },
    data() { return { caseId: '', openingChat: false, filePath: '', fileUrl: '', contractText: '', notes: '', sessionId: '', stage: 'idle', error: '', raw: '', result: null, expanded: null,
        riskPresentation, labels: { uploading: '上传中', reading: '读取合同中', creating: '创建会话中', reviewing: '审查中' } }; },
    computed: { busy() { return this.openingChat || ['uploading', 'reading', 'creating', 'reviewing'].includes(this.stage); } },
    onLoad(options = {}) {
        this._workflow = createQuickReview(this, this.$request, uni);
        if (options.sessionId) this._workflow.restore(options.sessionId);
    },
    onUnload() { this._workflow.dispose(); },
    methods: {
        choose(source) {
            if (this.busy) return;
            uni.chooseImage({ count: 1, sourceType: [source], sizeType: ['compressed'], success: result => {
                const file = result.tempFiles[0];
                if (!file || !file.size || file.size > 10 * 1024 * 1024) { uni.showToast({ title: '请选择非空且不超过10MB的图片', icon: 'none' }); return; }
                this.filePath = result.tempFilePaths[0]; this.fileUrl = ''; this.sessionId = ''; this.contractText = ''; this.result = null; this.raw = ''; this.stage = 'idle'; this.error = '';this.recognize();
            }, fail: error => { if (!String(error.errMsg).includes('cancel')) uni.showToast({ title: '无法选择图片，请检查相机或相册权限', icon: 'none' }); } });
        },
        recognize() { return this._workflow.recognize(); },
        start() { return this._workflow.review(); },
        async openChat() {
            if (this.busy) return;
            this.openingChat = true;
            const token = uni.getStorageSync('token');
            try {
                const response = await this.$request('/agent/agent/agentOptions','GET',{}, {'content-type':'application/json'});
                if (uni.getStorageSync('token') !== token) return;
                const agent = response.code === 200 && Array.isArray(response.data) && response.data.find(a => a.agentName === '合同审查官' && String(a.status) === '0');
                if (!agent) throw new Error('合同审查官暂不可用，请稍后重试');
                uni.navigateTo({ url: '/pages/index/index?sessionId=' + encodeURIComponent(this.sessionId) + '&agentId=' + encodeURIComponent(agent.id) + '&appName=' + encodeURIComponent('合同审查官') });
            } catch (error) { uni.showToast({title:error.message || '会话暂时无法打开，请重试',icon:'none'}); }
            finally { this.openingChat = false; }
        }
    }
};
</script>
<style scoped>
.page { padding: 28rpx 32rpx calc(130rpx + env(safe-area-inset-bottom)); background: var(--brand-background); min-height: 100vh; box-sizing: border-box; color: var(--brand-text); }
.title { display:block; font-size:20px; font-weight:600; margin-bottom:20rpx; }.hint { display:block; color:var(--brand-muted); margin-bottom:24rpx; font-size:16px; }
.actions { display:flex; gap:16rpx; }.actions button { flex:1; font-size:16px; }.preview { width:100%; height:280rpx; margin-top:20rpx; }
.input,.notes { background:white; border-radius:16rpx; padding:22rpx; box-sizing:border-box; width:100%; margin:22rpx 0; }.input { height:260rpx; }.notes { height:88rpx; font-size:16px; }
.error { display:block; color:var(--brand-danger); margin:20rpx 0; }.result { background:white; padding:26rpx; border-radius:20rpx; margin:24rpx 0; }.summary,.suggestion { display:block; white-space:pre-wrap; line-height:1.8; margin:20rpx 0; }.risk { border-top:1px solid #eee; padding:22rpx 0; }.level { margin-right:16rpx; color:#42646d; }.high { color:#c73333; }.medium { color:#b57512; }.unknown { color:#7b678c; }
.footer { position:fixed; bottom:0; left:0; right:0; background:white; padding:20rpx 16rpx calc(20rpx + env(safe-area-inset-bottom)); text-align:center; font-size:14px; color:var(--brand-muted); }
</style>

<style scoped lang="scss">
@import '@/common/form-controls.scss';
.notes { @include native-input-frame; } .input { @include native-textarea-frame(200px); }
</style>
