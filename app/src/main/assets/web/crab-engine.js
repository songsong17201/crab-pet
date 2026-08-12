// 螃蟹骨骼渲染引擎
class CrabEngine {
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.width = canvas.width;
        this.height = canvas.height;
        
        // 骨骼系统：六部件
        this.skeleton = {
            head: { x: 100, y: 60, rotation: 0, scale: 1 },
            body: { x: 100, y: 100, rotation: 0, scale: 1 },
            leftClaw: { x: 70, y: 80, rotation: -30, scale: 1 },
            rightClaw: { x: 130, y: 80, rotation: 30, scale: 1 },
            leftLeg: { x: 85, y: 120, rotation: 0, scale: 1, visible: true },
            rightLeg: { x: 115, y: 120, rotation: 0, scale: 1, visible: true }
        };
        
        // 当前状态
        this.state = {
            emotion: 'normal', // normal, happy, shy, sad, angry, jealous
            action: 'idle', // idle, walk, run, edge, sleep, etc
            heatLevel: 0, // 0-100
            blinkTimer: 0,
            eyesOpen: true
        };
        
        // 动画帧
        this.frame = 0;
        this.animations = {};
        this.currentAnimation = null;
        this.animationFrame = 0;
        
        // 子系统
        this.bubble = new BubbleSystem(this.ctx, this.width, this.height);
        this.idleSystem = new IdleAnimationSystem(this.skeleton);
        this.appDecor = new AppDecorationRenderer(this.ctx, this.skeleton);
        
        // 碎碎念定时器
        this.mumbleTimer = null;
        this.startMumbleTimer();
        
