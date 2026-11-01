function judgeMedicationAction(actualTime, plannedTime, dosingIntervalHours, isFirstDose, isLastDose) {
    const parseTime = (time) => {
        if (time instanceof Date) {
            return time.getHours() * 60 + time.getMinutes();
        }
        const [hours, minutes] = time.split(':').map(Number);
        return hours * 60 + minutes;
    };

    const actualMinutes = parseTime(actualTime);
    const plannedMinutes = parseTime(plannedTime);
    const intervalMinutes = dosingIntervalHours * 60;
    const makeupWindowMinutes = Math.floor(intervalMinutes / 2);
    const earlyWindowMinutes = Math.floor(intervalMinutes / 4);

    const earlyStartMinutes = plannedMinutes - earlyWindowMinutes;
    const makeupEndMinutes = plannedMinutes + makeupWindowMinutes;

    let action = '';
    let message = '';

    if (isFirstDose) {
        if (actualMinutes < plannedMinutes) {
            action = 'too_early';
            message = '现在服药还太早哦，请在计划时间服药~';
        } else if (actualMinutes <= makeupEndMinutes) {
            action = 'ontime';
            message = '按时服药啦，真棒！下次也要坚持哦~';
        } else {
            action = 'missed';
            message = '已经超过补服时间了，这次就跳过吧，记得下次按时服药哦~';
        }
    } else {
        if (actualMinutes < earlyStartMinutes) {
            action = 'too_early';
            message = '距离下次服药还太早呢，晚点再吃吧~';
        } else if (actualMinutes < plannedMinutes) {
            action = 'early';
            message = '提前服药没问题，记得下次时间不变哦~';
        } else if (actualMinutes <= makeupEndMinutes) {
            action = 'makeup';
            message = '补服成功，下次记得按时哦~';
        } else {
            action = 'missed';
            message = '已经超过补服时间了，这次就跳过吧，记得下次按时服药哦~';
        }
    }

    return { action, message };
}

const MEDICATION_MESSAGES = {
    ontime: '按时服药啦，真棒！下次也要坚持哦~',
    makeup: '补服成功，下次记得按时哦~',
    early: '提前服药没问题，记得下次时间不变哦~',
    missed: '已经超过补服时间了，这次就跳过吧，记得下次按时服药哦~',
    too_early: '现在服药还太早哦，请在计划时间服药~'
};
