const MedicineFormUtils = {
    specUnitMapping: {
        '粒': ['粒', '盒', '瓶'],
        '片': ['片', '盒', '瓶'],
        '袋': ['袋', '盒'],
        '毫升': ['毫升', '瓶'],
        '克': ['克', '盒', '袋'],
        '支': ['支', '盒'],
        '颗': ['颗', '盒', '瓶'],
        '丸': ['丸', '盒', '瓶']
    },

    doseUnits: ['片', '粒', '袋', '毫升', '克', '支', '颗', '丸'],

    packageUnits: ['盒', '瓶', '袋'],

    allTimeOptions: ['06:00', '08:00', '12:00', '18:00', '20:00', '21:00'],

    parseSpec(spec) {
        if (!spec) return { quantity: null, unit: null, packageUnit: null };
        
        const patterns = [
            /(\d+)\s*([粒片袋毫升克支颗丸]+)\s*[\/\\]\s*([盒瓶袋]+)/,
            /(\d+)\s*([粒片袋毫升克支颗丸]+)\s*[\/\\每]\s*([盒瓶袋]+)/,
            /每[盒瓶袋]+(\d+)([粒片袋毫升克支颗丸]+)/,
            /(\d+)([粒片袋毫升克支颗丸]+)\/([盒瓶袋]+)/
        ];
        
        for (const pattern of patterns) {
            const match = spec.match(pattern);
            if (match) {
                return {
                    quantity: parseInt(match[1]),
                    unit: match[2],
                    packageUnit: match[3]
                };
            }
        }
        
        return { quantity: null, unit: null, packageUnit: null };
    },

    buildSpec(quantity, unit, packageUnit) {
        if (!quantity || !unit || !packageUnit) return '';
        return `${quantity}${unit}/${packageUnit}`;
    },

    getAvailableUnits(specUnit) {
        if (!specUnit || !this.specUnitMapping[specUnit]) {
            return this.doseUnits;
        }
        const units = this.specUnitMapping[specUnit];
        return {
            doseUnits: [specUnit],
            packageUnits: units.filter(u => u !== specUnit && this.packageUnits.includes(u))
        };
    },

    getDefaultReminderTimes(frequency) {
        const freq = parseInt(frequency) || 2;
        switch (freq) {
            case 1: return ['08:00'];
            case 2: return ['08:00', '18:00'];
            case 3: return ['08:00', '12:00', '18:00'];
            case 4: return ['06:00', '12:00', '18:00', '20:00'];
            case 5: return ['06:00', '08:00', '12:00', '18:00', '20:00'];
            case 6: return ['06:00', '08:00', '12:00', '18:00', '20:00', '21:00'];
            default: return ['08:00', '18:00'];
        }
    },

    calculateTotalQuantity(packages, perPackage) {
        const pkg = parseInt(packages) || 0;
        const perPkg = parseInt(perPackage) || 0;
        return pkg * perPkg;
    },

    calculateDays(totalQuantity, dosagePerTime, frequency) {
        const total = parseInt(totalQuantity) || 0;
        const dosage = parseInt(dosagePerTime) || 1;
        const freq = parseInt(frequency) || 1;
        const daily = dosage * freq;
        return daily > 0 ? Math.floor(total / daily) : 0;
    },

    validateForm(formData) {
        const errors = [];
        
        if (!formData.name || !formData.name.trim()) {
            errors.push({ field: 'name', message: '请输入药品名称' });
        }
        
        if (formData.dosagePerTime !== undefined) {
            const dosage = parseFloat(formData.dosagePerTime);
            if (isNaN(dosage) || dosage <= 0) {
                errors.push({ field: 'dosagePerTime', message: '每次用量必须大于0' });
            }
        }
        
        if (formData.frequency !== undefined) {
            const freq = parseInt(formData.frequency);
            if (isNaN(freq) || freq < 1 || freq > 6) {
                errors.push({ field: 'frequency', message: '每日次数应在1-6次之间' });
            }
        }
        
        if (formData.reminderTimes && formData.frequency) {
            const freq = parseInt(formData.frequency);
            const timesCount = formData.reminderTimes.length;
            if (timesCount !== freq) {
                errors.push({ 
                    field: 'reminderTimes', 
                    message: `提醒时间数量(${timesCount}个)与每日次数(${freq}次)不匹配，请选择${freq}个提醒时间` 
                });
            }
        }
        
        if (formData.packages !== undefined && formData.perPackage !== undefined) {
            const packages = parseInt(formData.packages);
            const perPackage = parseInt(formData.perPackage);
            if (packages <= 0) {
                errors.push({ field: 'packages', message: '盒/瓶数必须大于0' });
            }
            if (perPackage <= 0) {
                errors.push({ field: 'perPackage', message: '每盒数量必须大于0' });
            }
        }
        
        if (formData.spec && formData.unit) {
            const parsed = this.parseSpec(formData.spec);
            if (parsed.unit && parsed.unit !== formData.unit) {
                errors.push({ 
                    field: 'spec', 
                    message: `规格单位"${parsed.unit}"与剂量单位"${formData.unit}"不匹配` 
                });
            }
        }
        
        return {
            valid: errors.length === 0,
            errors: errors
        };
    },

    validateSpecUnitMatch(spec, doseUnit) {
        if (!spec || !doseUnit) return { valid: true };
        
        const parsed = this.parseSpec(spec);
        if (!parsed.unit) return { valid: true };
        
        if (parsed.unit !== doseUnit) {
            return {
                valid: false,
                message: `规格单位"${parsed.unit}"与剂量单位"${doseUnit}"不匹配，请重新选择`
            };
        }
        
        return { valid: true };
    },

    validateReminderTimes(frequency, reminderTimes) {
        const freq = parseInt(frequency) || 0;
        const times = reminderTimes || [];
        
        if (freq === 0) {
            return { valid: false, message: '请先设置每日次数' };
        }
        
        if (times.length !== freq) {
            return {
                valid: false,
                message: `当前每日${freq}次，已选择${times.length}个提醒时间，请${times.length > freq ? '删除' + (times.length - freq) + '个' : '再选择' + (freq - times.length) + '个'}时间`
            };
        }
        
        return { valid: true };
    },

    showFieldError(fieldElement, message) {
        this.clearFieldError(fieldElement);
        
        const errorDiv = document.createElement('div');
        errorDiv.className = 'field-error-msg';
        errorDiv.style.cssText = 'color: #EF4444; font-size: 13px; margin-top: 4px; display: flex; align-items: center; gap: 4px;';
        errorDiv.innerHTML = `<i class="fa fa-exclamation-circle"></i> ${message}`;
        
        const parent = fieldElement.closest('.form-group') || fieldElement.closest('.form-row') || fieldElement.parentElement;
        parent.appendChild(errorDiv);
        
        fieldElement.style.borderColor = '#EF4444';
    },

    clearFieldError(fieldElement) {
        const parent = fieldElement.closest('.form-group') || fieldElement.closest('.form-row') || fieldElement.parentElement;
        const existingError = parent.querySelector('.field-error-msg');
        if (existingError) {
            existingError.remove();
        }
        fieldElement.style.borderColor = '';
    },

    clearAllErrors(formContainer) {
        const errorElements = formContainer.querySelectorAll('.field-error-msg');
        errorElements.forEach(el => el.remove());
        
        const inputs = formContainer.querySelectorAll('input, select');
        inputs.forEach(input => {
            input.style.borderColor = '';
        });
    },

    showValidationSummary(errors, containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;
        
        if (errors.length === 0) {
            container.innerHTML = '';
            container.style.display = 'none';
            return;
        }
        
        container.innerHTML = `
            <div style="background: #FEF2F2; border: 1px solid #FECACA; border-radius: 12px; padding: 16px; margin-bottom: 16px;">
                <div style="color: #DC2626; font-weight: 600; margin-bottom: 8px; display: flex; align-items: center; gap: 8px;">
                    <i class="fa fa-exclamation-triangle"></i> 请修正以下问题
                </div>
                <ul style="margin: 0; padding-left: 20px; color: #991B1B;">
                    ${errors.map(e => `<li>${e.message}</li>`).join('')}
                </ul>
            </div>
        `;
        container.style.display = 'block';
    },

    formatMedicineDisplay(medicine) {
        const parts = [];
        
        if (medicine.name) parts.push(medicine.name);
        if (medicine.spec) parts.push(medicine.spec);
        if (medicine.dosagePerTime && medicine.unit) {
            parts.push(`每次${medicine.dosagePerTime}${medicine.unit}`);
        }
        if (medicine.frequency) {
            parts.push(`每日${medicine.frequency}次`);
        }
        
        return parts.join(' · ');
    },

    extractSpecInfo(spec) {
        const parsed = this.parseSpec(spec);
        return {
            perPackage: parsed.quantity,
            specUnit: parsed.unit,
            packageUnit: parsed.packageUnit
        };
    }
};

if (typeof window !== 'undefined') {
    window.MedicineFormUtils = MedicineFormUtils;
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = MedicineFormUtils;
}
