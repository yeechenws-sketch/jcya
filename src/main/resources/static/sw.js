const CACHE_NAME = 'elderly-care-v1';
const NOTIFICATION_TAG = 'elderly-care-notification';

self.addEventListener('install', (event) => {
    console.log('[ServiceWorker] 安装中');
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    console.log('[ServiceWorker] 激活中');
    event.waitUntil(clients.claim());
});

self.addEventListener('push', (event) => {
    console.log('[ServiceWorker] 收到推送消息');
    
    let data = {};
    if (event.data) {
        data = event.data.json();
    }

    const title = data.title || '新消息';
    const options = {
        body: data.body || '您有一条新消息',
        icon: data.icon || '/favicon.ico',
        badge: '/favicon.ico',
        tag: NOTIFICATION_TAG + '-' + (data.tag || Date.now()),
        requireInteraction: data.requireInteraction || false,
        data: data.data || {}
    };

    event.waitUntil(
        self.registration.showNotification(title, options)
    );
});

self.addEventListener('notificationclick', (event) => {
    console.log('[ServiceWorker] 通知被点击');
    
    event.notification.close();

    const data = event.notification.data;
    let url = '/';

    if (data.type === 'message' && data.fromId) {
        url = `/msg.html?targetUserId=${data.fromId}`;
    } else if (data.type === 'call') {
        url = `/msg.html`;
    }

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true })
            .then((clientList) => {
                for (const client of clientList) {
                    if (client.url.includes(url) && 'focus' in client) {
                        return client.focus();
                    }
                }
                if (clients.openWindow) {
                    return clients.openWindow(url);
                }
            })
    );
});

self.addEventListener('message', (event) => {
    console.log('[ServiceWorker] 收到消息:', event.data);

    if (event.data.type === 'SHOW_NOTIFICATION') {
        const data = event.data.payload;
        
        self.registration.showNotification(data.title, {
            body: data.body,
            icon: data.icon || '/favicon.ico',
            tag: NOTIFICATION_TAG + '-' + Date.now(),
            requireInteraction: data.requireInteraction || false,
            data: data.data || {}
        });
    }

    if (event.data.type === 'SHOW_CALL_NOTIFICATION') {
        const data = event.data.payload;
        
        self.registration.showNotification(data.title, {
            body: data.body,
            icon: data.icon || '/favicon.ico',
            tag: 'call-' + data.fromUserId,
            requireInteraction: true,
            data: {
                type: 'call',
                fromUserId: data.fromUserId,
                callType: data.callType
            }
        });
    }
});
