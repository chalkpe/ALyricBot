package pe.chalk.telegram.alyricbot;

import de.vivistra.telegrambot.client.BotRequest;
import de.vivistra.telegrambot.model.Audio;
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
import org.json.JSONObject;
import org.json.JSONTokener;
import org.khinenw.poweralyric.LyricLib;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot implements IReceiverService {
    private final String TOKEN;
    private final ContentType CONTENT_TYPE = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), MIME.UTF8_CHARSET);

    public static void main(String[] args){
        new ALyricBot(args[0]);
    }

    public ALyricBot(String token){
        this.TOKEN = token;

        BotSettings.setApiToken(this.TOKEN);
        Receiver.subscribe(this);
    }

    public void received(Message message){
        if(message.getMessageType() == MessageType.TEXT_MESSAGE){
            String[] commands = message.getMessage().toString().split(" ");
            if(commands[0].contains("@")){
                commands[0] = commands[0].split("@")[0];
            }

            if(!commands[0].equalsIgnoreCase("/lyric") || commands.length <= 1){
                return;
            }

            if(!commands[1].startsWith("http://") && !commands[1].startsWith("https://")){
                commands[1] = "http://" + commands[1];
            }

            this.search(message, () -> commands[1]);
        }else if(message.getMessageType() == MessageType.AUDIO_MESSAGE){
            this.reply(message, "⚫️⚪️⚪️  Getting file_id...");
            this.search(message, () -> {
                try{
                    return this.getFileURL(((AudioMessage) message).getMessage());
                }catch(Throwable e){
                    this.reply(message, e);
                }
                return null;
            });
        }
    }

    public String getFileURL(Audio audio) throws Throwable {
        Field fileIdField = Audio.class.getDeclaredField("fileId");
        fileIdField.setAccessible(true);
        String fileId = fileIdField.get(audio).toString();

        try(InputStream response = new URL(BotSettings.getApiUrlWithToken() + "getFile?file_id=" + fileId).openStream()){
            return String.format("https://api.telegram.org/file/bot%s/%s", this.TOKEN, new JSONObject(new JSONTokener(response)).getJSONObject("result").getString("file_path"));
        }
    }

    public void search(final Message message, final Supplier<String> urlFactory){
        new Thread(() -> {
            String url = urlFactory.get();
            try(InputStream stream = new URL(url).openStream()){
                /* #1 - DOWNLOAD AUDIO FILE FROM STREAM */
                this.reply(message, "⚫️⚫️⚪️ Downloading audio...");
                Path path = Files.createTempFile("TelegramALyricBotAudioCache", ".tmp");
                Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);

                /* #2 - SEARCH FROM ALSONG SERVER */
                this.reply(message, "⚫️⚫️⚫️ Searching lyrics...");
                Map<Integer, String> lyricsMap = LyricLib.parseLyric(LyricLib.getLyric(LyricLib.getHash(path.toFile())));

                /* #3 - REPLY RESULT AND DELETE FILE */
                this.reply(message, lyricsMap.isEmpty() ? "❌ There are no lyrics for this music :(" : String.join("\n\n", lyricsMap.values()));
                Files.deleteIfExists(path);
            }catch(Throwable e){
                this.reply(message, e);
            }
        }).start();
    }

    public void reply(Message message, String content){
        try{
            Field messageIdField = Message.class.getDeclaredField("messageID");
            messageIdField.setAccessible(true);
            Integer messageId = (Integer) messageIdField.get(message);

            BotRequest request = new BotRequest(new TextMessage(message.isFromGroupChat() ? message.getGroupChat().getId() : message.getSender().getId(), content));
            request.getContent().addTextBody("reply_to_message_id", Integer.toString(messageId), this.CONTENT_TYPE);

            Sender.bot.post(request);
        }catch(Throwable e){
            e.printStackTrace();
        }
    }

    public void reply(Message message, Throwable e){
        this.reply(message, "⁉️ ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage().replaceAll("https?://\\S*", "[DATA EXPUNGED]"));
        e.printStackTrace();
    }
}
