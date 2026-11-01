/**
 * 图片压缩工具 - 参考微信图片压缩标准
 * 目标：将图片压缩到800KB左右，保持图片质量
 */

const ImageCompressor = {
    TARGET_SIZE: 800 * 1024,
    MIN_QUALITY: 0.1,
    MAX_QUALITY: 0.92,
    MAX_DIMENSION: 4096,
    MIN_DIMENSION: 100,

    async compress(file, options = {}) {
        const {
            targetSize = this.TARGET_SIZE,
            maxWidth = this.MAX_DIMENSION,
            maxHeight = this.MAX_DIMENSION,
            showProgress = true,
            onProgress = null
        } = options;

        return new Promise((resolve, reject) => {
            if (!file || !file.type.startsWith('image/')) {
                reject(new Error('请选择有效的图片文件'));
                return;
            }

            if (file.size > 50 * 1024 * 1024) {
                reject(new Error('图片大小不能超过50MB'));
                return;
            }

            const startTime = Date.now();
            
            if (showProgress && onProgress) {
                onProgress(10, '正在读取图片...');
            }

            const reader = new FileReader();
            
            reader.onload = (e) => {
                if (showProgress && onProgress) {
                    onProgress(20, '正在解析图片...');
                }

                const img = new Image();
                
                img.onload = () => {
                    try {
                        const result = this.doCompress(img, file, {
                            targetSize,
                            maxWidth,
                            maxHeight,
                            showProgress,
                            onProgress
                        });
                        
                        const endTime = Date.now();
                        console.log(`[图片压缩] 完成，耗时: ${endTime - startTime}ms，原始大小: ${(file.size / 1024).toFixed(1)}KB，压缩后: ${(result.size / 1024).toFixed(1)}KB`);
                        
                        resolve(result);
                    } catch (err) {
                        reject(err);
                    }
                };
                
                img.onerror = () => {
                    reject(new Error('图片解析失败，请检查图片是否损坏'));
                };
                
                img.src = e.target.result;
            };
            
            reader.onerror = () => {
                reject(new Error('图片读取失败'));
            };
            
            reader.readAsDataURL(file);
        });
    },

    doCompress(img, originalFile, options) {
        const { targetSize, maxWidth, maxHeight, showProgress, onProgress } = options;
        
        let width = img.naturalWidth || img.width;
        let height = img.naturalHeight || img.height;
        
        if (width > maxWidth || height > maxHeight) {
            const ratio = Math.min(maxWidth / width, maxHeight / height);
            width = Math.round(width * ratio);
            height = Math.round(height * ratio);
        }
        
        width = Math.max(this.MIN_DIMENSION, width);
        height = Math.max(this.MIN_DIMENSION, height);

        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        
        const isTransparent = this.hasTransparency(img);
        
        if (!isTransparent) {
            ctx.fillStyle = '#FFFFFF';
            ctx.fillRect(0, 0, width, height);
        }
        
        ctx.drawImage(img, 0, 0, width, height);
        
        if (showProgress && onProgress) {
            onProgress(50, '正在压缩...');
        }

        let quality = this.MAX_QUALITY;
        let compressedBlob = null;
        let attempts = 0;
        const maxAttempts = 15;
        
        const mimeType = isTransparent ? 'image/png' : 'image/jpeg';
        
        while (attempts < maxAttempts) {
            const dataUrl = canvas.toDataURL(mimeType, quality);
            const base64Length = dataUrl.length - `data:${mimeType};base64,`.length;
            const size = Math.round(base64Length * 0.75);
            
            if (size <= targetSize * 1.1 || quality <= this.MIN_QUALITY) {
                compressedBlob = this.dataURLToBlob(dataUrl, mimeType);
                break;
            }
            
            const ratio = targetSize / size;
            if (ratio > 0.5) {
                quality = quality * ratio * 0.9;
            } else {
                quality = quality * 0.7;
            }
            
            quality = Math.max(this.MIN_QUALITY, Math.min(this.MAX_QUALITY, quality));
            attempts++;
        }
        
        if (!compressedBlob) {
            const dataUrl = canvas.toDataURL(mimeType, this.MIN_QUALITY);
            compressedBlob = this.dataURLToBlob(dataUrl, mimeType);
        }

        if (showProgress && onProgress) {
            onProgress(90, '正在生成文件...');
        }

        const extension = mimeType === 'image/png' ? 'png' : 'jpg';
        const originalName = originalFile.name || 'image.jpg';
        const baseName = originalName.replace(/\.[^/.]+$/, '');
        const newFileName = `${baseName}_compressed.${extension}`;
        
        const compressedFile = new File([compressedBlob], newFileName, {
            type: mimeType,
            lastModified: Date.now()
        });

        if (showProgress && onProgress) {
            onProgress(100, '压缩完成');
        }

        return {
            file: compressedFile,
            blob: compressedBlob,
            width: width,
            height: height,
            size: compressedFile.size,
            originalSize: originalFile.size,
            compressionRatio: ((1 - compressedFile.size / originalFile.size) * 100).toFixed(1),
            quality: quality
        };
    },

    hasTransparency(img) {
        const canvas = document.createElement('canvas');
        const width = Math.min(img.width, 100);
        const height = Math.min(img.height, 100);
        canvas.width = width;
        canvas.height = height;
        
        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, width, height);
        
        try {
            const imageData = ctx.getImageData(0, 0, width, height);
            const data = imageData.data;
            
            for (let i = 3; i < data.length; i += 4) {
                if (data[i] < 255) {
                    return true;
                }
            }
        } catch (e) {
            return false;
        }
        
        return false;
    },

    dataURLToBlob(dataUrl, mimeType) {
        const byteString = atob(dataUrl.split(',')[1]);
        const arrayBuffer = new ArrayBuffer(byteString.length);
        const uint8Array = new Uint8Array(arrayBuffer);
        
        for (let i = 0; i < byteString.length; i++) {
            uint8Array[i] = byteString.charCodeAt(i);
        }
        
        return new Blob([uint8Array], { type: mimeType });
    },

    async compressToFile(file, options = {}) {
        const result = await this.compress(file, options);
        return result.file;
    },

    isImageFile(file) {
        if (!file) return false;
        const validTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'];
        return validTypes.includes(file.type.toLowerCase());
    },

    formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
    }
};

window.ImageCompressor = ImageCompressor;

async function compressImage(file, options = {}) {
    if (!ImageCompressor.isImageFile(file)) {
        return file;
    }
    
    if (file.size <= 100 * 1024) {
        console.log('[图片压缩] 文件太小，跳过压缩');
        return file;
    }
    
    try {
        const result = await ImageCompressor.compress(file, options);
        console.log(`[图片压缩] 压缩率: ${result.compressionRatio}%`);
        return result.file;
    } catch (err) {
        console.error('[图片压缩] 压缩失败:', err);
        throw err;
    }
}

window.compressImage = compressImage;

console.log('[图片压缩工具] 已加载，目标大小: 800KB');
