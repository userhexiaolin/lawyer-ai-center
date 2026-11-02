<template>
  <mobile-shell ref="shell" :title="appName||'法律咨询'" current="consult" :session-id="sessionId" :busy="loading||sending" @navigate="navigateSection" @new-consult="navigateSection('consult')">
	<view class="content">
		<view class="body"><button class="chat-context-toggle" @click="contextOpen=!contextOpen">{{contextOpen?'收起案件与计时':'案件与计时'}}</button><view v-if="contextOpen"><work-timer :key="$store.state.token" :session-id="sessionId" /><case-association :key="$store.state.token" :session-id="sessionId" /></view>
			<view v-if="historyLoading" class="history-status">
				<u-loading-icon :show="true" size="18"></u-loading-icon>
				<text>{{ historyLoaded ? '正在加载更早的消息...' : '正在加载历史消息...' }}</text>
			</view>
			<view v-else-if="historyError" class="history-status history-action" @click="retryHistory">{{ historyError }}</view>
			<view v-else-if="hasMoreHistory" class="history-status history-action" @click="loadMoreHistory">加载更早消息</view>
			<view v-else-if="historyLoaded && historyCount > 0" class="history-status">已显示全部历史消息</view>
			<view v-if="historyLoaded && !arr.length" class="history-status">暂无历史消息，可以开始对话</view>
			<view v-for="(item, index) in arr" :key="item.message_id" :id="'message' + item.message_id">
				<view class="body_l" v-if="item.role == 'assistant'">
					<view class="body_lpic a">
						<image src="/static/brand/logo-transparent.png" mode=""></image>
					</view>
					<view class="body_box">
						<!-- 助手消息时间 - 左对齐，在气泡上方 -->
						<view v-if="item.createTime" class="message-time message-time-left">{{ formatTime(item.createTime) }}</view>
						<view class="body_jt">
							<u-icon name="play-left-fill" color="#F6F7FB" size="20"></u-icon>
						</view>
						<view class="body_rcon pdg" :class="{'error-message': item.isError}">
							<review-message :content="item.content" :initially-expanded="!!item.streamed" />
						</view>
						<view class="message-actions">
              <text class="answer-notice">AI 生成，请核对</text>
              <button v-if="item.content" class="message-copy" role="button" tabindex="0" @keydown.enter.prevent="copy(item)" @keydown.space.prevent="copy(item)" :aria-disabled="!!copyingMessageId" :disabled="!!copyingMessageId" :loading="copyingMessageId===String(item.message_id)" @click="copy(item)">{{copiedMessageId===String(item.message_id)?'已复制':'复制回答'}}</button>
            </view>
					</view>
				</view>
				<view class="body_r" v-if="item.role == 'user'">
					<view class="body_box">
						<!-- 用户消息时间 - 右对齐，在气泡上方 -->
						<view v-if="item.createTime" class="message-time message-time-right">{{ formatTime(item.createTime) }}</view>
						<view class="body_rjt">
							<u-icon name="play-right-fill" color="#F6F7FB" size="20"></u-icon>
						</view>
						<view class="body_rcon pdg">
							<!-- 显示文本 -->
							<zero-markdown-view v-if="item.content && item.type !== 'audio'" :markdown="messageDisplay(item.content,'user').markdown"></zero-markdown-view>
						</view>
            <view class="message-actions message-actions-user"><button v-if="item.content" class="message-copy" role="button" tabindex="0" @keydown.enter.prevent="copy(item)" @keydown.space.prevent="copy(item)" :aria-disabled="!!copyingMessageId" :disabled="!!copyingMessageId" :loading="copyingMessageId===String(item.message_id)" @click="copy(item)">{{copiedMessageId===String(item.message_id)?'已复制':'复制提问'}}</button></view>
					</view>
					<view class="body_rpic a">
						<image src="/static/head.png" mode="aspectFill"></image>
					</view>
				</view>
			</view>
		</view>
		<view class="body_l" v-if="loading" style="margin-left: 30rpx;" id="loading">
			<view class="body_lpic a">
				<image src="/static/brand/logo-transparent.png" mode=""></image>
			</view>
			<view class="body_box ">
				<view class="body_lcon pdg" style="display: flex;overflow-x: hidden;">
					<u-loading-icon :show="loading"></u-loading-icon>
					<view v-if="!typeingText" style="padding: 30rpx;">思考中...</view>
					<review-message v-else style="padding: 0 20rpx;" :content="typeingText" :streaming="true" />
				</view>
			</view>
		</view>

        <view class="chat-compose-area">
          <button v-if="!followLatest && arr.length" class="return-latest" role="button" tabindex="0" @keydown.enter.prevent="scrollReplyToLatest(true)" @keydown.space.prevent="scrollReplyToLatest(true)" @click="scrollReplyToLatest(true)">{{loading?'查看新回复 ↓':'回到最新 ↓'}}</button>
          <view class="flooer consultation-composer">
            <textarea v-model="prompt" class="composer-input" auto-height maxlength="20000" placeholder="输入问题或描述需要协助的事情…" aria-label="咨询问题" :show-confirm-bar="false" :cursor-spacing="20" placeholder-style="color:#606266;font-size:16px;" @linechange="composerInputHeight=Math.min(180,Math.max(64,$event.detail.height||64))"/>
            <view class="composer-toolbar">
              <consultation-model-select ref="modelSelect" v-if="appName==='法律咨询助手'" class="chat-model-select" :value="consultationModel" :disabled="loading||sending" @input="changeConsultationModel"/>
              <view v-else class="composer-toolbar-space"/>
              <text v-if="prompt.length>18000" class="composer-limit">{{prompt.length}} / 20000</text>
              <button class="flooer_r composer-send" role="button" tabindex="0" aria-label="发送问题" :loading="sending" :disabled="loading||sending||!prompt.trim()" @click="sendout"><text v-if="!sending" aria-hidden="true">↑</text></button>
            </view>
          </view>
          <text class="composer-footnote">AI 内容需人工核对</text>
        </view>
        <view id="chat-bottom" :style="{height:'calc('+(composerInputHeight+108)+'px + env(safe-area-inset-bottom))'}"/>
	</view>
  </mobile-shell>
</template>

