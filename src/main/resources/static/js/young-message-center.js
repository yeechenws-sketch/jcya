(function(global) {
    'use strict';

    if (!document.querySelector('link[href*="font-awesome"]')) {
        const faLink = document.createElement('link');
        faLink.rel = 'stylesheet';
        faLink.href = 'https://cdn.jsdelivr.net/npm/font-awesome@4.7.0/css/font-awesome.min.css';
        document.head.appendChild(faLink);
    }

    const YoungMessageCenter = {
        socket: null,
        signalingSocket: null,
        currentUser: null,
        isConnected: false,
        reconnectAttempts: 0,
        maxReconnectAttempts: 10,
        reconnectDelay: 3000,
        messageQueue: [],
        contacts: [],
        elders: [],
        onlineUsers: {},

        notificationSound: null,
        ringSound: null,
        emergencyRingSound: null,

        onMessageCallback: null,
        onCallCallback: null,
        onOnlineStatusCallback: null,
        onElderUpdateCallback: null,
        onConnectionStatusCallback: null,

        hasNotificationPermission: false,

        init: async function() {
            console.log('[子女端消息中心] 初始化中...');
            
            await this.checkNotificationPermission();
            this.preloadSounds();
            await this.loadUserInfo();
            
            if (this.currentUser) {
                await this.loadElders();
                this.connectWebSocket();
                this.connectSignaling();
            }
            
            this.setupVisibilityHandler();
            this.setupBeforeUnloadHandler();
            
            console.log('[子女端消息中心] 初始化完成');
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
            console.log('[子女端消息中心] 通知权限:', this.hasNotificationPermission);
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
                        
                        this.notificationSound = new Audio(ringtones.message || 'http://t9se9imi4.hn-bkt.clouddn.com/notice.wav');
                        this.notificationSound.preload = 'auto';
                        
                        this.ringSound = new Audio(ringtones.ring || 'http://t9se9imi4.hn-bkt.clouddn.com/ring.mp3');
                        this.ringSound.preload = 'auto';
                        this.ringSound.loop = true;
                        
                        this.emergencyRingSound = new Audio(ringtones.emergency || 'http://t9se9imi4.hn-bkt.clouddn.com/warning.mp3');
                        this.emergencyRingSound.preload = 'auto';
                        this.emergencyRingSound.loop = true;
                        
                        console.log('[子女端消息中心] 铃声配置加载成功');
                        return;
                    }
                }
            } catch (err) {
                console.error('[子女端消息中心] 加载铃声配置失败，使用默认配置:', err);
            }
            
            this.notificationSound = new Audio('http://t9se9imi4.hn-bkt.clouddn.com/notice.wav');
            this.notificationSound.preload = 'auto';
            
            this.ringSound = new Audio('http://t9se9imi4.hn-bkt.clouddn.com/ring.mp3');
            this.ringSound.preload = 'auto';
            this.ringSound.loop = true;
            
            this.emergencyRingSound = new Audio('http://t9se9imi4.hn-bkt.clouddn.com/warning.mp3');
            this.emergencyRingSound.preload = 'auto';
            this.emergencyRingSound.loop = true;
        },

        loadUserInfo: async function() {
            try {
                const res = await axios.get('/api/user/info');
                if (res.data.code === 200) {
                    this.currentUser = res.data.data;
                    localStorage.setItem('user_id', this.currentUser.id);
                    localStorage.setItem('nickname', this.currentUser.nickname || '');
                    localStorage.setItem('user_role', this.currentUser.role || 'younger');
                    console.log('[子女端消息中心] 用户信息加载成功:', this.currentUser.nickname);
                }
            } catch (err) {
                console.error('[子女端消息中心] 加载用户信息失败:', err);
            }
        },

        loadElders: async function() {
            try {
                const res = await axios.get('/api/friend/list/ordered');
                if (res.data.code === 200) {
                    const friends = res.data.data || [];
                    this.elders = friends.filter(f => f.role === 'older');
                    this.contacts = friends;
                    console.log('[子女端消息中心] 长辈列表加载成功:', this.elders.length);
                }
            } catch (err) {
                console.error('[子女端消息中心] 加载长辈列表失败:', err);
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
                    console.log('[子女端消息中心] WebSocket 连接成功');
                    this.isConnected = true;
                    this.reconnectAttempts = 0;
                    this.flushMessageQueue();
                    if (this.onConnectionStatusCallback) {
                        this.onConnectionStatusCallback(true);
                    }
                };

                this.socket.onmessage = (e) => {
                    this.handleWebSocketMessage(JSON.parse(e.data));
                };

                this.socket.onclose = (event) => {
                    console.log('[子女端消息中心] WebSocket 连接关闭:', event.code);
                    this.isConnected = false;
                    if (this.onConnectionStatusCallback) {
                        this.onConnectionStatusCallback(false);
                    }
                    this.attemptReconnect();
                };

                this.socket.onerror = (error) => {
                    console.error('[子女端消息中心] WebSocket 错误:', error);
                    this.isConnected = false;
                    if (this.onConnectionStatusCallback) {
                        this.onConnectionStatusCallback(false);
                    }
                };

            } catch (error) {
                console.error('[子女端消息中心] WebSocket 初始化失败:', error);
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
                    console.log('[子女端消息中心] 信令服务器连接成功');
                    this.signalingSocket.emit('login', {
                        userId: this.currentUser.id,
                        nickname: this.currentUser.nickname || '用户',
                        avatar: this.currentUser.avatar || ''
                    });
                });

                this.signalingSocket.on('calling', (data) => {
                    console.log('[子女端消息中心] 收到来电:', data);
                    this.handleIncomingCall(data);
                });

                this.signalingSocket.on('hangup', () => {
                    console.log('[子女端消息中心] 对方挂断');
                    this.handleHangup();
                });

                this.signalingSocket.on('emergencyCallAlreadyAnswered', (data) => {
                    console.log('[子女端消息中心] 紧急呼叫已被他人接听:', data);
                    this.showEmergencyCallAlreadyAnswered(data);
                });

                this.signalingSocket.on('emergencyCallEnded', (data) => {
                    console.log('[子女端消息中心] 紧急呼叫已结束:', data);
                    this.handleHangup();
                });

                this.signalingSocket.on('disconnect', () => {
                    console.log('[子女端消息中心] 信令服务器断开');
                });

            } catch (error) {
                console.error('[子女端消息中心] 信令服务器连接失败:', error);
            }
        },

        attemptReconnect: function() {
            if (this.reconnectAttempts >= this.maxReconnectAttempts) {
                console.log('[子女端消息中心] 达到最大重连次数');
                return;
            }

            this.reconnectAttempts++;
            const delay = this.reconnectDelay * Math.min(this.reconnectAttempts, 5);

            console.log(`[子女端消息中心] ${delay/1000}秒后尝试第${this.reconnectAttempts}次重连`);

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
            console.log('[子女端消息中心] 收到消息:', m);

            if (m.msgType === 'USER_STATUS') {
                this.onlineUsers[m.userId] = m.online;
                if (this.onOnlineStatusCallback) {
                    this.onOnlineStatusCallback(m.userId, m.online);
                }
                return;
            }

            if (m.msgType === 'MEDICINE_DRAFT') {
                this.showMedicineDraftNotification(m);
                return;
            }

            if (m.msgType === 'MEDICINE_TAKEN') {
                this.showMedicineTakenNotification(m);
                return;
            }

            if (m.msgType === 'EMERGENCY_ALERT') {
                this.showEmergencyNotification(m);
                return;
            }

            if (m.msgType === 'HEALTH_ALERT') {
                this.showHealthAlertNotification(m);
                return;
            }

            if (m.msgType === 'SYSTEM_NOTIFICATION') {
                console.log('[子女端消息中心] 收到系统通知:', m);
                this.handleSystemNotification(m);
                return;
            }

            if (m.msgType === 'FRIEND_DELETE') {
                this.loadElders();
                return;
            }

            if (m.msgType === 'FRIEND_REQUEST' || m.msgType === 'FRIEND_ACCEPT') {
                this.loadElders();
                return;
            }

            if (m.msgType === 'EMERGENCY_CONTACT_ACCEPTED' || 
                m.msgType === 'EMERGENCY_CONTACT_REJECTED' ||
                m.msgType === 'BECOME_EMERGENCY_CONTACT' ||
                m.msgType === 'REMOVED_EMERGENCY_CONTACT') {
                if (this.onElderUpdateCallback) {
                    this.onElderUpdateCallback(m);
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
            console.log('[子女端消息中心] 处理收到的消息:', m);
            
            const senderId = m.fromId;
            const elder = this.elders.find(e => String(e.id) === String(senderId));
            const contact = this.contacts.find(c => String(c.id) === String(senderId));

            const senderName = elder ? (elder.remark || elder.nickname) : 
                              (contact ? (contact.remark || contact.nickname) : '长辈');
            const senderAvatar = elder ? elder.avatar : (contact ? contact.avatar : '');

            let messagePreview = m.content;
            if (m.msgType === 'voice') {
                messagePreview = `[语音] ${m.duration || ''}秒`;
            } else if (m.msgType === 'image') {
                messagePreview = '[图片]';
            } else if (m.msgType === 'video') {
                messagePreview = '[视频]';
            }

            this.playNotificationSound();
            this.showFloatingNotification(senderName, messagePreview, senderAvatar, m);

            if (document.hidden || !document.hasFocus()) {
                this.showSystemNotification(senderName, messagePreview, senderAvatar, m);
            }

            if (this.onMessageCallback) {
                this.onMessageCallback(m, elder || contact);
            }
        },

        handleIncomingCall: function(data) {
            console.log('[子女端消息中心] 处理来电:', data);
            console.log('[子女端消息中心] isEmergency:', data.isEmergency);

            if (data.isEmergency) {
                this.playEmergencyRingSound();
            } else {
                this.playRingSound();
            }

            const callerName = data.fromUserName || '长辈';
            const callerAvatar = data.fromUserAvatar || '';
            const callType = data.type || 'voice';

            if (document.hidden || !document.hasFocus()) {
                this.showCallNotification(callerName, callerAvatar, callType, data);
            }

            this.showIncomingCallUI(data);

            if (this.onCallCallback) {
                this.onCallCallback(data);
            }
        },

        handleHangup: function() {
            this.stopRingSound();
            this.hideCallUI();
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

        playEmergencyRingSound: function() {
            if (this.emergencyRingSound) {
                this.emergencyRingSound.currentTime = 0;
                this.emergencyRingSound.play().catch(e => console.log('播放紧急铃声失败:', e));
            }
        },

        stopRingSound: function() {
            if (this.ringSound) {
                this.ringSound.pause();
                this.ringSound.currentTime = 0;
            }
            if (this.emergencyRingSound) {
                this.emergencyRingSound.pause();
                this.emergencyRingSound.currentTime = 0;
            }
        },

        showFloatingNotification: function(title, message, avatar, messageData) {
            console.log('[子女端消息中心] 显示悬浮通知:', title, message);

            let container = document.getElementById('young-notification-container');
            if (!container) {
                container = document.createElement('div');
                container.id = 'young-notification-container';
                document.body.appendChild(container);
            }

            const notification = document.createElement('div');
            notification.className = 'young-notification';
            notification.innerHTML = `
                <div class="young-notification-icon">
                    <i class="fa fa-comment"></i>
                </div>
                <div class="young-notification-content">
                    <div class="young-notification-title">${title}</div>
                    <div class="young-notification-message">${message}</div>
                </div>
                <button class="young-notification-close" onclick="this.parentElement.remove()">
                    <i class="fa fa-times"></i>
                </button>
            `;

            notification.onclick = (e) => {
                if (e.target.closest('.young-notification-close')) return;
                this.navigateToMessage(messageData.fromId);
                notification.remove();
            };

            container.appendChild(notification);

            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 300);
            }, 4000);
        },

        showMedicineDraftNotification: function(m) {
            const elderName = m.elderName || '长辈';
            this.playNotificationSound();
            this.showFloatingNotification(
                '用药草稿待审核',
                `${elderName}新增了药品信息，请及时审核`,
                '',
                { fromId: m.elderId, msgType: 'medicine_draft' }
            );

            if (this.onElderUpdateCallback) {
                this.onElderUpdateCallback(m);
            }
        },

        showMedicineTakenNotification: function(m) {
            const elderName = m.elderName || '长辈';
            const medicineName = m.medicineName || '药品';
            this.playNotificationSound();
            this.showFloatingNotification(
                '服药提醒',
                `${elderName}已服用${medicineName}`,
                '',
                { fromId: m.elderId, msgType: 'medicine_taken' }
            );
        },

        showEmergencyNotification: function(m) {
            const elderName = m.elderName || '长辈';
            this.playNotificationSound();
            this.showFloatingNotification(
                '紧急情况提醒',
                `${elderName}触发了紧急呼叫，请立即关注！`,
                '',
                { fromId: m.elderId, msgType: 'emergency' }
            );

            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification('紧急情况提醒', {
                        body: `${elderName}触发了紧急呼叫，请立即关注！`,
                        icon: '/favicon.ico',
                        requireInteraction: true
                    });
                }
            }
        },

        showHealthAlertNotification: function(m) {
            const elderName = m.elderName || '长辈';
            const alertType = m.alertType || '健康异常';
            this.playNotificationSound();
            this.showFloatingNotification(
                '健康预警',
                `${elderName}的${alertType}数据异常，请关注`,
                '',
                { fromId: m.elderId, msgType: 'health_alert' }
            );
        },

        handleSystemNotification: function(m) {
            console.log('[子女端消息中心] 处理系统通知:', m);
            
            this.playNotificationSound();
            
            const title = m.title || '系统通知';
            const content = m.content || '';
            
            this.showFloatingNotification(
                title,
                content,
                '',
                { fromId: 'system', msgType: 'system' }
            );
            
            if (document.hidden || !document.hasFocus()) {
                if (this.hasNotificationPermission) {
                    new Notification(title, {
                        body: content,
                        icon: '/favicon.ico'
                    });
                }
            }
            
            if (this.onElderUpdateCallback) {
                this.onElderUpdateCallback(m);
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

            const isEmergency = callData.isEmergency;
            const typeName = isEmergency ? '紧急呼叫' : (callType === 'video' ? '视频通话' : '语音通话');
            const notification = new Notification(`${callerName} 邀请您${typeName}`, {
                body: isEmergency ? '紧急呼叫，请立即接听！' : '点击接听',
                icon: callerAvatar || '/favicon.ico',
                tag: 'call-' + callData.fromUserId,
                requireInteraction: true,
                data: callData
            });

            notification.onclick = () => {
                window.focus();
                notification.close();
            };

            // 对于普通呼叫，60秒后关闭通知并停止铃声
            // 对于紧急呼叫，不设置超时，持续等待
            if (!isEmergency) {
                setTimeout(() => {
                    notification.close();
                    this.stopRingSound();
                }, 60000);
            }
        },

        showIncomingCallUI: function(data) {
            let callUI = document.getElementById('young-call-ui');
            if (callUI) {
                callUI.remove();
            }

            const callerName = data.fromUserName || '长辈';
            const callerAvatar = data.fromUserAvatar || '';
            const callType = data.type || 'voice';
            const isEmergency = data.isEmergency === true || data.isEmergency === 'true';
            const typeName = isEmergency ? '紧急呼叫' : (callType === 'video' ? '视频通话' : '语音通话');
            
            // 获取预警信息
            const alertType = data.alertType || null;
            const alertValue = data.alertValue || null;
            const alertTypeName = (function() {
                const typeNames = {
                    'BP': '血压',
                    'HR': '心率',
                    'BO': '血氧',
                    'BG': '血糖'
                };
                return typeNames[alertType] || alertType || '未知';
            })();
            
            // 判断是自动还是手动呼叫：如果有alertType就是自动呼叫
            const isAutoCall = alertType !== null;

            callUI = document.createElement('div');
            callUI.id = 'young-call-ui';
            callUI.innerHTML = `
                <div class="young-call-overlay ${isEmergency ? 'emergency' : ''}">
                    ${isEmergency ? '<div class="emergency-decoration"></div>' : ''}
                    <div class="young-call-content">
                        ${isEmergency ? '<div class="emergency-badge">紧急</div>' : ''}
                        <div class="young-call-avatar ${isEmergency ? 'emergency' : ''}">
                            ${callerAvatar ? `<img src="${callerAvatar}" alt="${callerName}">` : '<i class="fa fa-user"></i>'}
                        </div>
                        <div class="young-call-name">${callerName}</div>
                        <div class="young-call-type ${isEmergency ? 'emergency' : ''}">${typeName}</div>
                        ${isEmergency ? `
                            <div class="young-call-status emergency">
                                <i class="fa fa-exclamation-triangle" style="margin-right: 8px;"></i>
                                长辈发起紧急呼叫，请立即接听！
                            </div>
                            <div class="emergency-type-badge">
                                ${isAutoCall ? '自动呼叫' : '手动呼叫'}
                            </div>
                            ${alertType ? `
                                <div class="emergency-alert-info">
                                    异常类型：${alertTypeName}，当前值：${alertValue !== null ? alertValue : '--'}
                                </div>
                            ` : ''}
                        ` : ''}
                        <div class="young-call-actions">
                            <button class="young-call-btn reject" onclick="YoungMessageCenter.rejectCall()">
                                <i class="fa fa-phone" style="transform: rotate(135deg);"></i>
                                <span>拒绝</span>
                            </button>
                            <button class="young-call-btn accept ${isEmergency ? 'emergency' : ''}" onclick="YoungMessageCenter.acceptCall()">
                                <i class="fa fa-phone"></i>
                                <span>${isEmergency ? '立即接听' : '接听'}</span>
                            </button>
                        </div>
                    </div>
                </div>
            `;

            document.body.appendChild(callUI);
            callUI.callData = data;
        },

        hideCallUI: function() {
            const callUI = document.getElementById('young-call-ui');
            if (callUI) {
                callUI.remove();
            }
            this.stopRingSound();
        },

        showEmergencyCallAlreadyAnswered: function(data) {
            console.log('[子女端消息中心] 显示紧急呼叫已被接听提示:', data);
            this.stopRingSound();
            
            // 如果有来电UI，隐藏它
            const callUI = document.getElementById('young-call-ui');
            if (callUI) {
                callUI.remove();
            }
            
            // 显示提示
            this.showFloatingNotification(
                '紧急呼叫已被接听',
                `${data.answeredByName || '联系人'} 已接听紧急呼叫`,
                '',
                { fromId: data.answeredBy, msgType: 'emergency_answered' }
            );
        },

        acceptCall: function() {
            const callUI = document.getElementById('young-call-ui');
            if (callUI && callUI.callData) {
                const data = callUI.callData;
                console.log('[子女端消息中心] 接听呼叫:', data);
                this.stopRingSound();
                this.hideCallUI();

                if (this.signalingSocket) {
                    console.log('[子女端消息中心] 发送answerCall事件, toUserId:', data.fromUserId);
                    this.signalingSocket.emit('answerCall', {
                        toUserId: data.fromUserId,
                        fromUserId: this.currentUser.id
                    });
                } else {
                    console.error('[子女端消息中心] signalingSocket未连接!');
                }

                const myName = this.currentUser?.nickname || '用户';
                const myAvatar = this.currentUser?.avatar || '';
                const peerAvatar = data.fromUserAvatar || '';

                const url = `call.html?userId=${this.currentUser.id}&peerId=${data.fromUserId}&peerName=${encodeURIComponent(data.fromUserName)}&peerAvatar=${encodeURIComponent(peerAvatar)}&type=${data.type}&incoming=true&answered=true&myName=${encodeURIComponent(myName)}&myAvatar=${encodeURIComponent(myAvatar)}&isEmergency=${data.isEmergency || false}`;
                console.log('[子女端消息中心] 跳转到通话页面:', url);
                window.location.href = url;
            }
        },

        rejectCall: function() {
            const callUI = document.getElementById('young-call-ui');
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

        navigateToMessage: function(fromId) {
            const currentPage = window.location.pathname.split('/').pop();
            if (currentPage === 'young_msg.html') {
                const event = new CustomEvent('selectContact', { detail: { contactId: fromId } });
                window.dispatchEvent(event);
            } else {
                window.location.href = `young_msg.html?targetUserId=${fromId}`;
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

        onElderUpdate: function(callback) {
            this.onElderUpdateCallback = callback;
        },

        onConnectionStatus: function(callback) {
            this.onConnectionStatusCallback = callback;
        },

        sendMessage: function(msg) {
            if (this.isConnected) {
                this.socket.send(JSON.stringify(msg));
            } else {
                this.messageQueue.push(msg);
                console.log('[子女端消息中心] 消息已加入队列');
            }
        },

        getOnlineStatus: function(userId) {
            return this.onlineUsers[userId] || false;
        },

        getElders: function() {
            return this.elders;
        }
    };

    const style = document.createElement('style');
    style.textContent = `
        #young-notification-container {
            position: fixed !important;
            top: 80px !important;
            right: 20px !important;
            z-index: 2147483647 !important;
            display: flex;
            flex-direction: column;
            gap: 12px;
            pointer-events: none;
            max-width: 360px;
        }

        .young-notification {
            background: linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%);
            color: white;
            padding: 16px 20px;
            border-radius: 16px;
            box-shadow: 0 8px 32px rgba(37, 99, 235, 0.4);
            pointer-events: auto;
            cursor: pointer;
            animation: youngNotificationSlide 0.3s ease-out;
            display: flex;
            align-items: center;
            gap: 14px;
            backdrop-filter: blur(10px);
        }

        @keyframes youngNotificationSlide {
            from { 
                opacity: 0; 
                transform: translateX(100px); 
            }
            to { 
                opacity: 1; 
                transform: translateX(0); 
            }
        }

        .young-notification.fade-out {
            animation: youngNotificationFadeOut 0.3s ease-out forwards;
        }

        @keyframes youngNotificationFadeOut {
            from {
                opacity: 1;
                transform: translateX(0);
            }
            to {
                opacity: 0;
                transform: translateX(100px);
            }
        }

        .young-notification-icon {
            width: 44px;
            height: 44px;
            border-radius: 12px;
            background: rgba(255, 255, 255, 0.2);
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
        }

        .young-notification-icon i {
            font-size: 20px;
            color: white;
        }

        .young-notification-content {
            flex: 1;
            overflow: hidden;
            min-width: 0;
        }

        .young-notification-title {
            font-weight: 700;
            font-size: 15px;
            margin-bottom: 4px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .young-notification-message {
            font-size: 13px;
            opacity: 0.9;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .young-notification-close {
            background: rgba(255, 255, 255, 0.2);
            border: none;
            width: 28px;
            height: 28px;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            flex-shrink: 0;
            transition: background 0.2s;
        }

        .young-notification-close:hover {
            background: rgba(255, 255, 255, 0.3);
        }

        .young-notification-close i {
            font-size: 14px;
            color: white;
        }

        #young-call-ui {
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            right: 0 !important;
            bottom: 0 !important;
            z-index: 2147483647 !important;
        }

        .young-call-overlay {
            width: 100%;
            height: 100%;
            background: linear-gradient(135deg, #1E293B 0%, #0F172A 100%);
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .young-call-overlay.emergency {
            background: linear-gradient(135deg, rgba(220, 38, 38, 0.95) 0%, rgba(239, 68, 68, 0.9) 100%);
            animation: emergency-bg-pulse 2s infinite;
        }

        @keyframes emergency-bg-pulse {
            0%, 100% { background: linear-gradient(135deg, rgba(220, 38, 38, 0.95) 0%, rgba(239, 68, 68, 0.9) 100%); }
            50% { background: linear-gradient(135deg, rgba(220, 38, 38, 0.85) 0%, rgba(239, 68, 68, 0.8) 100%); }
        }

        .young-call-content {
            text-align: center;
            color: white;
        }

        .young-call-avatar {
            width: 100px;
            height: 100px;
            border-radius: 50%;
            background: linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%);
            display: flex;
            align-items: center;
            justify-content: center;
            margin: 0 auto 24px;
            font-size: 40px;
            animation: youngCallRing 1.5s ease-in-out infinite;
            overflow: hidden;
            box-shadow: 0 0 40px rgba(37, 99, 235, 0.5);
        }

        .young-call-avatar img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }

        .young-call-name {
            font-size: 26px;
            font-weight: 700;
            margin-bottom: 8px;
        }

        .young-call-type {
            font-size: 16px;
            color: rgba(255,255,255,0.7);
            margin-bottom: 40px;
        }

        .young-call-actions {
            display: flex;
            gap: 50px;
            justify-content: center;
        }

        .young-call-btn {
            width: 64px;
            height: 64px;
            border-radius: 50%;
            border: none;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            font-size: 24px;
            color: white;
            transition: transform 0.2s, box-shadow 0.2s;
        }

        .young-call-btn:hover {
            transform: scale(1.1);
        }

        .young-call-btn.reject {
            background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);
            box-shadow: 0 8px 24px rgba(239, 68, 68, 0.4);
        }

        .young-call-btn.accept {
            background: linear-gradient(135deg, #10B981 0%, #059669 100%);
            box-shadow: 0 8px 24px rgba(16, 185, 129, 0.4);
        }

        .young-call-btn span {
            font-size: 12px;
            margin-top: 4px;
        }

        .emergency-decoration {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            pointer-events: none;
            overflow: hidden;
        }

        .emergency-decoration::before,
        .emergency-decoration::after {
            content: '';
            position: absolute;
            width: 200%;
            height: 200%;
            top: -50%;
            left: -50%;
            background: conic-gradient(from 0deg, transparent 0deg, rgba(255, 255, 255, 0.05) 30deg, transparent 60deg);
            animation: emergency-spin 3s linear infinite;
        }

        .emergency-decoration::after {
            animation-direction: reverse;
            animation-duration: 5s;
        }

        @keyframes emergency-spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
        }

        .emergency-badge {
            position: absolute;
            top: 30px;
            left: 50%;
            transform: translateX(-50%);
            background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);
            color: white;
            padding: 10px 24px;
            border-radius: 30px;
            font-size: 18px;
            font-weight: 800;
            box-shadow: 0 6px 20px rgba(239, 68, 68, 0.5);
            z-index: 10;
            animation: emergency-badge-pulse 1.5s ease-in-out infinite;
        }

        @keyframes emergency-badge-pulse {
            0%, 100% { 
                transform: translateX(-50%) scale(1);
                box-shadow: 0 6px 20px rgba(239, 68, 68, 0.5);
            }
            50% { 
                transform: translateX(-50%) scale(1.05);
                box-shadow: 0 8px 30px rgba(239, 68, 68, 0.7);
            }
        }

        .young-call-avatar.emergency {
            background: linear-gradient(135deg, #EF4444 0%, #DC2626 100%);
            box-shadow: 0 0 50px rgba(239, 68, 68, 0.7);
            animation: emergency-avatar-ring 1.2s ease-in-out infinite;
        }

        @keyframes emergency-avatar-ring {
            0%, 100% { 
                transform: scale(1); 
                box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.8), 0 0 50px rgba(239, 68, 68, 0.7); 
            }
            50% { 
                transform: scale(1.1); 
                box-shadow: 0 0 0 25px rgba(239, 68, 68, 0), 0 0 80px rgba(239, 68, 68, 0.9); 
            }
        }

        .young-call-type.emergency {
            color: #FEE2E2;
            font-size: 20px;
            font-weight: 700;
            text-shadow: 0 2px 10px rgba(0,0,0,0.3);
        }

        .young-call-status.emergency {
            background: rgba(255, 255, 255, 0.15);
            backdrop-filter: blur(10px);
            border-radius: 16px;
            padding: 16px 24px;
            margin: 20px auto;
            max-width: 400px;
            font-size: 17px;
            font-weight: 600;
            color: #FEF2F2;
            border: 1px solid rgba(255, 255, 255, 0.2);
            box-shadow: 0 4px 20px rgba(0,0,0,0.2);
        }

        .emergency-type-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: rgba(255, 255, 255, 0.2);
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.3);
            color: #FEFCE8;
            padding: 10px 20px;
            border-radius: 20px;
            font-size: 15px;
            font-weight: 700;
            margin-bottom: 10px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.2);
        }

        .emergency-alert-info {
            background: rgba(255, 255, 255, 0.15);
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255, 255, 255, 0.25);
            color: #FEFCE8;
            padding: 12px 20px;
            border-radius: 15px;
            font-size: 16px;
            font-weight: 600;
            margin-bottom: 20px;
            box-shadow: 0 4px 15px rgba(0,0,0,0.15);
        }

        .young-call-btn.accept.emergency {
            background: linear-gradient(135deg, #10B981 0%, #059669 100%);
            box-shadow: 0 10px 30px rgba(16, 185, 129, 0.5);
            animation: emergency-accept-pulse 1s ease-in-out infinite;
        }

        @keyframes emergency-accept-pulse {
            0%, 100% { 
                transform: scale(1);
                box-shadow: 0 10px 30px rgba(16, 185, 129, 0.5);
            }
            50% { 
                transform: scale(1.1);
                box-shadow: 0 12px 40px rgba(16, 185, 129, 0.7);
            }
        }

        @keyframes youngCallRing {
            0%, 100% { 
                transform: scale(1); 
                box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.7), 0 0 40px rgba(37, 99, 235, 0.5); 
            }
            50% { 
                transform: scale(1.08); 
                box-shadow: 0 0 0 15px rgba(37, 99, 235, 0), 0 0 60px rgba(37, 99, 235, 0.6); 
            }
        }

        @media (max-width: 480px) {
            #young-notification-container {
                left: 16px !important;
                right: 16px !important;
                max-width: none;
            }

            .young-notification {
                padding: 14px 16px;
            }

            .young-notification-icon {
                width: 40px;
                height: 40px;
            }

            .young-notification-title {
                font-size: 14px;
            }

            .young-notification-message {
                font-size: 12px;
            }

            .young-call-avatar {
                width: 80px;
                height: 80px;
                font-size: 32px;
            }

            .young-call-name {
                font-size: 22px;
            }

            .young-call-btn {
                width: 56px;
                height: 56px;
                font-size: 20px;
            }
        }
    `;
    document.head.appendChild(style);

    global.YoungMessageCenter = YoungMessageCenter;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            YoungMessageCenter.init();
        });
    } else {
        YoungMessageCenter.init();
    }

})(window);
