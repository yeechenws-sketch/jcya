(function(global) {
    'use strict';

    if (!document.querySelector('link[href*="font-awesome"]')) {
        const faLink = document.createElement('link');
        faLink.rel = 'stylesheet';
        faLink.href = 'https://cdn.jsdelivr.net/npm/font-awesome@4.7.0/css/font-awesome.min.css';
        document.head.appendChild(faLink);
    }

    const GlobalMessageCenter = {
        socket: null,
        signalingSocket: null,
        currentUser: null,
        isConnected: false,
        reconnectAttempts: 0,
        maxReconnectAttempts: 10,
        reconnectDelay: 3000,
        messageQueue: [],
        contacts: [],
        onlineUsers: {},
        mqttInitialized: false,
        suppressInAppNotification: false,

        notificationSound: null,
        ringSound: null,

        onMessageCallback: null,
        onCallCallback: null,
        onOnlineStatusCallback: null,
        onVoicePrompt: null,

        hasNotificationPermission: false,
        serviceWorkerRegistration: null,

        init: async function() {
            console.log('[全局消息中心] 初始化中...');
            
            await this.checkNotificationPermission();
            await this.registerServiceWorker();
            this.preloadSounds();
            await this.loadUserInfo();
            
            if (this.currentUser) {
                await this.loadContacts();
                this.connectWebSocket();
                this.connectSignaling();
            }
            
            this.setupVisibilityHandler();
            this.setupBeforeUnloadHandler();
            
            this.initMqttManager();
            
            console.log('[全局消息中心] 初始化完成，联系人数量:', this.contacts.length);
        },

        checkNotificationPermission: async function() {
            if ('Notification' in window) {
                if (Notification.permission === 'granted') {
                    this.hasNotificationPermission = true;
                } else if (Notification.permission !== 'denied') {
                    const permission = await Notification.requestPermission();
                    this.hasNotificationPermission = (permission === 'granted');
                }
            }
            console.log('[全局消息中心] 通知权限:', this.hasNotificationPermission);
        },

        registerServiceWorker: async function() {
            if ('serviceWorker' in navigator) {
                try {
                    const reg = await navigator.serviceWorker.register('/sw.js');
                    this.serviceWorkerRegistration = reg;
                    console.log('[全局消息中心] ServiceWorker 注册成功');
                } catch (err) {
                    console.log('[全局消息中心] ServiceWorker 注册失败:', err);
                }
            }
        },

        preloadSounds: async function() {
            try {
                const res = await fetch('/api/qiniu/ringtones');
                if (res.ok) {
                    const data = await res.json();
                    if (data.success && data.items) {
                        const ringtones = {};
                        data.items.forEach(item => {
                            ringtones[item.type] = item.url;
                        });
                        
                        this.notificationSound = new Audio(ringtones.message || '/uploads/audios/notice.wav');
                        this.notificationSound.preload = 'auto';
                        
                        this.ringSound = new Audio(ringtones.ring || '/uploads/audios/ring.mp3');
                        this.ringSound.preload = 'auto';
                        this.ringSound.loop = true;
                        
                        this.newFriendSound = new Audio(ringtones.friend || '/uploads/audios/new_friend.wav');
                        this.newFriendSound.preload = 'auto';
                        
                        this.emergencySound = new Audio(ringtones.emergency || '/uploads/audios/warning.mp3');
                        this.emergencySound.preload = 'auto';
                        
                        console.log('[全局消息中心] 铃声配置加载成功');
                        return;
                    }
                }
            } catch (err) {
                console.error('[全局消息中心] 加载铃声配置失败，使用默认配置:', err);
            }
            
            this.notificationSound = new Audio('/uploads/audios/notice.wav');
            this.notificationSound.preload = 'auto';
            
            this.ringSound = new Audio('/uploads/audios/ring.mp3');
            this.ringSound.preload = 'auto';
            this.ringSound.loop = true;

            this.newFriendSound = new Audio('/uploads/audios/new_friend.wav');
            this.newFriendSound.preload = 'auto';
            
            this.emergencySound = new Audio('/uploads/audios/warning.mp3');
            this.emergencySound.preload = 'auto';
        },

        loadUserInfo: async function() {
            try {
                const res = await axios.get('/api/user/info');
                if (res.data.code === 200) {
                    this.currentUser = res.data.data;
                    localStorage.setItem('user_id', this.currentUser.id);
                    localStorage.setItem('nickname', this.currentUser.nickname || '');
                    localStorage.setItem('user_role', this.currentUser.role || 'older');
                    console.log('[全局消息中心] 用户信息加载成功:', this.currentUser.nickname);
                }
            } catch (err) {
                console.error('[全局消息中心] 加载用户信息失败:', err);
            }
        },

        connectWebSocket: function() {
            if (this.socket && this.socket.readyState === WebSocket.OPEN) {
                return;
            }

            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${window.location.host}/chat/${this.currentUser.id}`;

            try {
                this.socket = new WebSocket(wsUrl);

                this.socket.onopen = () => {
                    console.log('[全局消息中心] WebSocket 连接成功');
                    this.isConnected = true;
                    this.reconnectAttempts = 0;
                    this.flushMessageQueue();
                };

                this.socket.onmessage = (e) => {
                    this.handleWebSocketMessage(JSON.parse(e.data));
                };

                this.socket.onclose = (event) => {
                    console.log('[全局消息中心] WebSocket 连接关闭:', event.code);
                    this.isConnected = false;
                    this.attemptReconnect();
                };

                this.socket.onerror = (error) => {
                    console.error('[全局消息中心] WebSocket 错误:', error);
                    this.isConnected = false;
                };

            } catch (error) {
                console.error('[全局消息中心] WebSocket 初始化失败:', error);
                this.attemptReconnect();
            }
        },

        connectSignaling: function() {
            const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
            const signalingUrl = protocol + '//' + window.location.host;

            try {
                this.signalingSocket = io(signalingUrl, {
                    path: '/socket.io',
                    transports: ['websocket', 'polling'],
                    reconnection: true,
                    reconnectionAttempts: 10,
                    reconnectionDelay: 3000
                });

                this.signalingSocket.on('connect', () => {
                    console.log('[全局消息中心] 信令服务器连接成功');
                    this.signalingSocket.emit('login', {
                        userId: this.currentUser.id,
                        nickname: this.currentUser.nickname || '用户',
                        avatar: this.currentUser.avatar || ''
                    });
                });

                this.signalingSocket.on('calling', (data) => {
                    console.log('[全局消息中心] 收到来电:', data);
                    this.handleIncomingCall(data);
                });

                this.signalingSocket.on('hangup', () => {
                    console.log('[全局消息中心] 对方挂断');
                    this.handleHangup();
                });

                this.signalingSocket.on('disconnect', () => {
                    console.log('[全局消息中心] 信令服务器断开');
                });

            } catch (error) {
                console.error('[全局消息中心] 信令服务器连接失败:', error);
            }
        },

        attemptReconnect: function() {
            if (this.reconnectAttempts >= this.maxReconnectAttempts) {
                console.log('[全局消息中心] 达到最大重连次数');
                return;
            }

            this.reconnectAttempts++;
            const delay = this.reconnectDelay * Math.min(this.reconnectAttempts, 5);

            console.log(`[全局消息中心] ${delay/1000}秒后尝试第${this.reconnectAttempts}次重连`);

            setTimeout(() => {
                if (!this.isConnected) {
                    this.connectWebSocket();
                }
            }, delay);
        },

        flushMessageQueue: function() {
            while (this.messageQueue.length > 0 && this.isConnected) {
                const msg = this.messageQueue.shift();
                this.socket.send(JSON.stringify(msg));
            }
        },

        handleWebSocketMessage: function(m) {
            console.log('[全局消息中心] 收到消息:', m);
            
            if (m.msgType === 'MEDICATION_RESULT') {
                console.log('[全局消息中心] 收到服药结果消息，优先处理:', m);
                this.handleMedicationResult(m);
                return;
            }

            if (m.msgType === 'USER_STATUS') {
                this.onlineUsers[m.userId] = m.online;
                if (this.onOnlineStatusCallback) {
                    this.onOnlineStatusCallback(m.userId, m.online);
                }
                return;
            }

            if (m.msgType === 'FRIEND_DELETE') {
                this.loadContacts();
                window.dispatchEvent(new CustomEvent('global-friend-delete', { detail: m }));
                return;
            }

            if (m.msgType === 'FRIEND_REQUEST' || m.msgType === 'FRIEND_ACCEPT') {
                this.loadContacts();
                this.showFriendRequestNotification(m);
                window.dispatchEvent(new CustomEvent('global-friend-update', { detail: m }));
                return;
            }

            if (m.msgType === 'EMERGENCY_CONTACT_REQUEST') {
                console.log('[全局消息中心] 收到紧急联系人申请:', m);
                this.handleEmergencyContactRequest(m);
                return;
            }

            if (m.msgType === 'AUTO_EMERGENCY_TRIGGERED') {
                console.log('[全局消息中心] 收到自动紧急呼叫触发通知:', m);
                if (this.onAutoEmergencyTriggered) {
                    this.onAutoEmergencyTriggered(m);
                }
                return;
            }

            if (m.msgType === 'EMERGENCY_CALL_NOTIFICATION') {
                console.log('[全局消息中心] 收到自动紧急呼叫通知:', m);
                this.handleEmergencyCallNotification(m);
                return;
            }

            if (m.msgType === 'SYSTEM_NOTIFICATION') {
                console.log('[全局消息中心] 收到系统通知:', m);
                this.handleSystemNotification(m);
                return;
            }

            if (m.msgType === 'MEDICATION_REMINDER') {
                console.log('[全局消息中心] 收到用药提醒:', m);
                this.handleMedicationReminder(m);
                return;
            }

            if (m.msgType === 'DEVICE_EVENT') {
                console.log('[全局消息中心] 收到设备事件:', m);
                this.handleDeviceEvent(m);
                return;
            }

            if (m.type === 'VOICE_PROMPT' || m.msgType === 'VOICE_PROMPT') {
                console.log('[全局消息中心] 收到语音提示:', m);
                if (this.onVoicePrompt) {
                    this.onVoicePrompt(m.message || m.content);
                }
                return;
            }

            if (m.msgType === 'call_event') {
                return;
            }

            const isMyMessage = String(m.fromId) === String(this.currentUser?.id);
            if (!isMyMessage) {
                this.handleIncomingMessage(m);
            }
        },

        handleIncomingMessage: function(m) {
            console.log('[全局消息中心] 处理收到的消息:', m);
            
            const senderId = m.fromId;
            const contact = this.contacts.find(c => String(c.id) === String(senderId));

            const senderName = contact ? (contact.remark || contact.nickname) : '用户';
            const senderAvatar = contact ? contact.avatar : '';

            let messagePreview = m.content;
            if (m.msgType === 'voice') {
                messagePreview = `[语音] ${m.duration || ''}秒`;
            } else if (m.msgType === 'image') {
                messagePreview = '[图片]';
            } else if (m.msgType === 'video') {
                messagePreview = '[视频]';
            }

            this.playNotificationSound();

            if (!this.suppressInAppNotification) {
                this.showInAppNotification(senderName, messagePreview, senderAvatar, m);
            }

            if (document.hidden || !document.hasFocus()) {
                this.showSystemNotification(senderName, messagePreview, senderAvatar, m);
            }

            if (this.onMessageCallback) {
                this.onMessageCallback(m, contact);
            }
        },

        handleIncomingCall: function(data) {
            console.log('[全局消息中心] 处理来电:', data);

            this.playRingSound();

            const callerName = data.fromUserName || '用户';
            const callerAvatar = data.fromUserAvatar || '';
            const callType = data.type || 'voice';

            if (document.hidden || !document.hasFocus()) {
                this.showCallNotification(callerName, callerAvatar, callType, data);
            }

            this.showInAppCallUI(data);

            if (this.onCallCallback) {
                this.onCallCallback(data);
            }
        },

        handleHangup: function() {
            this.stopRingSound();
            this.hideCallUI();
        },

        handleEmergencyCallNotification: function(m) {
            console.log('[全局消息中心] 处理自动紧急呼叫通知:', m);
            
            this.playRingSound();
            
            const callerName = this.currentUser?.nickname || '长辈';
            const callType = 'video';
            
            const callData = {
                fromUserId: m.callRecordId ? m.fromId : this.currentUser?.id,
                fromUserName: callerName,
                fromUserAvatar: '',
                type: callType,
                isEmergency: true,
                isAutoEmergency: true,
                alertType: m.alertType,
                alertValue: m.alertValue,
                alertLevel: m.alertLevel,
                callRecordId: m.callRecordId
            };
            
            if (document.hidden || !document.hasFocus()) {
                this.showCallNotification(callerName, '', callType, callData);
            }
            
            this.showInAppCallUI(callData);
            
            if (this.onCallCallback) {
                this.onCallCallback(callData);
            }
        },

        handleSystemNotification: function(m) {
            console.log('[全局消息中心] 处理系统通知:', m);
            
            if (this.notificationSound) {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => console.log('播放提示音失败:', e));
            }

            const title = m.title || '系统通知';
            const content = m.content || '';
            
            this.showInAppNotification('系统通知', content, '', { fromId: 'system', msgType: 'system' });
            
            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification(title, {
                        body: content,
                        icon: '/favicon.ico'
                    });
                }
            }
            
            if (this.onSystemNotification) {
                this.onSystemNotification(m);
            }
        },

        handleMedicationReminder: function(m) {
            console.log('[全局消息中心] 处理用药提醒:', m);
            
            if (this.notificationSound) {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => console.log('播放提示音失败:', e));
            }

            const medicineName = m.medicineName || '药品';
            const time = m.time || '';
            const content = `该吃药了！${medicineName} - ${time}`;
            
            this.showMedicationReminderNotification(medicineName, time, content, m);
            
            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification('用药提醒', {
                        body: content,
                        icon: '/favicon.ico'
                    });
                }
            }
            
            if (this.onMedicationReminder) {
                this.onMedicationReminder(m);
            }
        },

        handleDeviceEvent: function(m) {
            console.log('[全局消息中心] 处理设备事件:', m);
            
            if (this.notificationSound) {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => console.log('播放提示音失败:', e));
            }

            const eventType = m.type || 'DEVICE_EVENT';
            const message = m.message || '设备事件';
            const medicineName = m.medicineName || '';
            const details = m.details || '';
            
            this.showDeviceEventNotification(eventType, message, medicineName, details, m);
            
            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification('智能药盒提醒', {
                        body: message,
                        icon: '/favicon.ico'
                    });
                }
            }
            
            if (this.onDeviceEvent) {
                this.onDeviceEvent(m);
            }
        },

        handleMedicationResult: function(m) {
            console.log('[全局消息中心] 处理服药结果:', m);
            
            if (this.notificationSound) {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => console.log('播放提示音失败:', e));
            }

            if (m.type === 'too_early' || m.type === 'missed' || m.locked || m.duplicate) {
                this.showBlockedTimeMedicationAlert(m);
            } else if (m.needMore) {
                this.showUnderdoseMedicationAlert(m);
            } else if (m.needRestock) {
                this.showOverdoseMedicationAlert(m);
            } else if (m.takeSuccess) {
                this.showSuccessMedicationAlert(m);
            }
            
            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification('服药提醒', {
                        body: m.message || '服药结果通知',
                        icon: '/favicon.ico'
                    });
                }
            }
        },

        showBlockedTimeMedicationAlert: function(data) {
            console.log('[全局消息中心] 显示非服药时间弹窗:', data);
            this.removeExistingMedicationAlerts();

            const overlay = document.createElement('div');
            overlay.className = 'global-medication-alert-overlay';
            overlay.innerHTML = `
                <div class="global-medication-alert-modal">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="font-size: 64px; color: #F59E0B; margin-bottom: 12px;">
                            <i class="fa fa-clock-o"></i>
                        </div>
                        <div style="font-size: 28px; font-weight: 800; color: #F59E0B;">非服药时间</div>
                        <div style="font-size: 18px; color: #92400E; font-weight: 700; margin-top: 8px;">
                            ${data.message || '请将药品放回药盒'}
                        </div>
                        ${data.detailMessage ? `<div style="font-size: 16px; color: #78350F; margin-top: 12px;">${data.detailMessage}</div>` : ''}
                    </div>
                    <div style="display: flex; gap: 16px; justify-content: center;">
                        <button onclick="GlobalMessageCenter.closeMedicationAlert()" style="flex: 1; padding: 16px; border-radius: 20px; font-size: 18px; font-weight: 700; border: none; cursor: pointer; background: #F3F4F6; color: #1E3A8A;">
                            好的
                        </button>
                    </div>
                </div>
            `;
            document.body.appendChild(overlay);
        },

        showUnderdoseMedicationAlert: function(data) {
            console.log('[全局消息中心] 显示少服弹窗:', data);
            this.removeExistingMedicationAlerts();

            const overlay = document.createElement('div');
            overlay.className = 'global-medication-alert-overlay';
            overlay.innerHTML = `
                <div class="global-medication-alert-modal">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="font-size: 64px; color: #F59E0B; margin-bottom: 12px;">
                            <i class="fa fa-exclamation-circle"></i>
                        </div>
                        <div style="font-size: 28px; font-weight: 800; color: #F59E0B;">少服提醒</div>
                        <div style="font-size: 18px; color: #92400E; font-weight: 700; margin-top: 8px;">
                            ${data.message || '服药量不足，请补充服用剩余的药物'}
                        </div>
                        ${data.detailMessage ? `<div style="font-size: 16px; color: #78350F; margin-top: 12px;">${data.detailMessage}</div>` : ''}
                        ${data.medicineName ? `<div style="font-size: 20px; font-weight: 800; color: #1E3A8A; margin-top: 16px;">${data.medicineName}</div>` : ''}
                    </div>
                    <div style="display: flex; gap: 16px; justify-content: center;">
                        <button onclick="GlobalMessageCenter.closeMedicationAlert()" style="flex: 1; padding: 16px; border-radius: 20px; font-size: 18px; font-weight: 700; border: none; cursor: pointer; background: #F59E0B; color: white;">
                            我知道了
                        </button>
                    </div>
                </div>
            `;
            document.body.appendChild(overlay);
        },

        showOverdoseMedicationAlert: function(data) {
            console.log('[全局消息中心] 显示多服弹窗:', data);
            this.removeExistingMedicationAlerts();

            const overlay = document.createElement('div');
            overlay.className = 'global-medication-alert-overlay';
            overlay.innerHTML = `
                <div class="global-medication-alert-modal">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="font-size: 64px; color: #DC2626; margin-bottom: 12px;">
                            <i class="fa fa-exclamation-triangle"></i>
                        </div>
                        <div style="font-size: 28px; font-weight: 800; color: #DC2626;">多服警告！</div>
                        <div style="font-size: 18px; color: #991B1B; font-weight: 700; margin-top: 8px;">
                            ${data.message || '您服用的药物剂量超过了正常范围！请立即将多余的药物放回药盒！'}
                        </div>
                        ${data.detailMessage ? `<div style="font-size: 16px; color: #7F1D1D; margin-top: 12px;">${data.detailMessage}</div>` : ''}
                        ${data.medicineName ? `<div style="font-size: 20px; font-weight: 800; color: #1E3A8A; margin-top: 16px;">${data.medicineName}</div>` : ''}
                    </div>
                    <div style="display: flex; gap: 16px; justify-content: center;">
                        <button onclick="GlobalMessageCenter.closeMedicationAlert()" style="flex: 1; padding: 16px; border-radius: 20px; font-size: 18px; font-weight: 700; border: none; cursor: pointer; background: #DC2626; color: white;">
                            我知道了
                        </button>
                    </div>
                </div>
            `;
            document.body.appendChild(overlay);
        },

        showSuccessMedicationAlert: function(data) {
            console.log('[全局消息中心] 显示成功弹窗:', data);
            this.removeExistingMedicationAlerts();

            const overlay = document.createElement('div');
            overlay.className = 'global-medication-alert-overlay';
            overlay.innerHTML = `
                <div class="global-medication-alert-modal">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <div style="font-size: 64px; color: #10B981; margin-bottom: 12px;">
                            <i class="fa fa-check-circle"></i>
                        </div>
                        <div style="font-size: 28px; font-weight: 800; color: #10B981;">服药成功</div>
                        <div style="font-size: 18px; color: #047857; font-weight: 700; margin-top: 8px;">
                            ${data.message || '您已正确服用药品'}
                        </div>
                        ${data.detailMessage ? `<div style="font-size: 16px; color: #065F46; margin-top: 12px;">${data.detailMessage}</div>` : ''}
                        ${data.medicineName ? `<div style="font-size: 20px; font-weight: 800; color: #1E3A8A; margin-top: 16px;">${data.medicineName}</div>` : ''}
                        ${data.stockAfter !== undefined ? `<div style="font-size: 16px; color: #64748B; margin-top: 12px;">剩余 ${data.stockAfter} ${data.unit || '粒'}</div>` : ''}
                    </div>
                    <div style="display: flex; gap: 16px; justify-content: center;">
                        <button onclick="GlobalMessageCenter.closeMedicationAlert()" style="flex: 1; padding: 16px; border-radius: 20px; font-size: 18px; font-weight: 700; border: none; cursor: pointer; background: #10B981; color: white;">
                            好的
                        </button>
                    </div>
                </div>
            `;
            document.body.appendChild(overlay);
        },

        removeExistingMedicationAlerts: function() {
            const existingAlerts = document.querySelectorAll('.global-medication-alert-overlay');
            existingAlerts.forEach(alert => alert.remove());
        },

        closeMedicationAlert: function() {
            this.removeExistingMedicationAlerts();
        },

        showDeviceEventNotification: function(eventType, message, medicineName, details, messageData) {
            console.log('[全局消息中心] 显示设备事件通知:', eventType, message);

            let container = document.getElementById('global-notification-container');
            if (!container) {
                container = document.createElement('div');
                container.id = 'global-notification-container';
                document.body.appendChild(container);
                console.log('[全局消息中心] 创建通知容器');
            }

            let iconClass = 'fa-cube';
            let gradientColor = 'linear-gradient(135deg,#3B82F6 0%,#1D4ED8 100%)';
            
            if (eventType === 'TAKEOUT') {
                iconClass = 'fa-arrow-up';
                gradientColor = 'linear-gradient(135deg,#F59E0B 0%,#D97706 100%)';
            } else if (eventType === 'PUTBACK') {
                iconClass = 'fa-arrow-down';
                gradientColor = 'linear-gradient(135deg,#10B981 0%,#059669 100%)';
            } else if (eventType === 'BOTTLE_REMOVED') {
                iconClass = 'fa-flask';
                gradientColor = 'linear-gradient(135deg,#EF4444 0%,#DC2626 100%)';
            } else if (eventType === 'BOTTLE_RETURNED') {
                iconClass = 'fa-check-circle';
                gradientColor = 'linear-gradient(135deg,#10B981 0%,#059669 100%)';
            } else if (eventType === 'LOW_STOCK') {
                iconClass = 'fa-exclamation-triangle';
                gradientColor = 'linear-gradient(135deg,#F59E0B 0%,#D97706 100%)';
            }

            const notification = document.createElement('div');
            notification.className = 'global-notification device-event';
            notification.innerHTML = `
                <div class="global-notification-icon" style="width:60px;height:60px;border-radius:50%;background:${gradientColor};display:flex;align-items:center;justify-content:center;font-size:28px;">
                    <i class="fa ${iconClass}"></i>
                </div>
                <div class="global-notification-body">
                    <div class="global-notification-sender">智能药盒</div>
                    <div class="global-notification-message">${message}${medicineName ? ' - ' + medicineName : ''}</div>
                    ${details ? `<div style="font-size:14px;opacity:0.8;margin-top:4px;">${details}</div>` : ''}
                </div>
                <button class="global-notification-close" onclick="this.parentElement.remove()"><i class="fa fa-times"></i></button>
            `;

            notification.onclick = (e) => {
                if (e.target.closest('.global-notification-close')) return;
                if (typeof window.location !== 'undefined') {
                    window.location.href = 'medicine.html';
                }
                notification.remove();
            };

            container.appendChild(notification);
            console.log('[全局消息中心] 设备事件通知已添加到DOM');

            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 300);
            }, 10000);
        },

        showMedicationReminderNotification: function(medicineName, time, content, messageData) {
            console.log('[全局消息中心] 显示用药提醒:', medicineName, time);

            let container = document.getElementById('global-notification-container');
            if (!container) {
                container = document.createElement('div');
                container.id = 'global-notification-container';
                document.body.appendChild(container);
                console.log('[全局消息中心] 创建通知容器');
            }

            const notification = document.createElement('div');
            notification.className = 'global-notification medication-reminder';
            notification.innerHTML = `
                <div class="global-notification-icon" style="width:60px;height:60px;border-radius:50%;background:linear-gradient(135deg,#EF4444 0%,#DC2626 100%);display:flex;align-items:center;justify-content:center;font-size:28px;">
                    <i class="fa fa-medkit"></i>
                </div>
                <div class="global-notification-body">
                    <div class="global-notification-sender">用药提醒</div>
                    <div class="global-notification-message">${content}</div>
                </div>
                <button class="global-notification-close" onclick="this.parentElement.remove()"><i class="fa fa-times"></i></button>
            `;

            notification.onclick = (e) => {
                if (e.target.closest('.global-notification-close')) return;
                if (typeof window.location !== 'undefined') {
                    window.location.href = 'medicine.html';
                }
                notification.remove();
            };

            container.appendChild(notification);
            console.log('[全局消息中心] 用药提醒已添加到DOM');

            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 300);
            }, 10000);
        },

        playNotificationSound: function() {
            if (this.notificationSound) {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => console.log('播放提示音失败:', e));
            }
        },

        playRingSound: function() {
            if (this.ringSound) {
                this.ringSound.currentTime = 0;
                this.ringSound.play().catch(e => console.log('播放铃声失败:', e));
            }
        },

        stopRingSound: function() {
            if (this.ringSound) {
                this.ringSound.pause();
                this.ringSound.currentTime = 0;
            }
        },

        showSystemNotification: function(title, body, icon, messageData) {
            if (!this.hasNotificationPermission) return;

            const notification = new Notification(title, {
                body: body,
                icon: icon || '/favicon.ico',
                tag: 'message-' + messageData.fromId,
                requireInteraction: false,
                data: messageData
            });

            notification.onclick = () => {
                window.focus();
                notification.close();
                this.navigateToMessage(messageData.fromId);
            };

            setTimeout(() => notification.close(), 5000);
        },

        showCallNotification: function(callerName, callerAvatar, callType, callData) {
            if (!this.hasNotificationPermission) return;

            const typeName = callType === 'video' ? '视频通话' : '语音通话';
            const notification = new Notification(`${callerName} 邀请您${typeName}`, {
                body: '点击接听',
                icon: callerAvatar || '/favicon.ico',
                tag: 'call-' + callData.fromUserId,
                requireInteraction: true,
                data: callData
            });

            notification.onclick = () => {
                window.focus();
                notification.close();
            };

            setTimeout(() => {
                notification.close();
                this.stopRingSound();
            }, 60000);
        },

        showInAppNotification: function(senderName, message, avatar, messageData) {
            console.log('[全局消息中心] 显示应用内通知:', senderName, message);

            let container = document.getElementById('global-notification-container');
            if (!container) {
                container = document.createElement('div');
                container.id = 'global-notification-container';
                document.body.appendChild(container);
                console.log('[全局消息中心] 创建通知容器');
            }

            const notification = document.createElement('div');
            notification.className = 'global-notification';
            notification.innerHTML = `
                <img class="global-notification-avatar" src="${avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + messageData.fromId}" alt="">
                <div class="global-notification-body">
                    <div class="global-notification-sender">${senderName}</div>
                    <div class="global-notification-message">${message}</div>
                </div>
                <button class="global-notification-close" onclick="this.parentElement.remove()"><i class="fa fa-times"></i></button>
            `;

            notification.onclick = (e) => {
                if (e.target.closest('.global-notification-close')) return;
                this.navigateToMessage(messageData.fromId);
                notification.remove();
            };

            container.appendChild(notification);
            console.log('[全局消息中心] 通知已添加到DOM');

            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 300);
            }, 5000);
        },

        showInAppCallUI: function(data) {
            let callUI = document.getElementById('global-call-ui');
            if (callUI) {
                callUI.remove();
            }

            const callerName = data.fromUserName || '用户';
            const callerAvatar = data.fromUserAvatar || '';
            const callType = data.type || 'voice';
            const isEmergency = data.isEmergency || false;
            const isAutoEmergency = data.isAutoEmergency || false;
            
            let typeName = callType === 'video' ? '视频通话' : '语音通话';
            if (isEmergency) {
                typeName = '🔥 紧急呼叫 🔥';
            }
            if (isAutoEmergency) {
                typeName = '🚨 自动紧急呼叫 🚨';
            }

            callUI = document.createElement('div');
            callUI.id = 'global-call-ui';
            callUI.innerHTML = `
                <div class="global-call-overlay" style="${isEmergency ? 'background: linear-gradient(135deg, rgba(220, 38, 38, 0.95) 0%, rgba(239, 68, 68, 0.9) 100%);' : ''}">
                    <div class="global-call-content" style="${isEmergency ? 'border: 3px solid #EF4444;' : ''}">
                        <div class="global-call-avatar" style="${isEmergency ? 'background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);' : ''}">
                            ${callerAvatar ? `<img src="${callerAvatar}" alt="${callerName}">` : '<i class="fa fa-user"></i>'}
                        </div>
                        <div class="global-call-name">${callerName}</div>
                        <div class="global-call-type">${typeName}来电...</div>
                        ${isAutoEmergency && data.alertType ? `<div class="global-call-alert" style="color: #FEE2E2; font-size: 14px; margin-top: 8px;">预警类型: ${this.getAlertTypeName(data.alertType)} - ${data.alertValue}</div>` : ''}
                        <div class="global-call-actions">
                            <button class="global-call-btn reject" onclick="GlobalMessageCenter.rejectCall()">
                                <i class="fa fa-phone"></i>
                            </button>
                            <button class="global-call-btn accept" onclick="GlobalMessageCenter.acceptCall()">
                                <i class="fa fa-phone"></i>
                            </button>
                        </div>
                    </div>
                </div>
            `;

            document.body.appendChild(callUI);

            callUI.callData = data;
        },

        hideCallUI: function() {
            const callUI = document.getElementById('global-call-ui');
            if (callUI) {
                callUI.remove();
            }
            this.stopRingSound();
        },

        acceptCall: async function() {
            const callUI = document.getElementById('global-call-ui');
            if (callUI && callUI.callData) {
                const data = callUI.callData;
                this.stopRingSound();
                this.hideCallUI();

                if (data.isAutoEmergency && data.callRecordId) {
                    try {
                        await axios.post('/api/auto-emergency/answer', {
                            callRecordId: data.callRecordId,
                            answeredByUserId: this.currentUser.id
                        });
                        console.log('[全局消息中心] 自动紧急呼叫接听成功');
                    } catch (e) {
                        console.error('[全局消息中心] 自动紧急呼叫接听失败:', e);
                    }
                }

                if (this.signalingSocket) {
                    this.signalingSocket.emit('answerCall', {
                        toUserId: data.fromUserId,
                        fromUserId: this.currentUser.id
                    });
                }

                const myName = this.currentUser?.nickname || '用户';
                const myAvatar = this.currentUser?.avatar || '';
                const peerAvatar = data.fromUserAvatar || '';
                const isEmergency = data.isEmergency || false;

                const url = `call.html?userId=${this.currentUser.id}&peerId=${data.fromUserId}&peerName=${encodeURIComponent(data.fromUserName)}&peerAvatar=${encodeURIComponent(peerAvatar)}&type=${data.type}&incoming=true&answered=true&myName=${encodeURIComponent(myName)}&myAvatar=${encodeURIComponent(myAvatar)}&isEmergency=${isEmergency}`;
                window.location.href = url;
            }
        },

        rejectCall: function() {
            const callUI = document.getElementById('global-call-ui');
            if (callUI && callUI.callData) {
                const data = callUI.callData;
                if (this.signalingSocket) {
                    this.signalingSocket.emit('rejectCall', {
                        toUserId: data.fromUserId
                    });
                }
            }
            this.hideCallUI();
        },

        showFriendRequestNotification: function(m) {
            if (this.newFriendSound) {
                this.newFriendSound.currentTime = 0;
                this.newFriendSound.play().catch(e => console.log('播放好友申请提示音失败:', e));
            }

            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification('新的好友申请', {
                        body: '有人申请添加您为好友',
                        icon: '/favicon.ico'
                    });
                }
            } else {
                this.showInAppNotification('系统', '新的好友申请', '', { fromId: 'system', msgType: 'friend_request' });
            }
        },

        handleEmergencyContactRequest: function(m) {
            console.log('[全局消息中心] 处理紧急联系人申请:', m);
            
            if (this.newFriendSound) {
                this.newFriendSound.currentTime = 0;
                this.newFriendSound.play().catch(e => console.log('播放紧急联系人申请提示音失败:', e));
            }

            if (window.showEmergencyRequestModal) {
                console.log('[全局消息中心] 调用 showEmergencyRequestModal');
                window.showEmergencyRequestModal(m);
            } else {
                console.error('[全局消息中心] 找不到 showEmergencyRequestModal 函数');
                
                if (this.hasNotificationPermission) {
                    new Notification('紧急联系人申请', {
                        body: m.fromName + ' 想成为您的紧急联系人',
                        icon: '/favicon.ico'
                    });
                }
            }
        },

        navigateToMessage: function(fromId) {
            const currentPage = window.location.pathname.split('/').pop();
            if (currentPage === 'msg.html') {
                const event = new CustomEvent('selectContact', { detail: { contactId: fromId } });
                window.dispatchEvent(event);
            } else {
                window.location.href = `msg.html?targetUserId=${fromId}`;
            }
        },

        loadContacts: async function() {
            try {
                const res = await axios.get('/api/friend/list/ordered');
                if (res.data.code === 200) {
                    this.contacts = res.data.data || [];
                }
            } catch (err) {
                console.error('[全局消息中心] 加载联系人失败:', err);
            }
        },

        setupVisibilityHandler: function() {
            document.addEventListener('visibilitychange', () => {
                if (!document.hidden) {
                    if (!this.isConnected && this.currentUser) {
                        this.connectWebSocket();
                    }
                }
            });
        },

        setupBeforeUnloadHandler: function() {
            window.addEventListener('beforeunload', () => {
                if (this.socket) {
                    this.socket.close();
                }
                if (this.signalingSocket) {
                    this.signalingSocket.disconnect();
                }
            });
        },

        onMessage: function(callback) {
            this.onMessageCallback = callback;
        },

        onCall: function(callback) {
            this.onCallCallback = callback;
        },

        onOnlineStatus: function(callback) {
            this.onOnlineStatusCallback = callback;
        },

        onVoice: function(callback) {
            this.onVoicePrompt = callback;
        },

        onMedicationReminder: function(callback) {
            this.onMedicationReminder = callback;
        },

        onDeviceEvent: function(callback) {
            this.onDeviceEvent = callback;
        },

        sendMessage: function(msg) {
            if (this.isConnected) {
                this.socket.send(JSON.stringify(msg));
            } else {
                this.messageQueue.push(msg);
                console.log('[全局消息中心] 消息已加入队列');
            }
        },

        getOnlineStatus: function(userId) {
            return this.onlineUsers[userId] || false;
        },

        getAlertTypeName: function(alertType) {
            const alertTypeNames = {
                'BP': '血压',
                'HR': '心率',
                'BO': '血氧',
                'BG': '血糖'
            };
            return alertTypeNames[alertType] || alertType;
        },

        initMqttManager: function() {
            if (this.mqttInitialized) return;
            
            console.log('[全局消息中心] 检查全局MQTT管理器...');
            
            if (typeof GlobalMqttManager !== 'undefined') {
                console.log('[全局消息中心] 初始化全局MQTT管理器');
                
                if (!GlobalMqttManager.isConnected()) {
                    GlobalMqttManager.init();
                }
                
                GlobalMqttManager.onMessage((data) => {
                    console.log('[全局消息中心] 收到MQTT消息:', data);
                });
                
                GlobalMqttManager.onDeviceStatus((status) => {
                    console.log('[全局消息中心] 设备状态变更:', status);
                });
                
                this.mqttInitialized = true;
                console.log('[全局消息中心] 全局MQTT管理器初始化成功');
            } else {
                console.warn('[全局消息中心] 未找到全局MQTT管理器，跳过初始化');
            }
        }
    };

    const style = document.createElement('style');
    style.textContent = `
        #global-notification-container {
            position: fixed !important;
            top: 130px !important;
            left: 50% !important;
            transform: translateX(-50%) !important;
            z-index: 2147483647 !important;
        }

        .global-medication-alert-overlay {
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            right: 0 !important;
            bottom: 0 !important;
            background: rgba(0, 0, 0, 0.8) !important;
            display: flex !important;
            align-items: center !important;
            justify-content: center !important;
            z-index: 2147483647 !important;
        }

        .global-medication-alert-modal {
            background: white !important;
            border-radius: 24px !important;
            padding: 40px !important;
            max-width: 600px !important;
            width: 95% !important;
            text-align: center !important;
            animation: alertPulse 0.5s ease !important;
            z-index: 2147483647 !important;
        }

        .global-medication-alert-modal [style*="z-index"] {
            z-index: 2147483647 !important;
        }

        .global-notification {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px 40px;
            border-radius: 20px;
            box-shadow: 0 15px 40px rgba(102, 126, 234, 0.4);
            pointer-events: auto;
            cursor: pointer;
            animation: notificationSlide 0.5s ease-out;
            max-width: 600px;
            display: flex;
            align-items: center;
            gap: 20px;
        }

        @keyframes notificationSlide {
            from { opacity: 0; transform: translateY(-30px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .global-notification.fade-out {
            animation: fadeOut 0.3s ease-out forwards;
        }

        .global-notification-content {
            display: flex;
            align-items: center;
            gap: 20px;
        }

        .global-notification-avatar {
            width: 60px;
            height: 60px;
            border-radius: 50%;
            border: 3px solid white;
            object-fit: cover;
        }

        .global-notification-body {
            flex: 1;
            overflow: hidden;
        }

        .global-notification-sender {
            font-weight: 700;
            font-size: 20px;
            margin-bottom: 6px;
        }

        .global-notification-message {
            font-size: 16px;
            opacity: 0.9;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .global-notification-close {
            background: none;
            border: none;
            font-size: 24px;
            color: white;
            cursor: pointer;
            padding: 8px;
            line-height: 1;
            opacity: 0.8;
        }

        .global-notification-close:hover {
            opacity: 1;
        }

        #global-call-ui {
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            right: 0 !important;
            bottom: 0 !important;
            z-index: 2147483647 !important;
        }

        .global-call-overlay {
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.9);
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .global-call-content {
            text-align: center;
            color: white;
        }

        .global-call-avatar {
            width: 120px;
            height: 120px;
            border-radius: 50%;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 0 auto 30px;
            font-size: 50px;
            animation: ring 1.5s ease-in-out infinite;
            overflow: hidden;
        }

        .global-call-avatar img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .global-call-name {
            font-size: 32px;
            font-weight: 700;
            margin-bottom: 10px;
        }

        .global-call-type {
            font-size: 20px;
            color: rgba(255,255,255,0.8);
            margin-bottom: 40px;
        }

        .global-call-actions {
            display: flex;
            gap: 60px;
            justify-content: center;
        }

        .global-call-btn {
            width: 70px;
            height: 70px;
            border-radius: 50%;
            border: none;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 28px;
            color: white;
            transition: transform 0.2s;
        }

        .global-call-btn:hover {
            transform: scale(1.1);
        }

        .global-call-btn.reject {
            background: #ef4444;
        }

        .global-call-btn.accept {
            background: #22c55e;
        }

        @keyframes fadeOut {
            from {
                opacity: 1;
            }
            to {
                opacity: 0;
            }
        }

        @keyframes ring {
            0%, 100% { transform: scale(1); box-shadow: 0 0 0 0 rgba(102, 126, 234, 0.7); }
            50% { transform: scale(1.1); box-shadow: 0 0 0 20px rgba(102, 126, 234, 0); }
        }
    `;
    document.head.appendChild(style);

    global.GlobalMessageCenter = GlobalMessageCenter;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            GlobalMessageCenter.init();
        });
    } else {
        GlobalMessageCenter.init();
    }

})(window);