<script>
import MobileShell from '@/components/mobile-shell.vue';
import ConsultationModelSelect from '@/components/consultation-model-select.vue';
import { normalizeConsultationModel, requireConsultationModel } from '@/common/api/consultation-model';
import { openMobileSection } from '@/common/mobile-navigation';
import { confirmAction } from '@/common/document-workflow';
import ReviewMessage from '@/components/review-message.vue';
import {presentMessage} from '@/common/message-presentation';
import {messageCopyText} from '@/common/message-copy';
	import setting from "@/common/config";
	import { uploadFile } from "@/common/upload";
	import { createSession, getSessionMessages, normalizeSessionId, compareMessageIds } from "@/common/api/session";
	import { rememberLegalContext, getLegalContext, consumeLegalFirstMessage } from "@/common/api/legal-entry";
	//markdown相关插件
	// import markdownFunc from '@/uni_modules/jo-markdown/components/jo-markdown/index.js';
	// #ifdef H5

	// 引入Recorder.js核心库和mp3编码器
	import Recorder from 'recorder-core'
	import 'recorder-core/src/engine/mp3'
	import 'recorder-core/src/engine/mp3-engine'

	// 可选：引入波形可视化插件
	// import 'recorder-core/src/extensions/waveview'

	// #endif
	import {
		mapState
	} from "vuex"
	import alert from "../../uni_modules/uview-ui/libs/config/props/alert";
	var that;
	export default {
  components: { ReviewMessage,MobileShell,ConsultationModelSelect },
		data() {
			return {
                consultationModel:null, contextOpen:false, copiedMessageId:'', copyingMessageId:'', followLatest:true,
				prompt: '',
                composerInputHeight: 64,
				arr: [],
				historyLoading: false,
				historyLoaded: false,
				historyError: '',
				historyPage: 0,
				historyPageSize: 20,
				historyCount: 0,
				hasMoreHistory: false,
				timer: null,
				loading: false,
				chat_id: '',
				sessionId: '', // 后端持久化的会话 ID，与每条消息的 chatId 无关
				sessionInitialized: false,
				sending: false,
				socketReady: false,
				msgId: '',
				typeingText: "",
				typeingId: "",
				now_model: {},
				quickReplies: [], // 快捷回复选项
				appId: '', // 应用ID
				appName: '', // 应用名称
				appDesc: '', // 应用描述
				agentId: '', // 智能体ID（专业助理进入时携带）
				systemPrompt: '', // 系统提示词（创作大师/首页问题进入时可携带）
				autoSendPending: false, // 待自动发送（首页问题带入 inputText+autoSend 时触发）
				uploading: false,
				selectedImage: '', // 选中的图片临时路径
				currentChatId: '', // 当前对话的 chatId（一问一答使用相同的 chatId）
				isRecording: false, // 是否正在录音
				showRecordModal: false, // 显示录音弹窗
				recordDuration: 0, // 录音时长
				recordTimer: null, // 录音计时器
				recorderManager: null, // 录音管理器
				innerAudioContext: null, // 音频播放器
				// #ifdef H5
				h5Recorder: null, // H5录音器实例
				h5RecordStartTime: 0, // 录音开始时间戳
				h5RecordOpened: false, // 录音是否已打开
				// #endif
			}
		},
		computed: {
			...mapState(['config', 'num', 'userInfo'])
		},
		async onLoad(options = {}) {
			this._pageClosed = false;
            this._lastScrollTop = 0;
            this._copyGeneration = 0;
			this._sessionOptions = options;
		    let that = this;
			let userInfo = uni.getStorageSync('userInfo') || {}
			that.msgId = userInfo.userId

			// 接收应用ID和名称
			if (options.appId) {
				that.appId = options.appId
			}
			if (options.appName) {
				that.appName = decodeURIComponent(options.appName)
			}
			// 智能体ID（专业助理进入时携带）
			if (options.agentId) {
				that.agentId = options.agentId
			}
			// 系统提示词（创作大师/首页问题进入时可携带）
			if (options.systemPrompt) {
				that.systemPrompt = decodeURIComponent(options.systemPrompt)
			}
			// 首页问题带入的输入文本
			if (options.inputText) {
				that.prompt = decodeURIComponent(options.inputText)
			}
			// 是否进入后自动发送（首页问题点击）
			if (['true', '1', true].includes(options.autoSend) && that.prompt) {
				that.autoSendPending = true
			}

            // First questions are handed off once per user/session, without putting long text in URLs.
            if (options.firstMessage === '1') {
                const first = consumeLegalFirstMessage(uni, that.msgId, options.sessionId, options.agentId);
                if (first) { that.prompt = first; that.autoSendPending = true; }
            }

			uni.setNavigationBarTitle({
				title: that.appName || "与律行"
			})

			// 默认欢迎消息
			that.arr.push({
				role: 'assistant',
				content: that.appName === '合同审查官' ? '您好，我是合同审查官。请提供合同正文和您重点关注的条款，我会协助梳理风险与修改建议。' : '您好，我是您的法律咨询助手。请描述事情经过、发生时间及您的诉求，我会为您梳理法律分析、风险提示和下一步建议。',
				message_id: 'welcome'
			})

			try {
				await this.ensureSession();
				this.connectChatSocket();
			} catch (error) {
				if (!this._pageClosed) this.$failToast(error.message || error.msg || '会话初始化失败，请重试');
			}
		},
		onBackPress(){if(this.$refs.modelSelect&&this.$refs.modelSelect.opened){this.$refs.modelSelect.close();return true}if(this.$refs.shell&&this.$refs.shell.drawerOpen){this.$refs.shell.closeDrawer();return true}return false},
        onPageScroll(event) {
            const top = Number(event.scrollTop) || 0;
            if (top < (this._lastScrollTop || 0) - 8) {
                this.followLatest = false;
                clearTimeout(this._replyScrollTimer);
                this._replyScrollTimer = null;
            }
            this._lastScrollTop = top;
            if (this._scrollMeasureTimer || typeof uni.createSelectorQuery !== 'function') return;
            this._scrollMeasureTimer = setTimeout(() => {
                this._scrollMeasureTimer = null;
                if (this._pageClosed) return;
                const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : typeof uni.getSystemInfoSync === 'function' ? uni.getSystemInfoSync() : {};
                uni.createSelectorQuery().in(this).select('#chat-bottom').boundingClientRect(rect => {
                    if (!this._pageClosed && rect && info.windowHeight > 0) this.followLatest = rect.top <= info.windowHeight + 64;
                }).exec();
            }, 120);
        },
		onUnload() {
			this._pageClosed = true;
            this._copyGeneration = (this._copyGeneration || 0) + 1;
            clearTimeout(this._copyFeedbackTimer);
            clearTimeout(this._replyScrollTimer);
            clearTimeout(this._scrollMeasureTimer);
			this.socketReady = false;
			if (this._chatSocket) this._chatSocket.close();
			this._chatSocket = null;
		},
		onHide() { if (this.$refs.modelSelect) this.$refs.modelSelect.close(); },
		onShow() {
			that = this;
			if (this.sessionInitialized) {
				if (!this.historyLoaded) this.getMessages();
				if (!this._chatSocket) this.connectChatSocket();
			}
		},
		methods: {
            messageDisplay(content,role='assistant'){return presentMessage(content,{role})},
            async navigateSection(section){if((this.loading||this.sending)&&!await confirmAction(uni,'离开当前咨询？','正在生成的回复将停止在本页显示，已保存内容可从历史继续。','离开咨询'))return;if(section==='review'||section==='cases'){uni.navigateTo({url:section==='review'?'/pages/index/home':'/pages/cases/index'});return}openMobileSection(uni,section)},
            scrollReplyToLatest(force = false) {
                if (this._pageClosed) return;
                if (force) this.followLatest = true;
                if (!this.followLatest || this._replyScrollTimer) return;
                this._replyScrollTimer = setTimeout(() => {
                    this._replyScrollTimer = null;
                    this.$nextTick(() => {
                        if (!this._pageClosed && this.followLatest) uni.pageScrollTo({selector:'#chat-bottom', duration:0});
                    });
                }, 100);
            },
			// 缓存按登录用户隔离，路由 sessionId 优先。
			sessionCacheKey() {
				return 'mobileChatSession:' + this.msgId;
			},
			assertSessionUser() {
				const user = uni.getStorageSync('userInfo') || {};
				if (!this.msgId || !uni.getStorageSync('token') || String(user.userId) !== String(this.msgId)) {
					throw new Error('登录用户已变化，请重新进入聊天');
				}
			},
			persistSession() {
				rememberLegalContext(uni, this.msgId, this.sessionId, { agentId: this.agentId, appName: this.appName, consultationModel: this.consultationModel });
				uni.setStorageSync(this.sessionCacheKey(), {
					sessionId: this.sessionId, appId: this.appId, appName: this.appName,
					agentId: this.agentId, systemPrompt: this.systemPrompt, consultationModel: this.appName === '法律咨询助手' ? normalizeConsultationModel(this.consultationModel) : null
				});
				// H5 刷新恢复当前会话，并移除一次性的自动发送参数。
				// #ifdef H5
				if (typeof window !== 'undefined' && window.history && window.location) {
					const url = new URL(window.location.href);
					const hashRoute = url.hash.indexOf('/pages/index/index') >= 0;
					const route = hashRoute ? new URL(url.hash.slice(1), url.origin) : url;
					route.searchParams.set('sessionId', this.sessionId);
					['newSession', 'autoSend', 'inputText', 'firstMessage'].forEach(key => route.searchParams.delete(key));
					if (hashRoute) url.hash = route.pathname + route.search;
					window.history.replaceState(window.history.state, '', url.toString());
				}
				// #endif
			},
			async ensureSession() {
				this.assertSessionUser();
				if (this._pageClosed) throw new Error('聊天页面已关闭');
				if (this.sessionInitialized) return this.sessionId;
				// 自动发送与手动发送共用同一次初始化，避免重复创建会话。
				if (!this._sessionPromise) {
					this._sessionPromise = this.initializeSession().finally(() => { this._sessionPromise = null; });
				}
				return this._sessionPromise;
			},
			async initializeSession() {
				const options = this._sessionOptions || {};
				const newSession = options.newSession === '1' || options.newSession === 'true';
				const cached = uni.getStorageSync(this.sessionCacheKey()) || {};
				const hasRouteId = Object.prototype.hasOwnProperty.call(options, 'sessionId');
				let id = '';
				if (hasRouteId) {
					id = normalizeSessionId(options.sessionId);
					if (!id) throw new Error('会话 ID 无效，请从历史会话重新进入');
				} else if (!newSession) {
					const sameContext = ['appId', 'agentId', 'systemPrompt'].every(key =>
						options[key] === undefined || String(this[key] || '') === String(cached[key] || ''));
					if (sameContext) id = normalizeSessionId(cached.sessionId);
				}
				const restoring = !!id;
				if (restoring && id === normalizeSessionId(cached.sessionId)) {
					['appId', 'appName', 'agentId', 'systemPrompt'].forEach(key => {
						if (options[key] === undefined) this[key] = cached[key] || '';
					});
				}
                if (restoring) {
                    const context = getLegalContext(uni, this.msgId, id);
                    if (context) ['agentId', 'appName'].forEach(key => {
                        if (options[key] === undefined) this[key] = context[key];
                    });
                    if (this.appName === '法律咨询助手') this.consultationModel = normalizeConsultationModel(context ? context.consultationModel : id === normalizeSessionId(cached.sessionId) ? cached.consultationModel : null);
                }

				if (!id) {
					if (newSession) uni.removeStorageSync(this.sessionCacheKey());
					id = await createSession(this.$request, {
						reviewSession: this.appName === '合同审查官',
						sessionTitle: (this.appName || this.prompt || '新对话').slice(0, 50),
						sessionContent: ''
					});
				}
				this.assertSessionUser();
				if (this._pageClosed) throw new Error('聊天页面已关闭');
				this.sessionId = id;
				this.persistSession();
				uni.setNavigationBarTitle({ title: this.appName || '与律行' });
				if (restoring) {
					this.arr = [];
					await this.getMessages();
				} else {
					this.historyLoaded = true;
				}
				if (this._pageClosed) throw new Error('聊天页面已关闭');
				this.sessionInitialized = true;
				return id;
			},
			changeConsultationModel(model) {
                if (this.loading || this.sending || this.appName !== '法律咨询助手') return;
                this.consultationModel = normalizeConsultationModel(model);
                if (this.sessionInitialized) this.persistSession();
            },
			async sendChatMessage(message, displayMessage) {
				if (this.sending || this.loading) return false;
				this.sending = true;
				const localId = 'local-' + message.chatId + '-user';
				try {
					const sessionId = await this.ensureSession();
					if (!this.historyLoaded && !await this.getMessages()) {
						throw new Error('请先重试加载历史消息');
					}
					this.assertSessionUser();
					if (!this.socketReady || !this._chatSocket) {
						this.connectChatSocket();
						throw new Error('聊天连接尚未就绪，请稍后重试');
					}
					const token = uni.getStorageSync('token');
                    const selectedModel = this.appName === '法律咨询助手' ? await requireConsultationModel(this.$request, this.consultationModel) : null;
                    this.assertSessionUser();
                    if (this._pageClosed || token !== uni.getStorageSync('token')) throw new Error('会话已变化，请重新进入咨询');
                    const payload = { ...message, sessionId };
                    if (selectedModel) payload.consultationModelId = selectedModel.id;
					if (this.agentId) payload.agentId = this.agentId;
					if (this.systemPrompt) payload.systemPrompt = this.systemPrompt;
					this.loading = true;
					this.typeingText = '';
                    this.followLatest = true;
					this._activeChatId = message.chatId;
					this.arr = this.arr.filter(item => item.message_id !== 'welcome');
					this.arr.push({ ...displayMessage, message_id: localId });
					await new Promise((resolve, reject) => this._chatSocket.send({
						data: JSON.stringify(payload), success: resolve, fail: reject
					}));
					return true;
				} catch (error) {
					this.arr = this.arr.filter(item => item.message_id !== localId);
					this.loading = false;
					if (!this._pageClosed) this.$failToast(error.message || error.msg || error.errMsg || '发送失败，请重试');
					return false;
				} finally {
					this.sending = false;
				}
			},
			connectChatSocket() {
				const that = this;
				const token = uni.getStorageSync('token');
				const userInfo = uni.getStorageSync('userInfo') || {};
				if (this._pageClosed || this._chatSocket) return;

				// 构建 WebSocket URL 参数（只添加有值的参数，避免空字符串编码问题）
				const wsParams = [];
				wsParams.push("Authorization=Bearer " + encodeURIComponent(token));

				// 应用ID（重要：传递给后端）
				if (that.appId) {
					wsParams.push("appId=" + that.appId);
				}

				// 用户ID
				if (userInfo.userId) {
					wsParams.push("userId=" + userInfo.userId);
				}

				const wsUrl = setting.ws + "?" + wsParams.join("&");

				const socket = uni.connectSocket({
					url: wsUrl,
					complete() {}
				});
				this._chatSocket = socket;
				socket.onOpen(function(res) {
					if (that._pageClosed || that._chatSocket !== socket) return;
					that.socketReady = true;
					// 待自动发送（首页问题带入 inputText+autoSend）
					if (that.autoSendPending) {
						that.autoSendPending = false;
						that.$nextTick(() => that.sendout());
					}
				});
				socket.onError(function(res) {
					if (that._chatSocket !== socket) return;
					that.socketReady = false;
					that.loading = false;
					that._chatSocket = null;
					socket.close();
				});
				socket.onMessage(function(res) {
					if (that._pageClosed || that._chatSocket !== socket) return;

					// 处理结束标记
					if (res.data == "[DONE]") {
						if (!that.loading) return;
						that.loading = false;
						that.arr.push({
							role: 'assistant',
							content: that.typeingText,
							message_id: 'local-' + that._activeChatId + '-assistant',
                            streamed: true,
							createTime: new Date().getTime()
						});
						that.scrollReplyToLatest();
						// 保存回复信息（由后端管理，前端不再主动保存）
						// that.sub(that.typeingText, "assistant");
						that.typeingText = '';
						return;
					}

					// 忽略空消息或空白消息
					if (!res.data || res.data.trim() === '') {
						return;
					}

					// 检查是否是纯文本错误消息（不是JSON格式）
					if (typeof res.data === 'string' && res.data.trim().startsWith('错误')) {
						that.loading = false;
						// 显示错误消息
						that.arr.push({
							role: 'assistant',
							content: res.data,
							isError: true,
							message_id: 'error-' + that.generateUUID()
						});
						that.scrollReplyToLatest();
						// 提示用户
						uni.showToast({
							title: res.data,
							icon: 'none',
							duration: 3000
						});
						return;
					}

					try {
						let json = JSON.parse(res.data)

						// 检查是否是JSON格式的错误消息
						if (json.data && typeof json.data === 'string' && json.data.includes('错误')) {
							that.loading = false;
							// 显示错误消息
							that.arr.push({
								role: 'assistant',
								content: json.data,
								isError: true,
								message_id: 'error-' + that.generateUUID()
							});
							that.scrollReplyToLatest();
							// 提示用户
							uni.showToast({
								title: json.data,
								icon: 'none',
								duration: 3000
							});
							return;
						}

						// 正常的流式响应
						if (json.content) {
							that.typeingText += json.content
						}

						that.scrollReplyToLatest();
					} catch (error) {
						// JSON解析失败，静默忽略（可能是空内容或不完整的JSON）
						// 只在非空且非空白的情况下才记录错误
						if (res.data && res.data.trim() !== '') {
							console.warn('忽略无效的WebSocket消息:', res.data);
						}
						// 不做处理，可能是中间状态的数据或空内容
					}
				});
				// 断线后释放当前页面的连接，下次显示或发送时重新连接
				socket.onClose(function(res) {
					if (that._chatSocket !== socket) return;
					that._chatSocket = null;
					that.socketReady = false;
					that.loading = false;
					that.typeingText = '';
				});
			},
			// 生成 UUID（用于 chatId）
			generateUUID() {
				return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
					const r = Math.random() * 16 | 0;
					const v = c === 'x' ? r : (r & 0x3 | 0x8);
					return v.toString(16);
				});
			},
			// 选择图片
			chooseImage() {
				uni.chooseImage({
					count: 1,
					sizeType: ['compressed'],
					sourceType: ['album', 'camera'],
					success: (res) => {
						// 立即上传图片到服务器
						this.uploadImage(res.tempFilePaths[0])
					},
					fail: (err) => {
						console.error('选择图片失败:', err)
						uni.showToast({
							title: '选择图片失败',
							icon: 'none'
						})
					}
				})
			},
			// 三端共用上传提示与响应解析，页面只处理后端文件地址。
			async uploadAttachment(options) {
				this.uploading = true;
				uni.showLoading({ title: '上传中...', mask: true });
				let fileUrl = null;
				let errorMessage = '';
				try {
					const result = await uploadFile(options);
					fileUrl = result.data.fileUrl;
				} catch (error) {
					errorMessage = error.message || '上传失败，请重试';
				} finally {
					this.uploading = false;
					uni.hideLoading();
				}
				uni.showToast({ title: errorMessage || '上传成功', icon: errorMessage ? 'none' : 'success', duration: 1500 });
				return fileUrl;
			},
			// 图片上传完成前不把本地临时路径作为 fileUrl 发送。
			async uploadImage(filePath) {
				this.selectedImage = '';
				const url = await this.uploadAttachment({ filePath });
				this.selectedImage = url || '';
				return url;
			},
			// 移除图片
			removeImage() {
				this.selectedImage = ''
			},
			// 预览图片
			previewImage(url) {
				uni.previewImage({
					urls: [url],
					current: url
				})
			},
			// 首屏只取最近一页；更早消息分页加载，现有本地消息始终保留在尾部。
			getMessages({ more = false } = {}) {
				if (this._historyPromise) return this._historyPromise;
				if (this._pageClosed || (more && (!this.historyLoaded || !this.hasMoreHistory))) return Promise.resolve(false);
				if (!more && this.historyLoaded) return Promise.resolve(true);
				const sessionId = this.sessionId;
				// 固定首次加载的历史范围；升序页从后往前取，新增消息不会移动旧页偏移。
				const snapshot = more && this._historyNextPage != null;
				const pageNum = more ? (snapshot ? this._historyNextPage : this.historyPage + 1) : 1;
				const anchor = more && this.arr.length ? this.arr[0].message_id : '';
				this.historyLoading = true;
				this.historyError = '';
				this._historyPromise = (async () => {
					try {
						this.assertSessionUser();
						const page = await getSessionMessages(this.$request, this.msgId, sessionId, {
							pageNum, pageSize: this.historyPageSize, isAsc: snapshot ? 'asc' : 'desc'
						});
						this.assertSessionUser();
						if (this._pageClosed || this.sessionId !== sessionId) return false;
						const saved = new Map(this.arr.filter(item => item.id).map(item => [String(item.id), item]));
						page.rows.filter(item => !more || !this._historyOldestId ||
							compareMessageIds(item, { id: this._historyOldestId }) < 0).forEach(item => saved.set(item.id, item));
						const local = this.arr.filter(item => !item.id && item.message_id !== 'welcome');
						this.arr = [...Array.from(saved.values()).sort(compareMessageIds), ...local];
						this.historyCount = saved.size;
						this.historyPage = more ? this.historyPage + 1 : 1;
						if (!more) {
							this._historyOldestId = page.oldestId;
							this._historyNextPage = page.total === null ? null : Math.ceil(Math.max(0, page.total - this.historyPageSize) / this.historyPageSize);
						} else if (snapshot) {
							this._historyNextPage = pageNum - 1;
						}
						this.hasMoreHistory = snapshot ? this._historyNextPage > 0 : page.hasMore;
						this.historyLoaded = true;
						this.$nextTick(() => {
							if (!this._pageClosed) uni.pageScrollTo({ selector: anchor ? '#message' + anchor : '#chat-bottom', duration: 0 });
						});
						return true;
					} catch (error) {
						if (!this._pageClosed && this.sessionId === sessionId) {
							this.historyError = '历史消息加载失败，点击重试';
							this.$failToast('历史消息加载失败，请检查网络后重试');
						}
						return false;
					} finally {
						this.historyLoading = false;
					}
				})().finally(() => { this._historyPromise = null; });
				return this._historyPromise;
			},
			loadMoreHistory() {
				return this.getMessages({ more: true });
			},
			retryHistory() {
				return this.getMessages({ more: this.historyLoaded });
			},
			async sendout() {
				if (this.uploading) {
					this.$failToast('文件上传中，请稍候');
					return;
				}
				if (this.sending || this.loading) return;
				const userInfo = uni.getStorageSync('userInfo') || {};

				// 检查是否有内容或图片
				if (!this.prompt && !this.selectedImage) {
					this.$failToast("请输入内容或选择图片");
					return;
				}

				// 生成新的 chatId（每次发送消息时生成，用于标识一组完整的对话）
				this.currentChatId = this.generateUUID();

				// 构建消息对象
				const messageData = {
					role: 'user',
					content: this.prompt || '',
					message_id: this.arr.length + 1,
					createTime: new Date().getTime()
				}

				// 如果有图片，添加图片URL
				if (this.selectedImage) {
					messageData.imageUrl = this.selectedImage
				}

				const wsMessage = {
					content: this.prompt || '',
					chatId: this.currentChatId
				}

				// 智能体对话（专业助理）：带上 agentId，后端按智能体配置解析模型/提示词/知识库
				if (this.agentId) {
					wsMessage.agentId = this.agentId
				}
				// 系统提示词（创作大师/首页问题）：后端拼到用户输入前
				if (this.systemPrompt) {
					wsMessage.systemPrompt = this.systemPrompt
				}

				// 如果有图片，添加 fileUrl 和 chatType
				if (this.selectedImage) {
					wsMessage.fileUrl = this.selectedImage  // 使用 fileUrl 而不是 imageUrl
					wsMessage.chatType = 1  // chatType = 1 表示图片文件
				}


				if (!await this.sendChatMessage(wsMessage, messageData)) return;

				this.prompt = ""
				this.selectedImage = "" // 清空选中的图片

				this.scrollReplyToLatest(true);
			},
            copy(item) {
                if (!item || !item.content || this.copyingMessageId) return;
                const display = presentMessage(item.content, {role:item.role==='user'?'user':'assistant'});
                if (display.pending || !display.text.trim()) return;
                const id = String(item.message_id || item.id || 'message');
                const generation = this._copyGeneration || 0;
                const owner = uni.getStorageSync('token');
                const current = () => !this._pageClosed && generation === (this._copyGeneration || 0) && owner === uni.getStorageSync('token');
                this.copyingMessageId = id;
                const failed = () => {
                    if (!current()) return;
                    this.copyingMessageId = '';
                    uni.showToast({title:'复制失败，请重试或长按文字复制', icon:'none'});
                };
                try {
                    uni.setClipboardData({
                        data:messageCopyText(item.content,item.role==='user'?'user':'assistant'), showToast:false,
                        success:() => {
                            if (!current()) return;
                            clearTimeout(this._copyFeedbackTimer);
                            this.copyingMessageId = '';
                            this.copiedMessageId = id;
                            uni.showToast({title:'已复制全文', icon:'success'});
                            this._copyFeedbackTimer = setTimeout(() => {
                                if (current()) this.copiedMessageId = '';
                            }, 2000);
                        },
                        fail:failed
                    });
                } catch (_) { failed(); }
            },
			goBack() {
				uni.navigateBack()
			},
			formatTime(timestamp) {
				if (!timestamp) return ''
				const date = new Date(timestamp)
				const now = new Date()
				const diff = now - date

				if (diff < 60000) return '刚刚'
				if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
				if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
				return date.toLocaleDateString()
			},
			selectQuickReply(reply) {
				this.prompt = reply
				this.quickReplies = []
			},
			// 点赞
			handleLike(item, index) {
				const that = this

				// 检查消息是否有ID（只有从后端返回的消息才有ID）
				if (!item.id) {
					uni.showToast({
						title: '该消息暂不支持反馈',
						icon: 'none',
						duration: 2000
					})
					return
				}

				// 如果已经点赞，取消点赞（传null表示取消反馈）
				if (item.liked) {
					// 暂不支持取消，可以根据需求实现
					uni.showToast({
						title: '已点赞',
						icon: 'none',
						duration: 1500
					})
					return
				}

				// 调用后端接口：点赞（feedbackType=1）
				that.$request(`/system/message/feedback/${item.id}/1`, 'PUT', {}, {
					'content-type': 'application/json'
				}).then((res) => {
					if (res.code === 200) {
						// 点赞成功，更新UI
						that.$set(that.arr[index], 'liked', true)
						that.$set(that.arr[index], 'disliked', false)
						that.$set(that.arr[index], 'feedbackType', 1)
						uni.showToast({
							title: '点赞成功',
							icon: 'success',
							duration: 1500
						})
					} else {
						uni.showToast({
							title: res.msg || '点赞失败',
							icon: 'none',
							duration: 2000
						})
					}
				}).catch((err) => {
					console.error('点赞失败:', err)
					uni.showToast({
						title: '点赞失败，请重试',
						icon: 'none',
						duration: 2000
					})
				})
			},
			// 点踩
			handleDislike(item, index) {
				const that = this

				// 检查消息是否有ID（只有从后端返回的消息才有ID）
				if (!item.id) {
					uni.showToast({
						title: '该消息暂不支持反馈',
						icon: 'none',
						duration: 2000
					})
					return
				}

				// 如果已经点踩，取消点踩
				if (item.disliked) {
					// 暂不支持取消，可以根据需求实现
					uni.showToast({
						title: '已点踩',
						icon: 'none',
						duration: 1500
					})
					return
				}

				// 调用后端接口：点踩（feedbackType=2）
				that.$request(`/system/message/feedback/${item.id}/2`, 'PUT', {}, {
					'content-type': 'application/json'
				}).then((res) => {
					if (res.code === 200) {
						// 点踩成功，更新UI
						that.$set(that.arr[index], 'disliked', true)
						that.$set(that.arr[index], 'liked', false)
						that.$set(that.arr[index], 'feedbackType', 2)
						uni.showToast({
							title: '已反馈',
							icon: 'success',
							duration: 1500
						})
					} else {
						uni.showToast({
							title: res.msg || '反馈失败',
							icon: 'none',
							duration: 2000
						})
					}
				}).catch((err) => {
					console.error('点踩失败:', err)
					uni.showToast({
						title: '反馈失败，请重试',
						icon: 'none',
						duration: 2000
					})
				})
			},
			// 播放/暂停语音
			handlePlayAudio(item, index) {
				this.textToSpeech(item && item.content, index);
			},
			// 新媒体接口需要明确的音频模型；未配置前不发起朗读请求。
			textToSpeech(text, index) {
				if (this.arr[index]) this.$set(this.arr[index], 'playing', false);
				uni.showToast({ title: '暂不支持朗读，请使用文字咨询', icon: 'none', duration: 2500 });
			},

			// 播放录音文件
			playRecordedAudio(item, index) {
				const that = this

				// 如果正在播放，暂停
				if (item.audioPlaying) {
					that.$set(that.arr[index], 'audioPlaying', false)
					that.innerAudioContext.pause()
					return
				}

				// 停止其他正在播放的录音
				that.arr.forEach((msg, i) => {
					if (msg.audioPlaying && i !== index) {
						that.$set(that.arr[i], 'audioPlaying', false)
					}
					if (msg.playing && i !== index) {
						that.$set(that.arr[i], 'playing', false)
					}
				})

				// 停止当前播放
				that.innerAudioContext.stop()

				// 检查是否有音频URL
				if (!item.audioUrl) {
					uni.showToast({
						title: '音频文件不存在',
						icon: 'none',
						duration: 2000
					})
					return
				}

				// 开始播放
				that.$set(that.arr[index], 'audioPlaying', true)
				that.innerAudioContext.src = item.audioUrl
				that.innerAudioContext.play()


				// 监听播放结束
				that.innerAudioContext.onEnded(() => {
					that.$set(that.arr[index], 'audioPlaying', false)
				})

				// 监听播放错误
				that.innerAudioContext.onError((err) => {
					console.error('录音播放错误:', err)
					that.$set(that.arr[index], 'audioPlaying', false)
					uni.showToast({
						title: '播放失败',
						icon: 'none',
						duration: 2000
					})
				})
			},

			// 初始化录音管理器
			initRecorderManager() {
				const that = this

				// #ifdef H5
				// H5环境检查是否支持录音
				if (typeof uni.getRecorderManager !== 'function') {
					console.warn('H5环境不支持 uni.getRecorderManager，录音功能将不可用')
					return
				}
				// #endif

				try {
					that.recorderManager = uni.getRecorderManager()

					// 录音开始事件
					that.recorderManager.onStart(() => {
						that.isRecording = true
						that.showRecordModal = true
						that.recordDuration = 0

						// 开始计时
						that.recordTimer = setInterval(() => {
							that.recordDuration++
						}, 1000)
					})

					// 录音结束事件
					that.recorderManager.onStop((res) => {
						that.isRecording = false
						that.showRecordModal = false

						// 清除计时器
						if (that.recordTimer) {
							clearInterval(that.recordTimer)
							that.recordTimer = null
						}

						// 录音时长太短
						if (that.recordDuration < 1) {
							uni.showToast({
								title: '录音时间太短',
								icon: 'none',
								duration: 2000
							})
							return
						}

						// 获取录音文件信息
						const tempFilePath = res.tempFilePath
						const duration = res.duration
						const fileSize = res.fileSize


						// 调用语音识别
						that.recognizeSpeech(tempFilePath)
					})

					// 录音错误事件
					that.recorderManager.onError((err) => {
						console.error('录音错误:', err)
						that.isRecording = false
						that.showRecordModal = false

						// 清除计时器
						if (that.recordTimer) {
							clearInterval(that.recordTimer)
							that.recordTimer = null
						}

						uni.showToast({
							title: '录音失败，请重试',
							icon: 'none',
							duration: 2000
						})
					})
				} catch (err) {
					console.error('初始化录音管理器失败:', err)
				}
			},

			// 初始化音频播放器
			initAudioContext() {
				this.innerAudioContext = uni.createInnerAudioContext()

				// 播放结束事件
				this.innerAudioContext.onEnded(() => {
					// 更新所有消息的播放状态
					this.arr.forEach((item, index) => {
						if (item.playing) {
							this.$set(this.arr[index], 'playing', false)
						}
					})
				})

				// 播放错误事件
				this.innerAudioContext.onError((err) => {
					console.error('音频播放错误:', err)
					uni.showToast({
						title: '播放失败',
						icon: 'none',
						duration: 2000
					})
				})
			},

			// #ifdef H5
			/**
			 * 初始化H5录音器
			 */
			initH5Recorder() {
				const that = this


				// 直接初始化，因为已经通过import引入了Recorder
				if (typeof Recorder !== 'undefined') {
					that.initH5RecorderInstance()
				} else {
					console.error('Recorder 未正确引入，请检查import语句')
					uni.showModal({
						title: '录音功能不可用',
						content: 'Recorder库未正确引入，请联系开发者',
						showCancel: false
					})
				}
			},

			/**
			 * 创建H5录音器实例
			 */
			initH5RecorderInstance() {
				const that = this


				// 直接使用Recorder创建实例（不再使用H5Recorder包装类）
				that.h5Recorder = Recorder({
					type: 'mp3',
					sampleRate: 16000,
					bitRate: 16,
					onProcess: function(buffers, powerLevel, bufferDuration, bufferSampleRate) {
						// bufferDuration: 录音时长(毫秒)
						// powerLevel: 音量百分比(0-100)
						that.recordDuration = Math.floor(bufferDuration / 1000)

						// 超过60秒自动停止
						if (that.recordDuration >= 60) {
							that.stopH5Record()
						}
					}
				})

			},

			/**
			 * 开始H5录音
			 */
			async startH5Record() {
				const that = this

				// 检查录音器是否初始化
				if (!that.h5Recorder) {
					// 尝试重新初始化
					if (typeof Recorder !== 'undefined') {
						that.initH5RecorderInstance()
					}

					if (!that.h5Recorder) {
						uni.showModal({
							title: '录音功能不可用',
							content: 'Recorder未正确初始化，请刷新页面后重试',
							showCancel: false
						})
						return
					}
				}

				// 记录开始时间
				that.h5RecordStartTime = Date.now()
				that.h5RecordOpened = false

				// 标记正在打开录音
				that.isRecording = true
				that.showRecordModal = true
				that.recordDuration = 0


				// 打开录音并开始
				that.h5Recorder.open(() => {
					that.h5RecordOpened = true

					// 检查是否已经被取消（用户快速松开了）
					if (!that.isRecording) {
						that.h5Recorder.close()
						that.h5RecordOpened = false
						return
					}

					// 开始录音
					that.h5Recorder.start()
				}, (msg, isUserNotAllow) => {
					console.error('H5录音打开失败:', msg, isUserNotAllow)
					that.isRecording = false
					that.showRecordModal = false
					that.h5RecordOpened = false

					if (isUserNotAllow) {
						uni.showModal({
							title: '需要麦克风权限',
							content: '请在浏览器设置中允许使用麦克风',
							showCancel: false
						})
					} else {
						// 如果是"open被取消"，不显示错误提示
						if (msg && msg.includes('open被取消')) {
						} else {
							uni.showModal({
								title: '录音失败',
								content: msg || '录音失败，请重试',
								showCancel: false
							})
						}
					}
				})
			},

			/**
			 * 停止H5录音
			 */
			async stopH5Record() {
				const that = this

				if (!that.h5Recorder) {
					return
				}

				// 计算按下时长
				const pressDuration = Date.now() - that.h5RecordStartTime

				// 如果按下时间太短（小于300ms），认为是误触
				if (pressDuration < 300) {
					that.isRecording = false
					that.showRecordModal = false

					// 如果录音已经打开，需要关闭
					if (that.h5RecordOpened) {
						that.h5Recorder.close()
						that.h5RecordOpened = false
					}

					uni.showToast({
						title: '按住说话',
						icon: 'none',
						duration: 1000
					})
					return
				}

				// 如果录音还没打开，等待一下
				if (!that.h5RecordOpened) {
					await new Promise(resolve => setTimeout(resolve, 500))
				}

				// 再次检查是否已打开
				if (!that.h5RecordOpened) {
					that.isRecording = false
					that.showRecordModal = false
					return
				}

				// 标记停止录音
				that.isRecording = false
				that.showRecordModal = false

				// 停止录音
				that.h5Recorder.stop((blob, duration) => {

					// 录音时长太短（小于1秒）
					if (duration < 1000) {
						uni.showToast({
							title: '录音时间太短',
							icon: 'none'
						})
						that.h5Recorder.close()
						that.h5RecordOpened = false
						return
					}

					// 播放录音
					that.playH5Recording(blob, duration)

					// 关闭录音
					that.h5Recorder.close()
					that.h5RecordOpened = false
				}, (msg) => {
					console.error('H5录音失败:', msg)

					// 如果是"未开始录音"错误，不显示提示（正常情况）
					if (msg && msg.includes('未开始录音')) {
					} else {
						uni.showToast({
							title: '录音失败',
							icon: 'none'
						})
					}

					that.h5Recorder.close()
					that.h5RecordOpened = false
				})
			},

			/**
			 * H5上传音频Blob到OSS
			 */
			uploadH5AudioBlob(blob) {
				return this.uploadAttachment({
					blob, fileName: `recording_${Date.now()}.mp3`,
					baseUrl: this.config?.baseUrl || setting.base_url
				});
			},

			/**
			 * 播放H5录音 - 修改为上传后发送到对话
			 */
			async playH5Recording(blob, duration) {
				const that = this

				// 先上传音频文件到OSS
				const audioUrl = await that.uploadH5AudioBlob(blob)

				if (!audioUrl) {
					uni.showToast({
						title: '音频上传失败',
						icon: 'none',
						duration: 2000
					})
					return
				}

				// 生成新的 chatId
				that.currentChatId = that.generateUUID()

				// 构建消息对象（用于显示在聊天列表）
				const messageData = {
					role: 'user',
					content: '[语音消息]',
					type: 'audio',
					audioUrl: audioUrl,
					duration: duration,
					message_id: that.arr.length + 1,
					createTime: new Date().getTime()
				}

				// 构建发送到WebSocket的数据
				const wsMessage = {
					content: '[语音消息]',
					fileUrl: audioUrl,  // 使用 fileUrl 而不是 imageUrl
					chatType: 2,  // chatType = 2 表示语音文件
					chatId: that.currentChatId
				}


				if (!await that.sendChatMessage(wsMessage, messageData)) return;

				that.scrollReplyToLatest(true);
			},
			// #endif

			// 开始录音
			startRecord() {
				const that = this

				// 立即设置录音状态，让图标立即响应
				that.isRecording = true
				that.showRecordModal = true

				// #ifdef H5
				// H5环境暂不支持录音功能
				that.isRecording = false
				that.showRecordModal = false
				uni.showModal({
					title: '录音功能不可用',
					content: 'H5环境暂不支持录音功能，请在微信小程序或App中使用以获得完整体验',
					confirmText: '我知道了',
					showCancel: false
				})
				return
				// #endif

				// #ifndef H5
				// 检查录音管理器是否已初始化
				if (!that.recorderManager) {
					that.isRecording = false
					that.showRecordModal = false
					uni.showModal({
						title: '录音功能不可用',
						content: '录音管理器初始化失败，请重启应用后重试',
						showCancel: false
					})
					return
				}


				// 小程序和App环境需要先检查权限
				uni.getSetting({
					success(res) {
						if (res.authSetting['scope.record']) {
							// 已授权，直接开始录音
							that.recorderManager.start({
								duration: 60000, // 最长录音时长60秒
								sampleRate: 16000, // 采样率
								numberOfChannels: 1, // 录音通道数
								encodeBitRate: 96000, // 编码码率
								format: 'mp3', // 音频格式
								frameSize: 50 // 指定帧大小
							})
						} else if (res.authSetting['scope.record'] === false) {
							// 已拒绝授权，引导用户到设置页面
							that.isRecording = false
							that.showRecordModal = false
							uni.showModal({
								title: '需要录音权限',
								content: '请在设置中允许使用录音功能',
								success(modalRes) {
									if (modalRes.confirm) {
										uni.openSetting({
											success(settingRes) {
												if (settingRes.authSetting['scope.record']) {
													uni.showToast({
														title: '授权成功，请重试',
														icon: 'success'
													})
												}
											}
										})
									}
								}
							})
						} else {
							// 未授权，请求授权
							uni.authorize({
								scope: 'scope.record',
								success() {
									// 授权成功，开始录音
									that.recorderManager.start({
										duration: 60000,
										sampleRate: 16000,
										numberOfChannels: 1,
										encodeBitRate: 96000,
										format: 'mp3',
										frameSize: 50
									})
								},
								fail() {
									// 授权失败
									that.isRecording = false
									that.showRecordModal = false
									uni.showToast({
										title: '需要录音权限',
										icon: 'none',
										duration: 2000
									})
								}
							})
						}
					},
					fail() {
						// 获取设置失败，尝试直接开始录音
						that.recorderManager.start({
							duration: 60000,
							sampleRate: 16000,
							numberOfChannels: 1,
							encodeBitRate: 96000,
							format: 'mp3',
							frameSize: 50
						})
					}
				})
				// #endif
			},

			// 结束录音
			endRecord() {
				if (this.isRecording) {
					this.recorderManager.stop()
				}
			},

			// App/小程序录音文件复用同一个上传封装。
			uploadAudio(tempFilePath) {
				return this.uploadAttachment({ filePath: tempFilePath });
			},

			// 语音识别（转文字）- 修改为先上传音频，然后发送到对话
			async recognizeSpeech(tempFilePath) {
				const that = this

				// 先上传音频文件到OSS
				const audioUrl = await that.uploadAudio(tempFilePath)

				if (!audioUrl) {
					uni.showToast({
						title: '音频上传失败',
						icon: 'none',
						duration: 2000
					})
					return
				}

				// 生成新的 chatId
				that.currentChatId = that.generateUUID()

				// 构建消息对象（用于显示在聊天列表）
				const messageData = {
					role: 'user',
					content: '[语音消息]',
					type: 'audio',
					audioUrl: audioUrl,
					message_id: that.arr.length + 1,
					createTime: new Date().getTime()
				}

				// 构建发送到WebSocket的数据
				const wsMessage = {
					content: '[语音消息]',
					fileUrl: audioUrl,  // 使用 fileUrl 而不是 imageUrl
					chatType: 2,  // chatType = 2 表示语音文件
					chatId: that.currentChatId
				}


				if (!await that.sendChatMessage(wsMessage, messageData)) return;

				that.scrollReplyToLatest(true);
			}
		}
	}
