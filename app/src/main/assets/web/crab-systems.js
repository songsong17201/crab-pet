/**
 * 对话气泡系统
 * 气泡颜色随心情变化：生气红/吃醋黄/害羞粉/嫉妒绿/耳语灰/普通白
 */
class BubbleSystem {
    constructor(ctx, canvasWidth, canvasHeight) {
        this.ctx = ctx;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.currentBubble = null;
        this.bubbleTimer = null;
    }

    show(text, color = 'white', duration = 3000) {
        this.currentBubble = {
            text: text,
            color: color,
            opacity: 1,
            startTime: Date.now(),
            duration: duration
        };

        if (this.bubbleTimer) clearTimeout(this.bubbleTimer);
        this.bubbleTimer = setTimeout(() => {
            this.currentBubble = null;
        }, duration);
    }

    render(crabX, crabY) {
        if (!this.currentBubble) return;

        const bubble = this.currentBubble;
        const elapsed = Date.now() - bubble.startTime;
        
        // 淡出效果
        if (elapsed > bubble.duration - 500) {
            bubble.opacity = Math.max(0, (bubble.duration - elapsed) / 500);
        }

        const ctx = this.ctx;
        ctx.save();
        ctx.globalAlpha = bubble.opacity;

        // 气泡位置：螃蟹头顶上方
        const bubbleX = crabX;
        const bubbleY = crabY - 50;

        // 测量文字宽度
        ctx.font = '11px "Courier New", monospace';
        const textWidth = ctx.measureText(bubble.text).width;
        const padding = 8;
        const bubbleWidth = textWidth + padding * 2;
        const bubbleHeight = 20;

        // 气泡背景色
        const colorMap = {
            'white': { bg: 'rgba(255,255,255,0.95)', border: '#ccc', text: '#333' },
            'pink': { bg: 'rgba(255,220,230,0.95)', border: '#ffaacc', text: '#cc3366' },
            'red': { bg: 'rgba(255,220,220,0.95)', border: '#ff6666', text: '#cc0000' },
            'yellow': { bg: 'rgba(255,255,220,0.95)', border: '#ffcc00', text: '#996600' },
            'green': { bg: 'rgba(220,255,220,0.95)', border: '#66cc66', text: '#006600' },
            'grey': { bg: 'rgba(240,240,240,0.95)', border: '#999', text: '#666' }
        };

        const colors = colorMap[bubble.color] || colorMap['white'];

        // 绘制气泡圆角矩形
        const x = bubbleX - bubbleWidth / 2;
        const y = bubbleY - bubbleHeight / 2;
        const radius = 6;

        ctx.fillStyle = colors.bg;
        ctx.strokeStyle = colors.border;
        ctx.lineWidth = 1.5;

        ctx.beginPath();
        ctx.moveTo(x + radius, y);
        ctx.lineTo(x + bubbleWidth - radius, y);
        ctx.quadraticCurveTo(x + bubbleWidth, y, x + bubbleWidth, y + radius);
        ctx.lineTo(x + bubbleWidth, y + bubbleHeight - radius);
        ctx.quadraticCurveTo(x + bubbleWidth, y + bubbleHeight, x + bubbleWidth - radius, y + bubbleHeight);
        ctx.lineTo(x + radius, y + bubbleHeight);
        ctx.quadraticCurveTo(x, y + bubbleHeight, x, y + bubbleHeight - radius);
        ctx.lineTo(x, y + radius);
        ctx.quadraticCurveTo(x, y, x + radius, y);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();

        // 绘制小三角（指向螃蟹）
        ctx.fillStyle = colors.bg;
        ctx.beginPath();
        ctx.moveTo(bubbleX - 4, y + bubbleHeight);
        ctx.lineTo(bubbleX, y + bubbleHeight + 6);
        ctx.lineTo(bubbleX + 4, y + bubbleHeight);
        ctx.closePath();
        ctx.fill();
        ctx.strokeStyle = colors.border;
        ctx.beginPath();
        ctx.moveTo(bubbleX - 4, y + bubbleHeight);
        ctx.lineTo(bubbleX, y + bubbleHeight + 6);
        ctx.lineTo(bubbleX + 4, y + bubbleHeight);
        ctx.stroke();

        // 绘制文字
        ctx.fillStyle = colors.text;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(bubble.text, bubbleX, bubbleY);

        ctx.restore();
    }
}

/**
 * 待机递进动画系统
 */
