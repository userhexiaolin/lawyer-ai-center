import { uploadFile } from '../common/upload'

/**
 * H5录音工具类
 * 基于 Recorder.js (https://github.com/xiangyuecn/Recorder)
 * 参考文档：https://segmentfault.com/a/1190000043886953
 *
 * 注意：此文件已废弃，请直接在页面中使用 import Recorder from 'recorder-core' 方式引入
 * 保留此文件仅为兼容性考虑
 */

class H5Recorder {
	constructor(options = {}) {
		this.recorder = null
		this.isRecording = false
		
		// 回调函数
		this.onStart = options.onStart || function() {}
		this.onStop = options.onStop || function() {}
		this.onError = options.onError || function() {}
		this.onProgress = options.onProgress || function() {}
		
		// 录音配置
		this.config = {
			type: 'mp3',
			sampleRate: 16000,
			bitRate: 16,
			...options.config
		}
	}
	
	/**
	 * 开始录音
	 */
	async start() {
		const that = this
		
		// 检查Recorder是否加载
		if (typeof Recorder === 'undefined') {
			that.onError('Recorder.js未加载')
			return Promise.reject('Recorder.js未加载')
		}
		
		return new Promise((resolve, reject) => {
			// 创建Recorder实例
			that.recorder = Recorder({
				type: that.config.type,
				sampleRate: that.config.sampleRate,
				bitRate: that.config.bitRate,
				
				// 录音过程中实时回调
				onProcess: function(buffers, powerLevel, duration, sampleRate) {
					// duration: 录音时长(毫秒)
					// powerLevel: 音量百分比(0-100)
					that.onProgress(duration, powerLevel)
				}
			})
			
			// 打开录音
			that.recorder.open(function() {
				console.log('录音已打开')
				
				// 开始录音
				that.recorder.start()
				that.isRecording = true
				that.onStart()
				resolve()
				
			}, function(msg, isUserNotAllow) {
				console.error('录音打开失败:', msg, isUserNotAllow)
				that.onError(msg, isUserNotAllow)
				reject(msg)
			})
		})
	}
	
	/**
	 * 停止录音
	 */
	async stop() {
		const that = this
		
		if (!that.recorder) {
			return Promise.reject('录音器未初始化')
		}
		
		return new Promise((resolve, reject) => {
			that.recorder.stop(function(blob, duration) {
				console.log('录音成功:', blob, duration + 'ms')
				that.isRecording = false
				that.onStop(blob, duration)
				
				// 关闭录音，释放资源
				that.recorder.close()
				that.recorder = null
				
				resolve({ blob, duration })
				
			}, function(msg) {
				console.error('录音失败:', msg)
				that.onError(msg)
				reject(msg)
			})
		})
	}
	
	/**
	 * 暂停录音
	 */
	pause() {
		if (this.recorder && this.isRecording) {
			this.recorder.pause()
		}
	}
	
	/**
	 * 继续录音
	 */
	resume() {
		if (this.recorder && this.isRecording) {
			this.recorder.resume()
		}
	}
	
	/**
	 * 取消录音
	 */
	cancel() {
		if (this.recorder) {
			this.recorder.close()
			this.recorder = null
			this.isRecording = false
		}
	}
	
	/**
	 * Blob转Base64
	 */
	static blobToBase64(blob) {
		return new Promise((resolve, reject) => {
			const reader = new FileReader()
			reader.onloadend = () => resolve(reader.result)
			reader.onerror = reject
			reader.readAsDataURL(blob)
		})
	}
	
	/**
	 * Blob转File
	 */
	static blobToFile(blob, fileName) {
		return new File([blob], fileName, { type: blob.type })
	}
	
	/**
	 * 上传录音文件
	 */
	static async uploadBlob(blob, url, options = {}) {
		const fileName = options.fileName || 'recording.' + (blob.type.split('/')[1] || 'mp3')
		return uploadFile({
			blob, url, fileName, formData: options.params || {}, headers: options.headers || {}
		})
	}
}

export default H5Recorder

