package pe.chalk.telegram.alyricbot;

import de.vivistra.telegrambot.client.BotRequest;
import de.vivistra.telegrambot.model.message.AudioMessage;
import de.vivistra.telegrambot.model.message.Message;
import de.vivistra.telegrambot.model.message.MessageType;
import de.vivistra.telegrambot.model.message.TextMessage;
import de.vivistra.telegrambot.receiver.IReceiverService;
import de.vivistra.telegrambot.receiver.Receiver;
import de.vivistra.telegrambot.sender.Sender;
import de.vivistra.telegrambot.settings.BotSettings;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot implements IReceiverService {
    public static String TOKEN = "";
    public static final ContentType CONTENT_TYPE = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), MIME.UTF8_CHARSET);

    private LyricManager manager;

    @FunctionalInterface
    public interface Factory <T> {
        T get() throws Throwable;
    }

    public static void main(String[] args) throws IOException{
        new ALyricBot(args[0]);
    }

    public ALyricBot(String token) throws IOException{
        ALyricBot.TOKEN = token;
        this.manager = new LyricManager(Paths.get("cache"));

        BotSettings.setApiToken(ALyricBot.TOKEN);
        Receiver.subscribe(this);
    }

    public void received(Message message){
        if(message.getMessageType() == MessageType.AUDIO_MESSAGE){
            this.search(message, () -> this.manager.getHash((AudioMessage) message));
        }else if(message.getMessageType() == MessageType.TEXT_MESSAGE){
            String[] commands = message.getMessage().toString().split(" ");
            if(!commands[0].startsWith("/")) return;

            if(commands[0].contains("@")){
                String[] mainCommand = commands[0].split("@");
                if(mainCommand.length >= 2 && !mainCommand[1].equalsIgnoreCase("ALyricBot")) return;

                commands[0] = mainCommand[0];
            }

            if(!commands[0].equalsIgnoreCase("/lyric")) return;
            if(commands.length <= 1){
                ALyricBot.reply(message, "\uD83C\uDF10 https://github.com/ChalkPE/ALyricBot\n\nUsage: /lyric <URL OF MUSIC FILE>");
                return;
            }

            if(!commands[1].startsWith("http://") && !commands[1].startsWith("https://"))commands[1] = "http://" + commands[1];
            this.search(message, () -> this.manager.getHash(message, String.join(" ", Arrays.copyOfRange(commands, 1, commands.length))));
        }
    }

    public void search(final Message message, final Factory<String> hashFactory){
        new Thread(() -> {
            try{
                /* #1 - DOWNLOAD AUDIO FILE FROM STREAM */
                ALyricBot.reply(message, "⚫️⚪️⚪️  Getting an information...");
                String hash = hashFactory.get();
                if(hash.equals("*INVALID*")) throw new IllegalArgumentException("Invalid music file!");

                /* #2 - SEARCH FROM ALSONG SERVER */
                String lyrics = this.manager.getLyrics(hash, message);

                /* #3 - REPLY RESULT AND DELETE FILE */
                ALyricBot.reply(message, lyrics == null ? "❌ There are no lyrics for this music :(" : lyrics);
            }catch(Throwable e){
                ALyricBot.reply(message, e);
            }
        }).start();
    }

    public static void reply(Message message, Throwable e){
        String errorMessage = e.getMessage();
        if(errorMessage == null) errorMessage = "";

        ALyricBot.reply(message, "⁉️ ERROR: " + e.getClass().getSimpleName() + ": " + errorMessage.replaceAll("https?://\\S*", "[DATA EXPUNGED]"));
        e.printStackTrace();
    }

    public static void reply(Message message, String content){
        try{
            Field messageIdField = Message.class.getDeclaredField("messageID");
            messageIdField.setAccessible(true);
            Integer messageId = (Integer) messageIdField.get(message);

            ALyricBot.reply(message.isFromGroupChat() ? message.getGroupChat().getId() : message.getSender().getId(), messageId, content);
        }catch(Throwable e){
            e.printStackTrace();
        }
    }

    public static void reply(int recipient, int messageId, String content){
        if(content == null || content.equals("")){
            return;
        }

        try{
            String nextContent = null;
            if(content.length() >= 4096){
                nextContent = content.substring(4096);
                content = content.substring(0, 4096);
            }

            BotRequest request = new BotRequest(new TextMessage(recipient, content));
            request.getContent().addTextBody("reply_to_message_id", Integer.toString(messageId), ALyricBot.CONTENT_TYPE);
            request.getContent().addTextBody("disable_web_page_preview", Boolean.toString(true), ALyricBot.CONTENT_TYPE);

            Sender.bot.post(request);
            ALyricBot.reply(recipient, messageId, nextContent);
        }catch(Throwable e){
            e.printStackTrace();
        }
    }
}