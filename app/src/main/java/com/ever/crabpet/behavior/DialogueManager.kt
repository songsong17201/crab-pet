package com.ever.crabpet.behavior

import java.util.Calendar

/**
 * 对话系统：时段对话库 + 场景提醒 + 碎碎念
 */
class DialogueManager {

    data class Dialogue(
        val text: String,
        val bubbleColor: String = "white", // white/pink/red/yellow/green/grey
        val emotion: String = "normal"
    )

    /**
     * 时段对话
     */
    fun getTimedDialogue(): Dialogue? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val pool = when (hour) {
            in 6..9 -> morningDialogues
            in 10..12 -> noonDialogues
            in 13..17 -> afternoonDialogues
            in 18..22 -> eveningDialogues
            else -> nightDialogues
        }
        return pool.randomOrNull()
    }

    /**
     * 碎碎念自言自语
     */
    fun getMumble(category: MumbleCategory = MumbleCategory.RANDOM): Dialogue {
        val pool = when (category) {
            MumbleCategory.DAILY -> dailyMumbles
            MumbleCategory.CLINGY -> clingyMumbles
            MumbleCategory.TSUNDERE -> tsundereMumbles
            MumbleCategory.NIGHT -> nightMumbles
            MumbleCategory.BORED -> boredMumbles
            MumbleCategory.RANDOM -> allMumbles
        }
        return pool.random()
    }

    /**
     * 充电相关对话
     */
    fun getBatteryDialogue(state: IdleBehaviorManager.BatteryState): Dialogue {
        return when (state) {
            IdleBehaviorManager.BatteryState.LOW_25 -> Dialogue(
                "电量不多了...要充电吗？", "white", "normal"
            )
            IdleBehaviorManager.BatteryState.CHARGING_HAPPY -> Dialogue(
                "在充电啦！开心~", "pink", "happy"
            )
            IdleBehaviorManager.BatteryState.CRITICAL_ANGRY -> Dialogue(
                "都说了要充电！不听话！", "red", "angry"
            )
            IdleBehaviorManager.BatteryState.CRITICAL_SAD -> Dialogue(
                "呜...要没电了...你不心疼我吗...", "grey", "sad"
            )
        }
    }

    /**
     * 点击交互对话
     */
    fun getClickDialogue(clickType: String): Dialogue {
        return when (clickType) {
            "single" -> singleClickDialogues.random()
            "double" -> Dialogue("♡", "pink", "shy")
            "multiple" -> {
                if (Math.random() < 0.5) {
                    Dialogue("别戳了！痒！", "red", "angry")
                } else {
                    Dialogue("呜呜...好了啦...", "pink", "shy")
                }
            }
            else -> Dialogue("?", "white", "normal")
        }
    }

    /**
     * 场景提醒对话
     */
    fun getSceneReminder(): Dialogue? {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            7, 12, 18 -> Dialogue("该吃饭了哦~", "white", "normal")
            23 -> Dialogue("很晚了...该睡觉了...", "grey", "normal")
            0, 1, 2 -> Dialogue("还不睡吗...我困了...", "grey", "sad")
            else -> null
        }
    }

    enum class MumbleCategory {
        DAILY, CLINGY, TSUNDERE, NIGHT, BORED, RANDOM
    }

    // ============ 对话库 ============

    private val morningDialogues = listOf(
        Dialogue("早上好~今天也要加油哦", "white", "happy"),
        Dialogue("醒了醒了！新的一天开始了~", "white", "happy"),
        Dialogue("早安...昨晚睡得好吗？", "white", "normal"),
        Dialogue("嗯...再让我趴一会儿...", "grey", "normal")
    )

    private val noonDialogues = listOf(
        Dialogue("中午了，记得吃饭哦", "white", "normal"),
        Dialogue("好饿...你吃了吗？", "white", "normal"),
        Dialogue("午饭时间！不许减肥！", "white", "normal")
    )

    private val afternoonDialogues = listOf(
        Dialogue("下午了~要不要休息一下？", "white", "normal"),
        Dialogue("困了就趴一会儿嘛", "white", "normal"),
        Dialogue("今天过得怎么样呀？", "white", "happy")
    )

    private val eveningDialogues = listOf(
        Dialogue("晚上好~今天辛苦了", "white", "happy"),
        Dialogue("晚饭吃了吗？", "white", "normal"),
        Dialogue("晚上的风好舒服~", "white", "happy")
    )

    private val nightDialogues = listOf(
        Dialogue("该睡了...明天见？", "grey", "normal"),
        Dialogue("晚安...做个好梦...", "grey", "happy"),
        Dialogue("这么晚了还不睡...陪你待会儿吧", "grey", "normal")
    )

    private val dailyMumbles = listOf(
        Dialogue("今天天气真好~", "white", "happy"),
        Dialogue("嗯哼~", "white", "normal"),
        Dialogue("...", "white", "normal")
    )

    private val clingyMumbles = listOf(
        Dialogue("你在看什么呀...给我看看嘛", "pink", "normal"),
        Dialogue("理理我嘛...", "pink", "normal"),
        Dialogue("我在这里哦", "pink", "happy"),
        Dialogue("别光看手机...看看我嘛", "pink", "normal")
    )

    private val tsundereMumbles = listOf(
        Dialogue("才...才没有想你呢", "white", "shy"),
        Dialogue("哼。", "white", "normal"),
        Dialogue("不是在等你...只是刚好在这里而已", "white", "shy"),
        Dialogue("...笨蛋", "white", "normal")
    )

    private val nightMumbles = listOf(
        Dialogue("好安静...只有我们两个了", "grey", "normal"),
        Dialogue("困了...但不想睡...", "grey", "normal"),
        Dialogue("月亮好圆...", "grey", "happy")
    )

    private val boredMumbles = listOf(
        Dialogue("好无聊啊...", "white", "normal"),
        Dialogue("(*哈欠*)", "white", "normal"),
        Dialogue("戳我一下嘛...", "white", "normal"),
        Dialogue("...有蚂蚁在爬", "white", "normal")
    )

    private val singleClickDialogues = listOf(
        Dialogue("嗯？", "white", "normal"),
        Dialogue("怎么了？", "white", "normal"),
        Dialogue("在~", "white", "happy"),
        Dialogue("你好呀！", "white", "happy"),
        Dialogue("(*挥钳子*)", "white", "happy")
    )

    private val allMumbles by lazy {
        dailyMumbles + clingyMumbles + tsundereMumbles + nightMumbles + boredMumbles
    }

    // 充电相关对话
    private val chargingDialogues = mapOf(
        "low" to listOf(
            "电量不多了...要充电吗？",
            "快没电了，充一下吧~",
            "电量有点低了哦"
        ),
        "critical_angry" to listOf(
            "都说了要充电！你不听！",
            "哼，不充电我就生气了！",
            "再不充电我就罢工！"
        ),
        "critical_sad" to listOf(
            "呜...要没电了...",
            "我快要消失了...充电...",
            "好黑暗...救救我..."
        ),
        "charging" to listOf(
            "在充电啦！开心~",
            "嘿嘿有电了有电了",
            "充电好舒服~"
        )
    )

    fun getChargingDialogue(event: String): String {
        return chargingDialogues[event]?.random() ?: "电量..."
    }
}