package net.neoforged.camelot.listener;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.neoforged.camelot.Database;
import net.neoforged.camelot.script.ScriptContext;
import net.neoforged.camelot.script.ScriptReplier;
import net.neoforged.camelot.script.ScriptUtils;
import org.jetbrains.annotations.NotNull;
import net.neoforged.camelot.db.schemas.Trick;
import net.neoforged.camelot.db.transactionals.TricksDAO;

import java.util.EnumSet;

/**
 * A listener listening for {@link MessageReceivedEvent} and seeing if they match a trick alias, which if found,
 * will be executed with the arguments.
 */
public record TrickListener(String prefix) implements EventListener {
    private static final EnumSet<Message.MentionType> ALLOWED_MENTIONS = EnumSet.of(
            Message.MentionType.CHANNEL, Message.MentionType.EMOJI, Message.MentionType.SLASH_COMMAND
    );

    public void onEvent(@NotNull GenericEvent gevent) {
        if (!(gevent instanceof MessageReceivedEvent event)) return;
        if (!event.isFromGuild() || event.getAuthor().isBot() || event.getAuthor().isSystem()) return;

        final String content = event.getMessage().getContentRaw();
        if (content.startsWith(prefix)) {
            final int nextSpace = content.indexOf(' ');
            final String trickName = content.substring(1, nextSpace < 0 ? content.length() : nextSpace);
            final Trick trick = Database.main().withExtension(TricksDAO.class, db -> db.getNamedTrick(trickName));

            if (trick == null) return;

            final String args = nextSpace < 0 ? "" : content.substring(nextSpace + 1);

            final ScriptContext context = new ScriptContext(event.getJDA(), event.getGuild(), event.getMember(), event.getChannel(), new ScriptReplier() {
                Message reply;

                @Override
                protected RestAction<?> doSend(MessageCreateData createData) {
                    synchronized (this) {
                        if (reply == null) {
                            return event.getMessage().reply(createData)
                                    .setAllowedMentions(ALLOWED_MENTIONS)
                                    .onSuccess(msg -> this.reply = msg);
                        } else {
                            return reply.editMessage(MessageEditData.fromCreateData(createData));
                        }
                    }
                }
            });

            ScriptUtils.submitExecution(context, trick.script(), args);
        }
    }
}
