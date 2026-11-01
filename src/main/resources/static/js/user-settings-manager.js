// 用户显示和播放设置全局管理器
(function() {
    // 默认设置
    const defaultSettings = {
        displaySize: 100,
        volume: 80,
        voiceMode: 0
    };

    // 全局设置对象
    window.userSettingsManager = {
        settings: { ...defaultSettings },
        
        // 初始化设置
        init: async function() {
            this.loadFromLocalStorage();
            this.listenStorageChange();
            
            // 尝试从服务器加载用户设置
            await this.loadFromServer();
            
            // 应用设置
            this.applyAllSettings();
        },
        
        // 从localStorage加载设置
        loadFromLocalStorage: function() {
            const stored = localStorage.getItem('userDisplaySettings');
            if (stored) {
                try {
                    const parsed = JSON.parse(stored);
                    this.settings = { ...defaultSettings, ...parsed };
                } catch (e) {
                    console.error('解析本地设置失败:', e);
                }
            }
        },
        
        // 保存设置到localStorage
        saveToLocalStorage: function() {
            localStorage.setItem('userDisplaySettings', JSON.stringify(this.settings));
        },
        
        // 从服务器加载设置
        loadFromServer: async function() {
            try {
                const res = await fetch('/api/user/info');
                const data = await res.json();
                
                if (data.code === 200 && data.data && data.data.id) {
                    const userId = data.data.id;
                    const settingsRes = await fetch('/api/user/display-settings?userId=' + userId);
                    const settingsData = await settingsRes.json();
                    
                    if (settingsData.code === 200 && settingsData.data) {
                        const s = settingsData.data;
                        this.settings = {
                            displaySize: s.displaySize || defaultSettings.displaySize,
                            volume: s.volume || defaultSettings.volume,
                            voiceMode: s.voiceMode !== undefined ? s.voiceMode : defaultSettings.voiceMode
                        };
                        this.saveToLocalStorage();
                    }
                }
            } catch (e) {
                console.error('加载服务器设置失败:', e);
            }
        },
        
        // 保存设置到服务器
        saveToServer: async function() {
            try {
                const res = await fetch('/api/user/info');
                const data = await res.json();
                
                if (data.code === 200 && data.data && data.data.id) {
                    await fetch('/api/user/display-settings', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            userId: data.data.id,
                            displaySize: this.settings.displaySize,
                            volume: this.settings.volume,
                            voiceMode: this.settings.voiceMode
                        })
                    });
                }
            } catch (e) {
                console.error('保存设置到服务器失败:', e);
            }
        },
        
        // 监听其他页面的设置变更
        listenStorageChange: function() {
            window.addEventListener('storage', (e) => {
                if (e.key === 'userSettingsUpdated') {
                    this.loadFromLocalStorage();
                    this.applyAllSettings();
                }
            });
        },
        
        // 广播设置变更
        broadcastChange: function() {
            localStorage.setItem('userSettingsUpdated', Date.now());
        },
        
        // 应用所有设置
        applyAllSettings: function() {
            this.applyDisplaySize(this.settings.displaySize);
            this.applyVolume(this.settings.volume);
            this.applyVoiceMode(this.settings.voiceMode);
        },
        
        // 应用显示大小
        applyDisplaySize: function(size) {
            // 使用CSS变量实现放大效果
            const scale = size / 100;
            const root = document.documentElement;
            
            // 更新CSS变量
            root.style.setProperty('--header-h1-size', (40 * scale) + 'px');
            root.style.setProperty('--header-p-size', (22 * scale) + 'px');
            root.style.setProperty('--time-size', (52 * scale) + 'px');
            root.style.setProperty('--date-size', (20 * scale) + 'px');
            root.style.setProperty('--feature-icon-size', (75 * scale) + 'px');
            root.style.setProperty('--feature-title-size', (34 * scale) + 'px');
            root.style.setProperty('--feature-desc-size', (21 * scale) + 'px');
            root.style.setProperty('--nav-icon-size', (34 * scale) + 'px');
            root.style.setProperty('--nav-text-size', (20 * scale) + 'px');
            root.style.setProperty('--temp-size', (65 * scale) + 'px');
            root.style.setProperty('--weather-tip-size', (19 * scale) + 'px');
            root.style.setProperty('--ai-status-size', (22 * scale) + 'px');
            root.style.setProperty('--mic-icon-size', (110 * scale) + 'px');
            root.style.setProperty('--mic-icon-inner-size', (50 * scale) + 'px');
            
            // 调整卡片和容器的大小
            const containers = document.querySelectorAll('.container, .feature-grid');
            containers.forEach(el => {
                el.style.gap = (25 * scale) + 'px';
            });
            
            const glassCards = document.querySelectorAll('.glass-card');
            glassCards.forEach(el => {
                el.style.padding = (30 * scale) + 'px';
            });
            
            // 调整天气图标大小
            const weatherIcon = document.getElementById('w-icon');
            if (weatherIcon) {
                weatherIcon.style.fontSize = (75 * scale) + 'px';
            }
            
            this.settings.displaySize = size;
            this.saveToLocalStorage();
        },
        
        // 应用音量
        applyVolume: function(vol) {
            this.settings.volume = vol;
            this.saveToLocalStorage();
            
            // 更新所有现有音频的音量
            document.querySelectorAll('audio').forEach(audio => {
                audio.volume = vol / 100;
            });
            
            // 更新语音设置面板中的显示
            const voiceVolumeSlider = document.getElementById('voice-volume-slider');
            const voiceVolumeValue = document.getElementById('voice-volume-value');
            if (voiceVolumeSlider) voiceVolumeSlider.value = vol;
            if (voiceVolumeValue) voiceVolumeValue.textContent = vol + '%';
        },
        
        // 应用语音模式
        applyVoiceMode: function(mode) {
            this.settings.voiceMode = mode;
            this.saveToLocalStorage();
            
            // 更新语音设置面板中的显示
            const voiceSwitch = document.getElementById('voice-mode-switch-voice');
            const voiceLabel = document.getElementById('voice-mode-label-voice');
            
            if (voiceSwitch && voiceLabel) {
                if (mode === 1) {
                    voiceSwitch.classList.add('active');
                    voiceLabel.textContent = '手动触发';
                } else {
                    voiceSwitch.classList.remove('active');
                    voiceLabel.textContent = '自动播放';
                }
            }
        },
        
        // 更新设置并保存
        updateSettings: function(newSettings) {
            this.settings = { ...this.settings, ...newSettings };
            this.applyAllSettings();
            this.saveToLocalStorage();
            this.saveToServer();
            this.broadcastChange();
        },
        
        // 获取当前设置
        getSettings: function() {
            return { ...this.settings };
        },
        
        // 检查是否可以自动播放语音
        canAutoPlay: function() {
            return this.settings.voiceMode !== 1;
        },
        
        // 获取音量
        getVolume: function() {
            return this.settings.volume / 100;
        }
    };

    // 在页面加载完成后自动初始化
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            window.userSettingsManager.init();
        });
    } else {
        window.userSettingsManager.init();
    }
})();