</script>

<style>
	/*每个页面公共css */
	@import '../../components/css/index.css';
</style>

<style lang="scss" scoped>

.answer-collapsed { max-height: 60vh; overflow: hidden; }
	/* 引入分离的样式文件 */
	@import './index.scss';
</style>

<style scoped>.body_lpic{background:var(--brand-surface)}.history-action{color:var(--brand-link)}</style>

<style scoped>.chat-context-toggle{display:flex;align-items:center;justify-content:center;margin:0 0 8px auto;padding:8px 12px;min-height:44px;box-sizing:border-box;border-radius:12px;font-size:14px;line-height:1.5;background:transparent;color:var(--brand-muted)}.chat-context-toggle::after{border:0}.body{padding-top:12px}.content{background:var(--brand-surface)}</style>

<style scoped>
.message-actions{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:4px}
.message-actions-user{justify-content:flex-end}
.answer-notice{font-size:14px;color:var(--brand-muted)}
.message-copy{display:inline-flex;align-items:center;justify-content:center;min-height:44px;margin:0;padding:0 12px;font-size:14px;line-height:1.4;color:var(--brand-link);background:transparent}
.message-copy::after{border:0}
.return-latest{position:absolute;right:16px;bottom:calc(100% + 12px);z-index:997;min-height:44px;padding:0 16px;font-size:14px;line-height:44px;border:1px solid var(--brand-border);border-radius:24px;background:var(--brand-surface);color:var(--brand-text)}
.message-copy:focus-visible,.return-latest:focus-visible{outline:2px solid var(--brand-link);outline-offset:2px}
</style>