class IdleAnimationSystem {
    constructor(skeleton) {
        this.skeleton = skeleton;
        this.currentStage = 'idle';
        this.frame = 0;
        this.decorations = []; // 装饰物（花、泡泡等）
    }

    setStage(stage) {
        this.currentStage = stage;
        this.frame = 0;
        this.decorations = [];
    }

    update() {
        this.frame++;
    }

    render(ctx) {
        switch (this.currentStage) {
            case 'peek':
                this.renderPeek(ctx);
                break;
            case 'bubble':
                this.renderBubble(ctx);
                break;
            case 'water':
                this.renderWater(ctx);
                break;
            case 'flower':
                this.renderFlower(ctx);
                break;
            case 'drowsy':
                this.renderDrowsy(ctx);
                break;
            case 'sleep':
                this.renderSleep(ctx);
                break;
        }
    }

    // 趴着偷看
    renderPeek(ctx) {
        const { body, head } = this.skeleton;
        // 身体压低
        body.y = 115;
        head.y = 85;
        // 腿收起
        this.skeleton.leftLeg.visible = false;
        this.skeleton.rightLeg.visible = false;
        // 眼睛偶尔左右看
        const lookDir = Math.sin(this.frame * 0.03) * 3;
        head.x = 100 + lookDir;
    }

