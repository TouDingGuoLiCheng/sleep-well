package com.sleepwell.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.liein.LieInState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Speech bubbles for lie-ins and naps, in the Wine Fox voice.
 * <p>
 * Fired when she actually lies down rather than when the roll succeeds: a maid who has to walk to her bed
 * would otherwise spend the whole bubble on the way there.
 */
public final class MaidBubble {
    /** Wine Fox's own lines; all short, soft, no stage directions. */
    private static final String[] NAP_LINES = {
            "唔……眼皮好重，我去眯一小会儿，真的一小会~",
            "偷偷睡一觉...主人不会发现吧？",
            "今天好像特别困…补个觉吧~"
    };
    private static final String[] LIE_IN_LINES = {
            "主人...再睡五分钟...就五分钟",
            "再一下下……被子里好暖和，外面好冷啊",
            "唔...这天堂般的温暖是怎么回事！没办法了，接着睡吧~"
    };

    private static final int BUBBLE_TICKS = 150;
    private static final ResourceLocation TYPE_2_BG =
            new ResourceLocation("touhou_little_maid:textures/entity/chat_bubble/type2.png");
    /** Was she asleep last tick, per maid: the bubble belongs to the waking-to-sleeping edge, once. */
    private static final Map<UUID, Boolean> WAS_ASLEEP = new HashMap<>();

    private MaidBubble() {
    }

    /** Call once per tick while a lie-in or nap state is active. */
    public static void tick(EntityMaid maid, LieInState state) {
        UUID id = maid.getUUID();
        boolean asleep = maid.isSleeping();
        Boolean before = WAS_ASLEEP.put(id, asleep);
        if (!asleep || Boolean.TRUE.equals(before) || !state.active()) {
            return;
        }
        if (state.kind() == LieInState.Kind.NAP) {
            say(maid, pick(NAP_LINES, maid));
        } else if (state.kind() == LieInState.Kind.LIE_IN) {
            say(maid, pick(LIE_IN_LINES, maid));
        }
    }

    /** Clears the edge tracker so a released state cannot leave a stale flag behind. */
    public static void forget(UUID id) {
        WAS_ASLEEP.remove(id);
    }

    private static String pick(String[] lines, EntityMaid maid) {
        return lines[maid.getRandom().nextInt(lines.length)];
    }

    private static void say(EntityMaid maid, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        maid.getChatBubbleManager().addChatBubble(
                TextChatBubbleData.create(BUBBLE_TICKS, Component.literal(text), TYPE_2_BG, 0));
    }
}
