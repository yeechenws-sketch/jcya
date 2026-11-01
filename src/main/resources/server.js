const express = require('express');
const https = require('https');
const { Server } = require('socket.io');
const fs = require('fs');

const app = express();

const sslOptions = {
  key: fs.readFileSync('/app/ssl/xiechen.xyz.key'),
  cert: fs.readFileSync('/app/ssl/xiechen.xyz_bundle.crt')
};

const server = https.createServer(sslOptions, app);

const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

const onlineUsers = {};
const busyUsers = new Set();
const emergencyCalls = new Map();
const activeEmergencyCalls = new Map();

io.on('connection', (socket) => {
  console.log('[连接] 用户连接，SocketID:', socket.id);

  socket.on('login', (data) => {
    let userId, nickname, avatar;
    
    if (typeof data === 'object') {
      userId = String(data.userId);
      nickname = data.nickname || '用户';
      avatar = data.avatar || '';
    } else {
      userId = String(data);
      nickname = '用户';
      avatar = '';
    }
    
    for (const [existingId, userInfo] of Object.entries(onlineUsers)) {
      if (String(existingId) === userId) {
        delete onlineUsers[existingId];
        console.log(`[登录] 用户 ${userId} 重复登录，清理旧连接`);
        break;
      }
    }
    
    onlineUsers[userId] = {
      socketId: socket.id,
      nickname: nickname,
      avatar: avatar
    };
    
    console.log(`[登录] 用户 ${userId} (${nickname}) 上线，SocketID: ${socket.id}`);
    console.log(`[登录] 当前在线用户: ${Object.keys(onlineUsers).join(', ')}`);
    socket.emit('loginSuccess', '登录成功');
  });

  socket.on('call', (data) => {
    const fromUserId = String(data.fromUserId);
    const toUserId = String(data.toUserId);
    const { type, fromUserName, fromUserAvatar, isEmergency } = data;
    
    const targetUser = onlineUsers[toUserId];
    const callerUser = onlineUsers[fromUserId];
    
    console.log(`[呼叫] ${fromUserId} -> ${toUserId}, 类型: ${type}, 紧急: ${isEmergency}`);
    console.log(`[呼叫] 主叫方Socket: ${callerUser ? callerUser.socketId : '未找到'}`);
    console.log(`[呼叫] 被叫方Socket: ${targetUser ? targetUser.socketId : '未找到'}`);
    console.log(`[呼叫] 当前在线: ${Object.keys(onlineUsers).join(', ')}`);
    
    if (!targetUser) {
      console.log(`[呼叫] 失败: 用户 ${toUserId} 不在线`);
      socket.emit('callFailed', '对方不在线，请稍后再试');
      return;
    }
    
    if (busyUsers.has(toUserId) && !data.isEmergency) {
      console.log(`[呼叫] 失败: 用户 ${toUserId} 忙线中`);
      socket.emit('callBusy', { toUserId, message: '对方正在通话中' });
      return;
    }
    
    const callerName = fromUserName || (callerUser ? callerUser.nickname : '用户');
    const callerAvatar = fromUserAvatar || (callerUser ? callerUser.avatar : '');
    
    busyUsers.add(fromUserId);
    
    io.to(targetUser.socketId).emit('calling', { 
      fromUserId, 
      type,
      fromUserName: callerName,
      fromUserAvatar: callerAvatar,
      isEmergency: isEmergency || false
    });
    
    console.log(`[呼叫] 成功: 已向 ${toUserId} 发送呼叫通知, 紧急: ${isEmergency}`);
  });

  socket.on('emergencyCall', (data) => {
    const fromUserId = String(data.fromUserId);
    const toUserIds = data.toUserIds || [];
    const { type, fromUserName, fromUserAvatar, isEmergency, alertType, alertValue, alertLevel } = data;
    
    const callerUser = onlineUsers[fromUserId];
    const callerName = fromUserName || (callerUser ? callerUser.nickname : '用户');
    const callerAvatar = fromUserAvatar || (callerUser ? callerUser.avatar : '');
    
    console.log(`[紧急呼叫] ${fromUserId} -> ${toUserIds.join(', ')}, 类型: ${type}, 预警: ${alertType} = ${alertValue}`);
    console.log(`[紧急呼叫] 当前在线: ${Object.keys(onlineUsers).join(', ')}`);
    
    busyUsers.add(fromUserId);
    
    activeEmergencyCalls.set(fromUserId, {
      fromUserId,
      fromUserName: callerName,
      toUserIds,
      answeredBy: null,
      answeredByName: null,
      startTime: Date.now(),
      alertType,
      alertValue,
      alertLevel
    });
    
    let calledCount = 0;
    toUserIds.forEach(toUserId => {
      const targetUser = onlineUsers[String(toUserId)];
      if (targetUser) {
        io.to(targetUser.socketId).emit('calling', { 
          fromUserId, 
          type,
          fromUserName: callerName,
          fromUserAvatar: callerAvatar,
          isEmergency: true,
          isGroupEmergency: true,
          alertType: alertType,
          alertValue: alertValue,
          alertLevel: alertLevel
        });
        calledCount++;
        console.log(`[紧急呼叫] 成功: 已向 ${toUserId} 发送紧急呼叫通知`);
      } else {
        console.log(`[紧急呼叫] 跳过: 用户 ${toUserId} 不在线`);
      }
    });
    
    if (calledCount > 0) {
      socket.emit('emergencyCallSuccess', { calledCount, userIds: toUserIds });
    } else {
      socket.emit('callFailed', '没有在线的紧急联系人');
    }
  });

  socket.on('answerCall', (data) => {
    const toUserId = String(data.toUserId);
    const fromUserId = String(data.fromUserId);
    const targetUser = onlineUsers[toUserId];
    
    console.log(`[接听] ${fromUserId} 接听 ${toUserId} 的呼叫`);
    console.log(`[接听] 主叫方Socket: ${targetUser ? targetUser.socketId : '未找到'}`);
    
    const emergencyCall = activeEmergencyCalls.get(toUserId);
    if (emergencyCall) {
      console.log(`[接听] 这是紧急呼叫`);
      if (emergencyCall.answeredBy) {
        console.log(`[接听] 紧急呼叫已被 ${emergencyCall.answeredBy} (${emergencyCall.answeredByName}) 接听，拒绝本次接听`);
        socket.emit('emergencyCallAlreadyAnswered', { 
          answeredBy: emergencyCall.answeredBy, 
          answeredByName: emergencyCall.answeredByName 
        });
        return;
      }
      
      const answererUser = onlineUsers[fromUserId];
      emergencyCall.answeredBy = fromUserId;
      emergencyCall.answeredByName = answererUser ? answererUser.nickname : '联系人';
      console.log(`[接听] 紧急呼叫由 ${fromUserId} (${emergencyCall.answeredByName}) 第一个接听`);
      
      const callerUser = onlineUsers[toUserId];
      if (callerUser) {
        io.to(callerUser.socketId).emit('emergencyCallAnsweredBySomeone', { 
          answeredBy: fromUserId, 
          answeredByName: emergencyCall.answeredByName 
        });
        console.log(`[接听] 已通知呼叫方 ${toUserId} 紧急呼叫已被 ${emergencyCall.answeredByName} 接听`);
      }
      
      emergencyCall.toUserIds.forEach(userId => {
        if (String(userId) !== fromUserId) {
          const otherUser = onlineUsers[String(userId)];
          if (otherUser) {
            io.to(otherUser.socketId).emit('emergencyCallAlreadyAnswered', { 
              answeredBy: fromUserId, 
              answeredByName: emergencyCall.answeredByName 
            });
            console.log(`[接听] 已通知 ${userId} 紧急呼叫已被接听`);
          }
        }
      });
    }
    
    if (targetUser) {
      busyUsers.add(fromUserId);
      
      io.to(targetUser.socketId).emit('callAnswered', { fromUserId });
      console.log(`[接听] 成功: 已通知主叫方 ${toUserId}`);
    } else {
      console.log(`[接听] 失败: 主叫方 ${toUserId} 不在线`);
    }
  });

  socket.on('calleeReady', (data) => {
    const toUserId = String(data.toUserId);
    const fromUserId = String(data.fromUserId);
    const targetUser = onlineUsers[toUserId];
    
    console.log(`[就绪] 被叫方 ${fromUserId} 准备就绪，通知主叫方 ${toUserId}`);
    
    if (targetUser) {
      io.to(targetUser.socketId).emit('calleeReady', { fromUserId });
    }
  });

  socket.on('rejectCall', (data) => {
    const toUserId = String(data.toUserId);
    const targetUser = onlineUsers[toUserId];
    
    console.log(`[拒绝] 拒绝呼叫，通知 ${toUserId}`);
    
    if (targetUser) {
      io.to(targetUser.socketId).emit('callRejected', { message: '对方已拒绝' });
    }
    
    for (const userId of busyUsers) {
      if (onlineUsers[userId] && onlineUsers[userId].socketId === socket.id) {
        busyUsers.delete(userId);
      }
    }
  });

  socket.on('hangup', (data) => {
    const toUserId = data.toUserId ? String(data.toUserId) : null;
    const targetUser = toUserId ? onlineUsers[toUserId] : null;
    
    console.log(`[挂断] 挂断通话，通知 ${toUserId}`);
    
    if (targetUser) {
      io.to(targetUser.socketId).emit('hangup');
    }
    
    for (const userId of busyUsers) {
      if (onlineUsers[userId] && onlineUsers[userId].socketId === socket.id) {
        busyUsers.delete(userId);
      }
    }
    
    if (toUserId) {
      busyUsers.delete(toUserId);
    }
  });

  socket.on('emergencyCallEnded', (data) => {
    const fromUserId = String(data.fromUserId);
    console.log('[紧急呼叫结束] 从', fromUserId, '收到结束通知');
    
    const emergencyCall = activeEmergencyCalls.get(fromUserId);
    if (emergencyCall) {
      console.log('[紧急呼叫结束] 找到活跃呼叫，通知所有紧急联系人');
      
      emergencyCall.toUserIds.forEach(userId => {
        const targetUser = onlineUsers[String(userId)];
        if (targetUser) {
          io.to(targetUser.socketId).emit('emergencyCallEnded', data);
          console.log('[紧急呼叫结束] 已通知', userId);
        }
      });
      
      activeEmergencyCalls.delete(fromUserId);
    }
    
    busyUsers.delete(fromUserId);
  });

  socket.on('disconnect', () => {
    console.log('[断开] 用户断开连接，SocketID:', socket.id);
    
    let disconnectedUserId = null;
    for (const [userId, userInfo] of Object.entries(onlineUsers)) {
      if (userInfo.socketId === socket.id) {
        disconnectedUserId = userId;
        break;
      }
    }
    
    if (disconnectedUserId && activeEmergencyCalls.has(disconnectedUserId)) {
      console.log(`[断开] 用户 ${disconnectedUserId} 断开，有进行中的紧急呼叫，通知联系人`);
      const emergencyCall = activeEmergencyCalls.get(disconnectedUserId);
      if (emergencyCall && emergencyCall.toUserIds) {
        emergencyCall.toUserIds.forEach(toUserId => {
          const targetUser = onlineUsers[String(toUserId)];
          if (targetUser) {
            io.to(targetUser.socketId).emit('emergencyCallEnded', {
              fromUserId: disconnectedUserId,
              fromUserName: emergencyCall.fromUserName || '长辈'
            });
            console.log(`[断开] 已通知用户 ${toUserId} 呼叫结束`);
          }
        });
      }
      activeEmergencyCalls.delete(disconnectedUserId);
    }
    
    setTimeout(() => {
      for (const [userId, userInfo] of Object.entries(onlineUsers)) {
        if (userInfo.socketId === socket.id) {
          delete onlineUsers[userId];
          console.log(`[断开] 用户 ${userId} 下线，当前在线: ${Object.keys(onlineUsers).join(', ')}`);
          break;
        }
      }
    }, 10000);
  });
});

const PORT = 3000;
server.listen(PORT, () => {
  console.log(`HTTPS 信令服务器启动成功！端口: ${PORT}`);
});
