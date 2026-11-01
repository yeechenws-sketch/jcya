(function(global) {
    'use strict';

    if (!document.querySelector('link[href*="font-awesome"]')) {
        const faLink = document.createElement('link');
        faLink.rel = 'stylesheet';
        faLink.href = 'https://cdn.jsdelivr.net/npm/font-awesome@4.7.0/css/font-awesome.min.css';
        document.head.appendChild(faLink);
    }

    const AdminMessageCenter = {
        socket: null,
        currentUser: null,
        isConnected: false,
        reconnectAttempts: 0,
        maxReconnectAttempts: 10,
        reconnectDelay: 3000,
        contacts: [],
        onlineUsers: {},

        notificationSound: null,

        onMessageCallback: null,
        onOnlineStatusCallback: null,

        hasNotificationPermission: false,
        suppressInAppNotification: false,

        init: async function() {
            console.log('[社区端消息中心] 初始化中...');
            
            await this.checkNotificationPermission();
            this.preloadSounds();
            await this.loadUserInfo();
            
            if (this.currentUser) {
                await this.loadContacts();
                this.connectWebSocket();
            }
            
            this.setupVisibilityHandler();
            this.setupBeforeUnloadHandler();
            
            console.log('[社区端消息中心] 初始化完成');
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
            console.log('[社区端消息中心] 通知权限:', this.hasNotificationPermission);
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
                        
                        console.log('[社区端消息中心] 铃声配置加载成功');
                        return;
                    }
                }
            } catch (err) {
                console.error('[社区端消息中心] 加载铃声配置失败，使用默认配置:', err);
            }
            
            this.notificationSound = new Audio('http://t9se9imi4.hn-bkt.clouddn.com/notice.wav');
            this.notificationSound.preload = 'auto';
        },

        loadUserInfo: async function() {
            try {
                const res = await axios.get('/api/user/info');
                if (res.data.code === 200) {
                    this.currentUser = res.data.data;
                    localStorage.setItem('user_id', this.currentUser.id);
                    localStorage.setItem('nickname', this.currentUser.nickname || '');
                    localStorage.setItem('user_role', this.currentUser.role || 'admin');
                    console.log('[社区端消息中心] 用户信息加载成功:', this.currentUser.nickname);
                }
            } catch (err) {
                console.error('[社区端消息中心] 加载用户信息失败:', err);
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
                    console.log('[社区端消息中心] WebSocket 连接成功');
                    this.isConnected = true;
                    this.reconnectAttempts = 0;
                    this.updateConnectionStatus('connected');
                };

                this.socket.onmessage = (e) => {
                    this.handleWebSocketMessage(JSON.parse(e.data));
                };

                this.socket.onclose = (event) => {
                    console.log('[社区端消息中心] WebSocket 连接关闭:', event.code);
                    this.isConnected = false;
                    this.updateConnectionStatus('disconnected');
                    this.attemptReconnect();
                };

                this.socket.onerror = (error) => {
                    console.error('[社区端消息中心] WebSocket 错误:', error);
                    this.isConnected = false;
                    this.updateConnectionStatus('disconnected');
                };

            } catch (error) {
                console.error('[社区端消息中心] WebSocket 初始化失败:', error);
                this.attemptReconnect();
            }
        },

        attemptReconnect: function() {
            if (this.reconnectAttempts >= this.maxReconnectAttempts) {
                console.log('[社区端消息中心] 达到最大重连次数');
                return;
            }

            this.reconnectAttempts++;
            const delay = this.reconnectDelay * Math.min(this.reconnectAttempts, 5);

            console.log(`[社区端消息中心] ${delay/1000}秒后尝试第${this.reconnectAttempts}次重连`);
            this.updateConnectionStatus('connecting');

            setTimeout(() => {
                if (!this.isConnected) {
                    this.connectWebSocket();
                }
            }, delay);
        },

        updateConnectionStatus: function(status) {
            const statusEl = document.getElementById('admin-ws-status');
            if (statusEl) {
                const dot = statusEl.querySelector('.admin-ws-dot');
                const text = statusEl.querySelector('.admin-ws-text');
                if (dot) {
                    dot.className = 'admin-ws-dot ' + status;
                }
                if (text) {
                    const map = { 'connected': '已连接', 'disconnected': '已断开', 'connecting': '连接中' };
                    text.textContent = map[status] || '未知';
                }
            }
        },

        handleWebSocketMessage: function(m) {
            console.log('[社区端消息中心] 收到消息:', m);

            if (m.msgType === 'USER_STATUS') {
                this.onlineUsers[m.userId] = m.online;
                if (this.onOnlineStatusCallback) {
                    this.onOnlineStatusCallback(m.userId, m.online);
                }
                return;
            }

            if (m.msgType === 'FRIEND_DELETE') {
                this.loadContacts();
                window.dispatchEvent(new CustomEvent('admin-friend-delete', { detail: m }));
                return;
            }

            if (m.msgType === 'FRIEND_REQUEST' || m.msgType === 'FRIEND_ACCEPT') {
                this.loadContacts();
                this.showFriendRequestNotification(m);
                window.dispatchEvent(new CustomEvent('admin-friend-update', { detail: m }));
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
            console.log('[社区端消息中心] 处理收到的消息:', m);
            
            const senderId = m.fromId;
            const contact = this.contacts.find(c => String(c.id) === String(senderId));

            const senderName = contact ? (contact.remark || contact.nickname) : '住户';
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

        playNotificationSound: function() {
            if (this.notificationSound) {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => console.log('播放提示音失败:', e));
            }
        },

        showSystemNotification: function(title, body, icon, messageData) {
            if (!this.hasNotificationPermission) return;

            const notification = new Notification(title, {
                body: body,
                icon: icon || '/favicon.ico',
                tag: 'admin-message-' + messageData.fromId,
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

        showInAppNotification: function(senderName, message, avatar, messageData) {
            console.log('[社区端消息中心] 显示应用内通知:', senderName, message);

            let container = document.getElementById('admin-notification-container');
            if (!container) {
                container = document.createElement('div');
                container.id = 'admin-notification-container';
                document.body.appendChild(container);
                console.log('[社区端消息中心] 创建通知容器');
            }

            const notification = document.createElement('div');
            notification.className = 'admin-notification';
            notification.innerHTML = `
                <img class="admin-notification-avatar" src="${avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + messageData.fromId}" alt="">
                <div class="admin-notification-body">
                    <div class="admin-notification-sender">${senderName}</div>
                    <div class="admin-notification-message">${message}</div>
                </div>
                <button class="admin-notification-close" onclick="this.parentElement.remove()"><i class="fa fa-times"></i></button>
            `;

            notification.onclick = (e) => {
                if (e.target.closest('.admin-notification-close')) return;
                this.navigateToMessage(messageData.fromId);
                notification.remove();
            };

            container.appendChild(notification);
            console.log('[社区端消息中心] 通知已添加到DOM');

            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 300);
            }, 5000);
        },

        showFriendRequestNotification: function(m) {
            this.showInAppNotification('系统', '新的好友申请', '', { fromId: 'system', msgType: 'friend_request' });
        },

        navigateToMessage: function(fromId) {
            const currentPage = window.location.pathname.split('/').pop();
            if (currentPage === 'admin_msg.html') {
                const event = new CustomEvent('selectContact', { detail: { contactId: fromId } });
                window.dispatchEvent(event);
            } else {
                window.location.href = `admin_msg.html?targetUserId=${fromId}`;
            }
        },

        loadContacts: async function() {
            try {
                const res = await axios.get('/api/friend/list/ordered');
                if (res.data.code === 200) {
                    this.contacts = res.data.data || [];
                }
            } catch (err) {
                console.error('[社区端消息中心] 加载联系人失败:', err);
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
            });
        },

        onMessage: function(callback) {
            this.onMessageCallback = callback;
        },

        onOnlineStatus: function(callback) {
            this.onOnlineStatusCallback = callback;
        },

        sendMessage: function(msg) {
            if (this.isConnected) {
                this.socket.send(JSON.stringify(msg));
            } else {
                console.log('[社区端消息中心] WebSocket未连接，无法发送消息');
            }
        },

        getOnlineStatus: function(userId) {
            return this.onlineUsers[userId] || false;
        },

        getConnectionStatus: function() {
            return this.isConnected ? 'connected' : 'disconnected';
        }
    };

    const style = document.createElement('style');
    style.textContent = `
        #admin-notification-container {
            position: fixed !important;
            top: 100px !important;
            left: 50% !important;
            transform: translateX(-50%) !important;
            z-index: 2147483647 !important;
            display: flex;
            flex-direction: column;
            gap: 10px;
            pointer-events: none;
        }

        .admin-notification {
            background: linear-gradient(135deg, #1d5a8b 0%, #0a3b62 100%);
            color: white;
            padding: 16px 28px;
            border-radius: 16px;
            box-shadow: 0 12px 32px rgba(29, 90, 139, 0.4);
            pointer-events: auto;
            cursor: pointer;
            animation: adminNotificationSlide 0.4s ease-out;
            max-width: 500px;
            display: flex;
            align-items: center;
            gap: 16px;
            border: 1px solid rgba(255, 255, 255, 0.15);
        }

        @keyframes adminNotificationSlide {
            from { opacity: 0; transform: translateY(-20px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .admin-notification.fade-out {
            animation: adminFadeOut 0.3s ease-out forwards;
        }

        @keyframes adminFadeOut {
            from { opacity: 1; transform: translateY(0); }
            to { opacity: 0; transform: translateY(-10px); }
        }

        .admin-notification-avatar {
            width: 50px;
            height: 50px;
            border-radius: 50%;
            border: 2px solid rgba(255, 255, 255, 0.3);
            object-fit: cover;
            flex-shrink: 0;
        }

        .admin-notification-body {
            flex: 1;
            overflow: hidden;
        }

        .admin-notification-sender {
            font-weight: 700;
            font-size: 1rem;
            margin-bottom: 4px;
        }

        .admin-notification-message {
            font-size: 0.9rem;
            opacity: 0.9;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        .admin-notification-close {
            background: rgba(255, 255, 255, 0.15);
            border: none;
            font-size: 1.1rem;
            color: white;
            cursor: pointer;
            padding: 6px 10px;
            line-height: 1;
            opacity: 0.8;
            border-radius: 50%;
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: all 0.2s;
            flex-shrink: 0;
        }

        .admin-notification-close:hover {
            opacity: 1;
            background: rgba(255, 255, 255, 0.25);
        }

        #admin-ws-status {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 0.8rem;
            color: #a8ceff;
            margin-left: 15px;
        }

        .admin-ws-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            animation: adminPulse 1.5s infinite;
        }

        .admin-ws-dot.connected { background: #22c55e; }
        .admin-ws-dot.disconnected { background: #ef4444; animation: none; }
        .admin-ws-dot.connecting { background: #f59e0b; }

        @keyframes adminPulse { 
            0%, 100% { opacity: 1; } 
            50% { opacity: 0.5; } 
        }
    `;
    document.head.appendChild(style);

    global.AdminMessageCenter = AdminMessageCenter;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            AdminMessageCenter.init();
        });
    } else {
        AdminMessageCenter.init();
    }

})(window);