<style scoped>
.content .body{padding:16px}
.body_l,.body_r{margin-bottom:24px}
.body_lpic,.body_rpic,.body_jt,.body_rjt{display:none}
.body_l .body_box{flex:1;min-width:0;width:100%}
.body_l .body_rcon,.body_l .body_lcon{box-sizing:border-box;width:100%;max-width:none;margin:0;padding:0;background:transparent;font-size:16px;line-height:1.7;text-align:left}
.body_r .body_box{min-width:0;max-width:88%}
.body_r .body_rcon{box-sizing:border-box;max-width:100%;margin:0;padding:12px 14px;border-radius:16px;font-size:16px;line-height:1.7;text-align:left}
</style>

<style lang="scss">
@import '../../components/css/consultation-composer.scss';
.chat-compose-area { position:fixed; left:0; right:0; bottom:0; z-index:10; box-sizing:border-box; padding:12px 16px calc(8px + env(safe-area-inset-bottom)); background:var(--brand-surface); }
.content .flooer.consultation-composer { position:relative; left:auto; right:auto; bottom:auto; z-index:auto; display:block; width:100%; max-width:760px; margin:0 auto; padding:14px 14px 10px; border:1px solid var(--brand-border); border-radius:22px; box-sizing:border-box; background:var(--brand-background); }
.chat-compose-area .return-latest { right:16px; bottom:calc(100% + 8px); z-index:1; }
</style>