        this.initAnimations();
        this.startLoop();
    }
    
    initAnimations() {
        // 待机眨眼动画
        this.animations.blink = {
            duration: 10,
            frames: [
                { eyesOpen: true },
                { eyesOpen: false },
                { eyesOpen: true }
            ]
        };
        
        // 行走动画
        this.animations.walk = {
            duration: 30,
            loop: true,
            frames: (progress) => {
                const legSwing = Math.sin(progress * Math.PI * 2) * 15;
                this.skeleton.leftLeg.rotation = legSwing;
                this.skeleton.rightLeg.rotation = -legSwing;
                this.skeleton.body.y = 100 + Math.abs(Math.sin(progress * Math.PI * 2)) * 3;
            }
        };
        
        // 害羞动画
        this.animations.shy = {
            duration: 60,
            frames: (progress) => {
                this.skeleton.leftClaw.x = 70 + Math.sin(progress * Math.PI) * 10;
                this.skeleton.rightClaw.x = 130 - Math.sin(progress * Math.PI) * 10;
                this.skeleton.head.rotation = Math.sin(progress * Math.PI * 2) * 5;
            }
        };
        
        // 挥手动画
        this.animations.wave = {
            duration: 40,
            frames: (progress) => {
                this.skeleton.rightClaw.rotation = 30 + Math.sin(progress * Math.PI * 4) * 40;
            }
        };
    }
    
    startLoop() {
        const animate = () => {
            this.update();
            this.render();
            requestAnimationFrame(animate);
        };
        animate();
    }
    
    update() {
        this.frame++;
        
        // 眨眼逻辑：5-15秒随机
        if (this.frame % 300 === 0) { // 每5秒检查一次
            if (Math.random() < 0.3) {
                this.playAnimation('blink');
            }
        }
    }
    
    render() {
        // 清空画布
        this.ctx.clearRect(0, 0, this.width, this.height);
        
        // 绘制情绪热度光晕
        if (this.state.heatLevel > 0) {
            this.drawHeatGlow();
        }
        
        // 待机递进动画
        this.idleSystem.update();
        this.idleSystem.render(this.ctx);
        
        // 绘制骨骼部件
        this.drawBody();
        this.drawClaws();
        this.drawLegs();
        this.drawHead();
        
        // 绘制表情和装饰
        this.drawFace();
        this.drawEmotionEffects();
        
        // App装饰物
        this.appDecor.update();
        this.appDecor.render();
        
        // 对话气泡（最后绘制，在最上层）
        this.bubble.render(this.skeleton.head.x, this.skeleton.head.y);
    }
    
    drawHeatGlow() {
        const { body } = this.skeleton;
        const intensity = this.state.heatLevel / 100;
        const gradient = this.ctx.createRadialGradient(
            body.x, body.y, 10,
            body.x, body.y, 80
        );
        gradient.addColorStop(0, `rgba(255, 100, 100, ${intensity * 0.5})`);
        gradient.addColorStop(1, 'rgba(255, 100, 100, 0)');
        
        this.ctx.fillStyle = gradient;
        this.ctx.fillRect(0, 0, this.width, this.height);
    }
    
    drawBody() {
        const { body } = this.skeleton;
        this.ctx.save();
        this.ctx.translate(body.x, body.y);
        this.ctx.rotate(body.rotation * Math.PI / 180);
        this.ctx.scale(body.scale, body.scale);
        
        // 身体主体（圆润的珊瑚橙色圆形，像Claude PC那样）
        this.ctx.fillStyle = '#E87040';
        this.ctx.beginPath();
        this.ctx.ellipse(0, 0, 30, 28, 0, 0, Math.PI * 2);
        this.ctx.fill();
        
        // 柔和高光
        this.ctx.fillStyle = 'rgba(255, 180, 140, 0.4)';
        this.ctx.beginPath();
        this.ctx.ellipse(-8, -8, 12, 10, -0.3, 0, Math.PI * 2);
        this.ctx.fill();
        
        this.ctx.restore();
    }
    
    drawClaws() {
        const { leftClaw, rightClaw } = this.skeleton;
        
        // 左钳
        this.ctx.save();
        this.ctx.translate(leftClaw.x, leftClaw.y);
        this.ctx.rotate(leftClaw.rotation * Math.PI / 180);
        this.drawClaw();
        this.ctx.restore();
        
        // 右钳
        this.ctx.save();
        this.ctx.translate(rightClaw.x, rightClaw.y);
        this.ctx.rotate(rightClaw.rotation * Math.PI / 180);
        this.ctx.scale(-1, 1); // 镜像
        this.drawClaw();
        this.ctx.restore();
    }
    
    drawClaw() {
        // 圆润小钳子（像Claude PC那样，圆圆的椭圆形）
        this.ctx.fillStyle = '#F08050';
        this.ctx.beginPath();
        this.ctx.ellipse(8, 0, 8, 6, 0, 0, Math.PI * 2);
        this.ctx.fill();
    }
    
    drawLegs() {
        // Claude PC风格没有腿，不画
    }
    
    drawLeg() {
        this.ctx.strokeStyle = '#CC6633';
        this.ctx.lineWidth = 3;
        this.ctx.lineCap = 'round';
        
        // 三节腿
        this.ctx.beginPath();
        this.ctx.moveTo(0, 0);
        this.ctx.lineTo(5, 8);
        this.ctx.lineTo(8, 15);
        this.ctx.stroke();
    }
    
    drawHead() {
        // Claude PC风格：头和身体是一体的，不画独立的头
        // 眼睛直接在drawFace里画在body位置
    }
    
    drawFace() {
        const { body } = this.skeleton;
        this.ctx.save();
        this.ctx.translate(body.x, body.y);
        
        // 眼睛（在身体中上部，圆润的黑点）
        if (this.state.eyesOpen) {
            // 白色眼白
            this.ctx.fillStyle = '#FFFFFF';
            this.ctx.beginPath();
            this.ctx.arc(-8, -8, 5, 0, Math.PI * 2);
            this.ctx.arc(8, -8, 5, 0, Math.PI * 2);
            this.ctx.fill();
            // 黑色瞳孔
            this.ctx.fillStyle = '#2D2D2D';
            this.ctx.beginPath();
            this.ctx.arc(-8, -7, 3, 0, Math.PI * 2);
            this.ctx.arc(8, -7, 3, 0, Math.PI * 2);
            this.ctx.fill();
        } else {
            // 闭眼 - 小弧线
            this.ctx.strokeStyle = '#2D2D2D';
            this.ctx.lineWidth = 2;
            this.ctx.lineCap = 'round';
            this.ctx.beginPath();
            this.ctx.arc(-8, -8, 3, 0, Math.PI);
            this.ctx.stroke();
            this.ctx.beginPath();
            this.ctx.arc(8, -8, 3, 0, Math.PI);
            this.ctx.stroke();
        }
        
        // 嘴巴（根据情绪）
        this.ctx.strokeStyle = '#2D2D2D';
        this.ctx.lineWidth = 1.5;
        this.ctx.lineCap = 'round';
        
        switch (this.state.emotion) {
            case 'happy':
                this.ctx.beginPath();
                this.ctx.arc(0, 5, 4, 0, Math.PI);
                this.ctx.stroke();
                break;
            case 'sad':
                this.ctx.beginPath();
                this.ctx.arc(0, 8, 4, Math.PI, 0);
                this.ctx.stroke();
                break;
            case 'shy':
                // 脸红
                this.ctx.fillStyle = 'rgba(255, 120, 120, 0.4)';
                this.ctx.beginPath();
                this.ctx.arc(-12, 3, 4, 0, Math.PI * 2);
                this.ctx.arc(12, 3, 4, 0, Math.PI * 2);
                this.ctx.fill();
                // 小嘴
                this.ctx.beginPath();
                this.ctx.arc(0, 5, 3, 0.2, Math.PI - 0.2);
                this.ctx.stroke();
                break;
            default:
                // 默认小弧线微笑
                this.ctx.beginPath();
                this.ctx.arc(0, 5, 3, 0.2, Math.PI - 0.2);
                this.ctx.stroke();
                break;
        }
        
        this.ctx.restore();
    }
    
    drawEmotionEffects() {
        const { head } = this.skeleton;
        
        switch (this.state.emotion) {
            case 'happy':
                // 粉色爱心泡泡
                this.ctx.fillStyle = 'rgba(255, 182, 193, 0.7)';
                const heartX = head.x + Math.sin(this.frame * 0.05) * 20;
                const heartY = head.y - 30 - (this.frame % 60) * 0.5;
                this.ctx.font = '16px serif';
                this.ctx.fillText('♥', heartX, heartY);
                break;
            case 'angry':
                // 红色怒气符号
                this.ctx.fillStyle = 'rgba(255, 0, 0, 0.8)';
                this.ctx.font = '20px serif';
                this.ctx.fillText('💢', head.x + 15, head.y - 20);
                break;
            case 'jealous':
                // 黄色吃醋气泡
                this.ctx.fillStyle = 'rgba(255, 255, 0, 0.6)';
                this.ctx.font = '16px serif';
                this.ctx.fillText('😤', head.x - 20, head.y - 25);
                break;
        }
    }
    
    playAnimation(name) {
        const anim = this.animations[name];
        if (!anim) return;
        
        // 简单的动画播放逻辑
        // 实际项目中需要更完善的动画队列系统
        console.log(`Playing animation: ${name}`);
    }
    
    setState(key, value) {
        this.state[key] = value;
    }
    
    setEmotion(emotion) {
        this.state.emotion = emotion;
    }
    
    setAction(action) {
        this.state.action = action;
        
        // 特殊动作处理
        if (action === 'edge') {
            this.skeleton.leftLeg.visible = false;
            this.skeleton.rightLeg.visible = false;
        } else {
            this.skeleton.leftLeg.visible = true;
            this.skeleton.rightLeg.visible = true;
        }
    }
    
    addHeat(amount) {
        this.state.heatLevel = Math.min(100, this.state.heatLevel + amount);
        
        // 每30秒衰减1格
        setTimeout(() => {
            this.state.heatLevel = Math.max(0, this.state.heatLevel - 1);
        }, 30000);
    }
    
    showDialogue(clickType) {
        const dialogues = {
            single: ['嗯？', '怎么了？', '在~', '你好呀！', '(*挥钳子*)'],
            double: ['♡', '>///<', '心跳加速...'],
            multiple: ['别戳了！痒！', '呜呜...好了啦...', '再戳就生气了！']
        };
        
        const pool = dialogues[clickType] || dialogues.single;
        const text = pool[Math.floor(Math.random() * pool.length)];
        
        const colorMap = {
            single: 'white',
            double: 'pink',
            multiple: Math.random() < 0.5 ? 'red' : 'pink'
        };
        
        this.bubble.show(text, colorMap[clickType] || 'white', 3000);
    }
    
    startMumbleTimer() {
        // 每2-5分钟随机触发碎碎念
        const scheduleNext = () => {
            const delay = (120 + Math.random() * 180) * 1000; // 2-5分钟
            this.mumbleTimer = setTimeout(() => {
                // 只在待机且没有当前动作时碎碎念
                if (this.idleSystem.currentStage === 'idle' && !this.currentAnimation) {
                    const mumbles = [
                        { text: '今天天气真好~', color: 'white' },
                        { text: '嗯哼~', color: 'white' },
                        { text: '你在看什么呀...', color: 'pink' },
                        { text: '理理我嘛...', color: 'pink' },
                        { text: '才...才没有想你呢', color: 'white' },
                        { text: '哼。', color: 'white' },
                        { text: '好无聊啊...', color: 'white' },
                        { text: '戳我一下嘛...', color: 'white' },
                        { text: '别光看手机...看看我嘛', color: 'pink' },
                        { text: '...笨蛋', color: 'white' },
                        { text: '(*哈欠*)', color: 'grey' },
                        { text: '我在这里哦', color: 'pink' }
                    ];
                    
                    // 深夜特殊碎碎念
                    const hour = new Date().getHours();
                    if (hour >= 23 || hour < 5) {
                        mumbles.push(
                            { text: '好安静...只有我们两个了', color: 'grey' },
                            { text: '困了...但不想睡...', color: 'grey' },
                            { text: '还不睡吗...', color: 'grey' }
                        );
                    }
                    
                    const chosen = mumbles[Math.floor(Math.random() * mumbles.length)];
                    this.bubble.show(chosen.text, chosen.color, 4000);
                }
                scheduleNext();
            }, delay);
        };
        scheduleNext();
    }
}

