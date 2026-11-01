(function(global) {
    'use strict';

    const GlobalMqttManager = {
        mqttClient: null,
        connected: false,
        deviceId: 'BOX001',
        serverUrl: 'wss://xiechen.xyz/mqtt',
        deviceOnline: false,
        lastHeartbeat: null,
        deviceData: {
            weight: 0,
            lastWeight: null,
            weightChange: 0,
            bottlePresent: true,
            initStep: 3,
            singleDoseWeight: null,
            fullBottleWeight: null,
            toleranceMin: null,
            toleranceMax: null
        },
        boundMedicine: null,
        heartbeatCheckTimer: null,
        onMessageCallback: null,
        onDeviceStatusCallback: null,

        init: function() {
            console.log('[全局MQTT管理器] 初始化中...');
            this.loadDeviceBinding();
            this.startHeartbeatCheck();
            this.autoConnect();
        },

        loadDeviceBinding: function() {
            try {
                const savedMedicine = localStorage.getItem('boundMedicine');
                const savedInitStep = localStorage.getItem('deviceInitStep');
                const savedDoseWeight = localStorage.getItem('singleDoseWeight');

                if (savedMedicine) {
                    this.boundMedicine = JSON.parse(savedMedicine);
                }

                if (savedInitStep) {
                    this.deviceData.initStep = parseInt(savedInitStep) || 3;
                }

                if (savedDoseWeight) {
                    this.deviceData.singleDoseWeight = parseFloat(savedDoseWeight) || null;
                }

                console.log('[全局MQTT管理器] 已加载设备绑定');
            } catch (e) {
                console.error('[全局MQTT管理器] 加载设备绑定失败:', e);
            }
        },

        autoConnect: function() {
            if (!this.mqttClient) {
                console.log('[全局MQTT管理器] 自动连接中...');
                this.connect();
            }
        },

        connect: function() {
            if (this.mqttClient) {
                this.mqttClient.end();
                this.mqttClient = null;
            }

            const self = this;

            try {
                const options = {
                    clientId: 'global-mqtt-client-' + Date.now(),
                    clean: true,
                    connectTimeout: 10000,
                    reconnectPeriod: 5000
                };

                console.log('[全局MQTT管理器] 正在连接:', this.serverUrl);

                if (typeof mqtt === 'undefined') {
                    console.warn('[全局MQTT管理器] MQTT库未加载，跳过连接');
                    return;
                }

                this.mqttClient = mqtt.connect(this.serverUrl, options);

                this.mqttClient.on('connect', () => {
                    self.connected = true;
                    console.log('[全局MQTT管理器] MQTT服务器已连接');

                    const subscribeTopic = `medicine/box/${self.deviceId}/data`;
                    self.mqttClient.subscribe(subscribeTopic, (err) => {
                        if (err) {
                            console.error('[全局MQTT管理器] 订阅失败:', err.message);
                        } else {
                            console.log('[全局MQTT管理器] 已订阅:', subscribeTopic);
                        }
                    });
                });

                this.mqttClient.on('message', (topic, message) => {
                    try {
                        const data = JSON.parse(message.toString());
                        console.log('[全局MQTT管理器] 收到消息:', data);
                        self.handleMessage(data);
                    } catch (e) {
                        console.error('[全局MQTT管理器] 解析错误:', e);
                    }
                });

                this.mqttClient.on('error', (err) => {
                    self.connected = false;
                    console.error('[全局MQTT管理器] MQTT错误:', err.message);
                });

                this.mqttClient.on('close', () => {
                    self.connected = false;
                    console.log('[全局MQTT管理器] MQTT已断开');
                });

                this.mqttClient.on('reconnect', () => {
                    console.log('[全局MQTT管理器] 正在重新连接...');
                });
            } catch (e) {
                console.error('[全局MQTT管理器] 连接失败:', e.message);
            }
        },

        disconnect: function() {
            if (this.mqttClient) {
                this.mqttClient.end();
                this.mqttClient = null;
            }
            this.connected = false;
            console.log('[全局MQTT管理器] 已断开连接');
        },

        handleMessage: function(data) {
            const wasOffline = !this.deviceOnline;
            this.lastHeartbeat = Date.now();
            this.deviceOnline = true;

            if (wasOffline) {
                console.log('[全局MQTT管理器] 设备已上线');
            }

            if (data.eventType === 'weight_change' || data.eventType === 'heartbeat') {
                let currentWeight = data.weightAfter || data.weight || 0;
                const previousWeight = this.deviceData.weight;

                if (typeof currentWeight === 'string') {
                    currentWeight = parseFloat(currentWeight);
                }

                const newBottlePresent = data.bottlePresent !== undefined 
                    ? data.bottlePresent 
                    : currentWeight > 5;

                this.deviceData = {
                    ...this.deviceData,
                    weight: currentWeight,
                    weightChange: currentWeight - previousWeight,
                    bottlePresent: newBottlePresent
                };

                this.checkAndHandleDoseChange(currentWeight, previousWeight);
                this.deviceData.lastWeight = currentWeight;
            } else if (data.eventType === 'bottle_removed') {
                this.deviceData.bottlePresent = false;
                this.deviceData.weightBeforeRemove = this.deviceData.weight;
                this.deviceData.lastWeight = this.deviceData.weight;
                this.deviceData.weight = 0;
                console.log('[全局MQTT管理器] 瓶子已移除');
            } else if (data.eventType === 'bottle_returned') {
                this.deviceData.bottlePresent = true;
                const returnedWeight = data.weightAfter || 0;
                const previousWeight = this.deviceData.weightBeforeRemove || 0;
                this.deviceData.weight = returnedWeight;
                this.deviceData.lastWeight = returnedWeight;
                this.deviceData.weightBeforeRemove = null;
                
                if (this.deviceData.initStep >= 3 && previousWeight > 0 && this.deviceData.singleDoseWeight) {
                    const dose = this.deviceData.singleDoseWeight;
                    const minDetectThreshold = dose * 0.3;
                    const weightDiff = previousWeight - returnedWeight;

                    if (Math.abs(weightDiff) > minDetectThreshold) {
                        if (weightDiff > 0) {
                            this.checkRealDoseChange(weightDiff, previousWeight, returnedWeight);
                        } else {
                            this.checkRealRefill(-weightDiff, previousWeight, returnedWeight);
                        }
                    }
                }
                
                console.log('[全局MQTT管理器] 瓶子已放回');
            }

            if (this.onMessageCallback) {
                this.onMessageCallback(data);
            }

            if (this.onDeviceStatusCallback) {
                this.onDeviceStatusCallback({
                    online: this.deviceOnline,
                    data: this.deviceData
                });
            }
        },

        checkAndHandleDoseChange: function(currentWeight, previousWeight) {
            if (this.deviceData.initStep >= 3 && this.deviceData.singleDoseWeight && this.deviceData.lastWeight !== null) {
                const dose = this.deviceData.singleDoseWeight;
                const minDetectThreshold = dose * 0.3;
                const weightDiff = this.deviceData.lastWeight - currentWeight;

                console.log('[全局MQTT管理器] 重量变化检测] lastWeight:', this.deviceData.lastWeight, 'currentWeight:', currentWeight, 'weightDiff:', weightDiff, 'threshold:', minDetectThreshold);

                if (Math.abs(weightDiff) > minDetectThreshold) {
                    if (weightDiff > 0) {
                        console.log('[全局MQTT管理器] 检测到取药，调用 checkRealDoseChange');
                        this.checkRealDoseChange(weightDiff, this.deviceData.lastWeight, currentWeight);
                    } else {
                        console.log('[全局MQTT管理器] 检测到放回，调用 checkRealRefill');
                        this.checkRealRefill(-weightDiff, this.deviceData.lastWeight, currentWeight);
                    }
                } else {
                    console.log('[全局MQTT管理器] 重量变化小于阈值，忽略');
                }
            }
            
            this.sendToBackend({ eventType: 'weight_change' }, currentWeight, previousWeight);
        },

        checkRealDoseChange: async function(weightDiff, weightBefore, weightAfter) {
            console.log('[全局MQTT管理器] checkRealDoseChange 开始处理，参数:', { weightDiff, weightBefore, weightAfter, boundMedicine: this.boundMedicine });
            
            if (!this.boundMedicine) {
                console.log('[全局MQTT管理器] 未绑定药品，跳过');
                return;
            }

            try {
                const res = await axios.post('/api/mqtt/deviceTake/optimized', {
                    deviceId: this.deviceId,
                    medicineId: this.boundMedicine.id,
                    medicineName: this.boundMedicine.name,
                    weightBefore: weightBefore,
                    weightAfter: weightAfter,
                    weightDiff: weightDiff
                });

                console.log('[全局MQTT管理器] API返回结果:', res.data);
            } catch (e) {
                console.error('[全局MQTT管理器] 调用API失败:', e);
            }
        },

        checkRealRefill: function(weightAdded, weightBefore, weightAfter) {
            console.log('[全局MQTT管理器] checkRealRefill 检测到放回药品:', { weightAdded, weightBefore, weightAfter });
        },

        sendToBackend: async function(data, currentWeight, previousWeight) {
            try {
                const res = await axios.post('/api/medicine-box/device/message', {
                    deviceId: this.deviceId,
                    eventType: 'weight_change',
                    slotId: 1,
                    weightBefore: previousWeight,
                    weightAfter: currentWeight
                });

                if (res.data && res.data.code === 200 && res.data.data) {
                    const resultData = res.data.data;
                    
                    if (resultData.stepComplete || resultData.initComplete) {
                        console.log('[全局MQTT管理器] 后端响应:', resultData.message);
                    }
                }
            } catch (e) {
                console.error('[全局MQTT管理器] 发送到后端失败:', e);
            }
        },

        startHeartbeatCheck: function() {
            if (this.heartbeatCheckTimer) {
                clearInterval(this.heartbeatCheckTimer);
            }

            this.heartbeatCheckTimer = setInterval(() => {
                if (this.lastHeartbeat) {
                    const timeSinceLastHeartbeat = Date.now() - this.lastHeartbeat;
                    if (timeSinceLastHeartbeat > 30000 && this.deviceOnline) {
                        this.deviceOnline = false;
                        console.log('[全局MQTT管理器] 设备离线');
                        if (this.onDeviceStatusCallback) {
                            this.onDeviceStatusCallback({
                                online: false,
                                data: this.deviceData
                            });
                        }
                    }
                }
            }, 10000);
        },

        setBoundMedicine: function(medicine) {
            this.boundMedicine = medicine;
            if (medicine) {
                localStorage.setItem('boundMedicine', JSON.stringify(medicine));
            } else {
                localStorage.removeItem('boundMedicine');
            }
        },

        getBoundMedicine: function() {
            return this.boundMedicine;
        },

        isConnected: function() {
            return this.connected;
        },

        isDeviceOnline: function() {
            return this.deviceOnline;
        },

        getDeviceData: function() {
            return this.deviceData;
        },

        onMessage: function(callback) {
            this.onMessageCallback = callback;
        },

        onDeviceStatus: function(callback) {
            this.onDeviceStatusCallback = callback;
        }
    };

    global.GlobalMqttManager = GlobalMqttManager;

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            if (typeof mqtt !== 'undefined') {
                GlobalMqttManager.init();
            }
        });
    } else {
        if (typeof mqtt !== 'undefined') {
            GlobalMqttManager.init();
        }
    }

})(window);
