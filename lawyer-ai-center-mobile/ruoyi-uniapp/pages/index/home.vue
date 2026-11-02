<template>
    <view class="quick-review" :style="{ paddingTop: topSpacing + 'px' }">
        <text class="review-eyebrow">与律行 · 合同快审</text><text class="review-title">签署之前，先看清风险</text>
        <text class="review-description">粘贴合同正文，由合同审查官梳理风险条款、修改建议和缺失约定。</text>
        <button class="review-button" @click="openImageReview">拍照合同 / 从相册选择</button>
        <text class="review-note">完整审查报告与修改建议请在电脑端查看</text>
        <case-association :key="$store.state.token" v-model="caseId" /><view class="review-card"><text class="field-title">合同正文</text><textarea v-model="contractText" class="contract-input" :maxlength="6000" placeholder="请粘贴需要审查的合同正文，建议一次审查一份合同。" :disabled="reviewBusy" placeholder-style="color:#606266;font-size:16px;" /><text class="word-count">{{ contractText.length }} / 6000</text></view>
        <view class="review-card"><text class="field-title">重点关注（选填）</text><input v-model="notes" class="notes-input" :maxlength="300" placeholder="例如：重点审查违约责任和管辖条款" :disabled="reviewBusy" placeholder-style="color:#606266;font-size:16px;" /></view>
        <view v-if="reviewError" class="review-error">{{ reviewError }}</view>
        <button class="review-button" :loading="reviewBusy" :disabled="reviewBusy" @click="startReview">{{ reviewBusy ? '正在准备审查' : '开始合同快审' }}</button>
        <text class="review-note">审查记录会保存在“工作台”的最近会话中，可随时继续对话。</text>
    </view>
</template>
<script>
import { createLegalSession, legalPageTop } from '@/common/api/legal-entry';
export default {
    data() { return { caseId: '', topSpacing: legalPageTop(uni), contractText: '', notes: '', reviewBusy: false, reviewError: '' }; },
    onShow() { this._reviewActive = true; this.topSpacing = legalPageTop(uni); },
    onHide() { this._reviewActive = false; },
    methods: {
        openImageReview() { uni.navigateTo({ url: '/pages/index/quick-review' }); },
        async startReview() {
            if (this.reviewBusy) return;
            if (!this.contractText.trim()) { uni.showToast({ title: '请先填写合同正文', icon: 'none' }); return; }
            if (!uni.getStorageSync('token') || !(uni.getStorageSync('userInfo') || {}).userId) { uni.navigateTo({ url: '/pages/h5/login' }); return; }
            this.reviewBusy = true; this.reviewError = '';
            try {
                const question = '请审查以下合同。审查重点：' + (this.notes.trim() || '全面审查合同风险') + '\n---合同正文开始---\n' + this.contractText.trim() + '\n---合同正文结束---';
                const entry = await createLegalSession(this.$request, uni, { agentName: '合同审查官', question, caseId: this.caseId });
                if (this._reviewActive === false) return;
                await new Promise((resolve, reject) => uni.navigateTo({ url: entry.url, success: resolve, fail: reject }));
            } catch (error) {
                if (this._reviewActive === false) return;
                this.reviewError = error.message || error.msg || '审查暂时无法打开，请稍后重试';
                uni.showToast({ title: this.reviewError, icon: 'none' });
            } finally { this.reviewBusy = false; }
        }
    }
};
</script>
<style scoped lang="scss">
.quick-review { box-sizing: border-box; min-height: 100vh; padding: calc(var(--status-bar-height, 0px) + 38rpx) 32rpx calc(40rpx + env(safe-area-inset-bottom)); background: var(--brand-background); color: var(--brand-text); }
/* #ifdef MP-WEIXIN */
.quick-review { padding-top: calc(var(--status-bar-height, 0px) + 92rpx); }
/* #endif */
.review-eyebrow, .review-title, .review-description, .review-note { display: block; }
.review-eyebrow { color: var(--brand-link); font-size:14px; }
.review-title { margin-top: 20rpx; font-size:24px; line-height: 1.5; font-weight: 600; }
.review-description { margin: 18rpx 0 30rpx; font-size:16px; line-height: 1.8; color: var(--brand-muted); }
.review-card { padding: 26rpx; margin-bottom: 22rpx; border-radius: 22rpx; background: #fff; }
.field-title { display: block; margin-bottom: 20rpx; font-size:16px; font-weight: 600; }
.contract-input { box-sizing: border-box; width: 100%; height: 380rpx; font-size:16px; line-height: 1.8; }
.word-count { display: block; text-align: right; margin-top: 18rpx; font-size:14px; color: var(--brand-muted); }
.notes-input { min-height: 54rpx; font-size:14px; }
.review-button { padding: 18rpx; margin-top: 30rpx; min-height: 92rpx; border-radius: 18rpx; background: var(--brand-primary); color: var(--brand-text); font-size:16px; line-height: 1.8; }
.review-button::after { border: none; }
.review-note { margin-top: 24rpx; color: var(--brand-muted); font-size:14px; line-height: 1.8; text-align: center; }
.review-error { color: var(--brand-danger); font-size:14px; line-height: 1.8; }
</style>

<style scoped lang="scss">
@import '@/common/form-controls.scss';
.notes-input { @include native-input-frame; } .contract-input { @include native-textarea-frame(220px); min-height:160px; }
</style>