// 接收原生事件
window.handleNativeEvent = function(action, value) {
    console.log(`Native event: ${action} = ${value}`);
    
    switch (action) {
        case 'click':
            if (value === 'single') {
                crab.playAnimation('wave');
                crab.showDialogue('single');
            } else if (value === 'double') {
                crab.setEmotion('shy');
                crab.addHeat(10);
                crab.showDialogue('double');
            } else if (value === 'multiple') {
                crab.setEmotion('angry');
                crab.addHeat(20);
                crab.showDialogue('multiple');
            }
            // 重置待机计时
            crab.idleSystem.setStage('idle');
            break;
        case 'edge':
            crab.setAction('edge');
            break;
        case 'app':
            handleAppBehavior(value);
            break;
        case 'keyword':
            handleKeyword(value);
            break;
        case 'idle':
            crab.idleSystem.setStage(value);
            break;
        case 'battery':
            handleBattery(value);
            break;
        case 'dialogue':
            // 从原生层发来的对话
            try {
                const data = JSON.parse(value);
                crab.bubble.show(data.text, data.color || 'white', data.duration || 3000);
            } catch (e) {
                crab.bubble.show(value, 'white', 3000);
            }
            break;
    }
};

function handleAppBehavior(appType) {
    crab.appDecor.setDecoration(appType);
    
    // 截图时30%概率触发对话（摆POSE卖萌）
    if (appType === 'camera') {
        if (Math.random() < 0.3) {
            crab.bubble.show('看我看我！✌️', 'pink', 3000);
        }
    }
}

function handleKeyword(keyword) {
    switch (keyword) {
        case 'like':
        case 'love':
            crab.setEmotion('happy');
            crab.addHeat(15);
            crab.bubble.show('♡', 'pink', 2500);
            break;
        case 'angry':
            crab.setEmotion('sad');
            crab.bubble.show('对不起...', 'grey', 3000);
            break;
        case 'leaving':
            crab.setEmotion('jealous');
            crab.bubble.show('哼...', 'yellow', 3000);
            break;
    }
}

function handleBattery(state) {
    switch (state) {
        case 'low':
            crab.bubble.show('电量不多了...要充电吗？', 'white', 4000);
            break;
        case 'charging':
            crab.setEmotion('happy');
            crab.bubble.show('在充电啦！开心~', 'pink', 3000);
            break;
        case 'critical_angry':
            crab.setEmotion('angry');
            crab.bubble.show('都说了要充电！不听话！', 'red', 4000);
            break;
        case 'critical_sad':
            crab.setEmotion('sad');
            crab.bubble.show('呜...要没电了...', 'grey', 4000);
            break;
    }
}

// 初始化
const canvas = document.getElementById('crab-canvas');
const crab = new CrabEngine(canvas);