    // 吹泡泡
    renderBubble(ctx) {
        const { head } = this.skeleton;
        // 画泡泡
        const bubblePhase = (this.frame % 120) / 120;
        const bubbleSize = bubblePhase * 12;
        const bubbleY = head.y - 10 - bubblePhase * 30;
        const bubbleOpacity = 1 - bubblePhase;

        ctx.save();
        ctx.globalAlpha = bubbleOpacity * 0.6;
        ctx.strokeStyle = '#99ccff';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.arc(head.x + 8, bubbleY, bubbleSize, 0, Math.PI * 2);
        ctx.stroke();

        // 泡泡高光
        ctx.beginPath();
        ctx.arc(head.x + 6, bubbleY - bubbleSize * 0.3, bubbleSize * 0.2, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(255,255,255,0.8)';
        ctx.fill();
        ctx.restore();
    }

    // 浇水
    renderWater(ctx) {
        const { rightClaw } = this.skeleton;
        // 右钳举起模拟拿水壶
        rightClaw.rotation = -60;
        rightClaw.y = 60;

        // 水滴
        ctx.save();
        ctx.fillStyle = '#66ccff';
        const dropPhase = (this.frame % 40) / 40;
        const dropY = 75 + dropPhase * 40;
        const dropOpacity = 1 - dropPhase;
        ctx.globalAlpha = dropOpacity;
        ctx.beginPath();
        ctx.ellipse(140, dropY, 2, 3, 0, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();

        // 小花（被浇的）
        ctx.save();
        ctx.fillStyle = '#66cc66';
        ctx.beginPath();
        ctx.moveTo(145, 130);
        ctx.lineTo(145, 145);
        ctx.stroke();
        ctx.fillStyle = '#ffcc00';
        ctx.beginPath();
        ctx.arc(145, 128, 4, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }

    // 摘花放头上
    renderFlower(ctx) {
        const { head } = this.skeleton;
        // 头顶画小花
        ctx.save();
        const flowerX = head.x + 2;
        const flowerY = head.y - 18;

        // 花茎
        ctx.strokeStyle = '#66cc66';
        ctx.lineWidth = 1.5;
        ctx.beginPath();
        ctx.moveTo(flowerX, flowerY + 5);
        ctx.lineTo(flowerX, flowerY);
        ctx.stroke();

        // 花瓣（像素风小花）
        ctx.fillStyle = '#ff99cc';
        const petalSize = 2.5;
        for (let i = 0; i < 5; i++) {
            const angle = (i / 5) * Math.PI * 2;
            const px = flowerX + Math.cos(angle) * 3;
            const py = flowerY + Math.sin(angle) * 3;
            ctx.beginPath();
            ctx.arc(px, py, petalSize, 0, Math.PI * 2);
            ctx.fill();
        }

        // 花心
        ctx.fillStyle = '#ffcc00';
        ctx.beginPath();
        ctx.arc(flowerX, flowerY, 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }

    // 打瞌睡
    renderDrowsy(ctx) {
        const { head, body } = this.skeleton;
        // 头微微下垂摇摆
        const nod = Math.sin(this.frame * 0.05) * 3;
        head.y = 65 + Math.abs(nod);
        head.rotation = nod * 0.5;

        // 偶尔冒小z
        if (this.frame % 80 < 40) {
            ctx.save();
            ctx.font = '10px monospace';
            ctx.fillStyle = 'rgba(100,100,100,0.5)';
            ctx.fillText('z', head.x + 15, head.y - 15);
            ctx.restore();
        }
    }

    // 睡着（头顶zzz）
    renderSleep(ctx) {
        const { head, body } = this.skeleton;
        // 身体趴下
        body.y = 112;
        head.y = 80;
        head.rotation = -5;
        this.skeleton.leftLeg.visible = false;
        this.skeleton.rightLeg.visible = false;
        // 钳子放下
        this.skeleton.leftClaw.rotation = -10;
        this.skeleton.rightClaw.rotation = 10;
        this.skeleton.leftClaw.y = 100;
        this.skeleton.rightClaw.y = 100;

        // zzz动画
        ctx.save();
        ctx.font = '10px monospace';
        ctx.fillStyle = 'rgba(100,100,200,0.7)';
        
        const zPhase = (this.frame % 90) / 90;
        const z1Y = head.y - 20 - zPhase * 15;
        const z1Opacity = 1 - zPhase;
        
        ctx.globalAlpha = z1Opacity;
        ctx.fillText('z', head.x + 12, z1Y);
        
        ctx.globalAlpha = z1Opacity * 0.7;
        ctx.font = '12px monospace';
        ctx.fillText('Z', head.x + 18, z1Y - 8);
        
        ctx.globalAlpha = z1Opacity * 0.4;
        ctx.font = '14px monospace';
        ctx.fillText('Z', head.x + 22, z1Y - 18);
        ctx.restore();
    }

    // 唤醒动画（伸懒腰）
    renderWake(ctx, progress) {
        const { head, body, leftClaw, rightClaw } = this.skeleton;
        
        // 身体慢慢起来
        body.y = 112 - progress * 12;
        head.y = 80 - progress * 20;
        head.rotation = -5 + progress * 5;
        
        // 钳子伸展（伸懒腰）
        leftClaw.rotation = -10 - progress * 50;
        rightClaw.rotation = 10 + progress * 50;
        leftClaw.y = 100 - progress * 20;
        rightClaw.y = 100 - progress * 20;
    }
}

/**
 * App行为装饰物渲染
 */
class AppDecorationRenderer {
    constructor(ctx, skeleton) {
        this.ctx = ctx;
        this.skeleton = skeleton;
        this.currentDecoration = null;
        this.frame = 0;
    }

    setDecoration(type) {
        this.currentDecoration = type;
        this.frame = 0;
    }

    clear() {
        this.currentDecoration = null;
    }

    update() {
        this.frame++;
    }

    render() {
        if (!this.currentDecoration) return;
        
        switch (this.currentDecoration) {
            case 'music':
                this.renderMusic();
                break;
            case 'shopping':
                this.renderShopping();
                break;
            case 'camera':
                this.renderCamera();
                break;
            case 'game':
                this.renderGame();
                break;
            case 'juggling':
                this.renderJuggling();
                break;
        }
    }

    // 音乐：戴耳机飘音符
    renderMusic() {
        const { head } = this.skeleton;
        const ctx = this.ctx;

        // 耳机
        ctx.save();
        ctx.strokeStyle = '#333';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(head.x, head.y - 5, 12, Math.PI * 1.1, Math.PI * 1.9);
        ctx.stroke();

        // 耳机垫
        ctx.fillStyle = '#333';
        ctx.beginPath();
        ctx.arc(head.x - 11, head.y, 3, 0, Math.PI * 2);
        ctx.arc(head.x + 11, head.y, 3, 0, Math.PI * 2);
        ctx.fill();

        // 飘音符
        const notePhase = (this.frame % 60) / 60;
        const noteY = head.y - 20 - notePhase * 25;
        const noteX = head.x + 15 + Math.sin(notePhase * Math.PI * 2) * 8;
        ctx.globalAlpha = 1 - notePhase;
        ctx.font = '12px serif';
        ctx.fillStyle = '#cc66ff';
        ctx.fillText('♪', noteX, noteY);

        // 第二个音符（错位）
        const notePhase2 = ((this.frame + 30) % 60) / 60;
        const noteY2 = head.y - 20 - notePhase2 * 25;
        const noteX2 = head.x - 10 + Math.sin(notePhase2 * Math.PI * 2) * 6;
        ctx.globalAlpha = 1 - notePhase2;
        ctx.fillText('♫', noteX2, noteY2);
        ctx.restore();
    }

    // 购物：看清单
    renderShopping() {
        const { leftClaw } = this.skeleton;
        const ctx = this.ctx;

        // 左钳举起拿清单
        leftClaw.rotation = -60;

        ctx.save();
        // 小清单纸
        ctx.fillStyle = '#ffffee';
        ctx.strokeStyle = '#ccc';
        ctx.lineWidth = 1;
        ctx.fillRect(45, 50, 18, 24);
        ctx.strokeRect(45, 50, 18, 24);

        // 清单上的小横线
        ctx.strokeStyle = '#999';
        ctx.lineWidth = 0.5;
        for (let i = 0; i < 4; i++) {
            ctx.beginPath();
            ctx.moveTo(48, 56 + i * 5);
            ctx.lineTo(60, 56 + i * 5);
            ctx.stroke();
        }

        // 勾勾
        ctx.strokeStyle = '#ff6666';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(48, 56);
        ctx.lineTo(50, 58);
        ctx.lineTo(53, 54);
        ctx.stroke();
        ctx.restore();
    }

    // 相机：拿相机
    renderCamera() {
        const { rightClaw } = this.skeleton;
        const ctx = this.ctx;

        rightClaw.rotation = 50;

        ctx.save();
        // 相机身体
        ctx.fillStyle = '#444';
        ctx.fillRect(138, 65, 20, 14);
        // 镜头
        ctx.fillStyle = '#222';
        ctx.beginPath();
        ctx.arc(148, 72, 5, 0, Math.PI * 2);
        ctx.fill();
        // 镜头高光
        ctx.fillStyle = '#667';
        ctx.beginPath();
        ctx.arc(146, 70, 1.5, 0, Math.PI * 2);
        ctx.fill();
        // 闪光灯
        ctx.fillStyle = '#ffcc00';
        ctx.beginPath();
        ctx.arc(153, 67, 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }

    // 游戏：拿游戏机
    renderGame() {
        const { leftClaw, rightClaw } = this.skeleton;
        const ctx = this.ctx;

        leftClaw.rotation = -15;
        rightClaw.rotation = 15;

        ctx.save();
        // 游戏机主体
        const gx = 100, gy = 75;
        ctx.fillStyle = '#336';
        ctx.beginPath();
        ctx.roundRect(gx - 14, gy - 6, 28, 14, 3);
        ctx.fill();
        // 屏幕
        ctx.fillStyle = '#8f8';
        ctx.fillRect(gx - 5, gy - 4, 10, 8);
        // 按键
        ctx.fillStyle = '#cc3333';
        ctx.beginPath();
        ctx.arc(gx + 9, gy - 1, 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#3333cc';
        ctx.beginPath();
        ctx.arc(gx + 9, gy + 3, 2, 0, Math.PI * 2);
        ctx.fill();
        // 十字键
        ctx.fillStyle = '#222';
        ctx.fillRect(gx - 11, gy - 1, 5, 2);
        ctx.fillRect(gx - 10, gy - 2, 2, 5);
        ctx.restore();
    }

    // 杂耍模式
    renderJuggling() {
        const { leftClaw, rightClaw, head } = this.skeleton;
        const ctx = this.ctx;

        // 钳子快速摆动
        leftClaw.rotation = -30 + Math.sin(this.frame * 0.2) * 30;
        rightClaw.rotation = 30 + Math.cos(this.frame * 0.2) * 30;

        // 头顶抛出小球
        ctx.save();
        const balls = ['#ff6666', '#66ff66', '#6666ff'];
        for (let i = 0; i < 3; i++) {
            const phase = ((this.frame * 3 + i * 40) % 120) / 120;
            const angle = phase * Math.PI * 2;
            const bx = head.x + Math.cos(angle) * 20;
            const by = head.y - 25 + Math.sin(angle) * 10;
            ctx.fillStyle = balls[i];
            ctx.beginPath();
            ctx.arc(bx, by, 3, 0, Math.PI * 2);
            ctx.fill();
        }
        ctx.restore();
    }
}

// 导出给主引擎使用
window.BubbleSystem = BubbleSystem;
window.IdleAnimationSystem = IdleAnimationSystem;
window.AppDecorationRenderer = AppDecorationRenderer;