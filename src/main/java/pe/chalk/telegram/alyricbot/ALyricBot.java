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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MIME;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.khinenw.poweralyric.LyricLib;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot implements IReceiverService {
    private final String TOKEN;
    private final ContentType CONTENT_TYPE = ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), MIME.UTF8_CHARSET);

    private CloseableHttpClient client;

    public static void main(String[] args){
        new ALyricBot(args[0]);
    }

    public ALyricBot(String token){
        this.TOKEN = token;
        this.client = HttpClients.createDefault();

        BotSettings.setApiToken(this.TOKEN);
        Receiver.subscribe(this);
    }

    public void received(Message message){
        if(message.getMessageType() != MessageType.AUDIO_MESSAGE){
            if(message.getMessageType() == MessageType.TEXT_MESSAGE){
                ArrayList<String> commands = new ArrayList<>(Arrays.asList(message.getMessage().toString().split(" ")));
                if(commands.get(0).contains("@")){
                    commands.set(0, commands.get(0).split("@")[0]);
                }

                if(commands.get(0).equalsIgnoreCase("/lyric") && commands.size() > 1){
                    new Thread(() -> {
                        File file = null;
                        try{
                            commands.remove(0);
                            String url = String.join(" ", commands);

                            System.out.println(url);

                            if(!url.startsWith("http://") && !url.startsWith("https://")){
                                throw new IllegalArgumentException("You must use http or https protocol");
                            }

                            file = File.createTempFile("telegram-", "");
                            this.start(message, new URL(url), file);
                        }catch(Throwable e){
                            this.reply(message, "⁉️ ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }finally{
                            if(file != null && file.exists()){
                                //noinspection ResultOfMethodCallIgnored
                                file.delete();
                            }
                        }
                    }).start();
                }
            }

            return;
        }

        new Thread(() -> {
            File file = null;
            try{
                Audio audio = ((AudioMessage) message).getMessage();

                Field fileIdField = Audio.class.getDeclaredField("fileId");
                fileIdField.setAccessible(true);
                String fileId = fileIdField.get(audio).toString();

                file = File.createTempFile("telegram-", "-" + fileId);

                this.reply(message, "⚫️⚪️⚪️  Getting file_id...");

                HttpPost post = new HttpPost(BotSettings.getApiUrlWithToken() + "getFile");
                post.setEntity(MultipartEntityBuilder.create().addTextBody("file_id", fileId, this.CONTENT_TYPE).build());

                try(CloseableHttpResponse response = this.client.execute(post)){
                    JSONObject fileJson = new JSONObject(new JSONTokener(response.getEntity().getContent()));
                    System.out.println(fileJson.toString());

                    String filePath = String.format("https://api.telegram.org/file/bot%s/%s", this.TOKEN, fileJson.getJSONObject("result").getString("file_path"));
                    this.start(message, new URL(filePath), file);
                }
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                if(file != null && file.exists()){
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }).start();
    }

    public void start(Message message, URL url, File file) throws Exception {
        this.reply(message, "⚫️⚫️⚪️ Downloading audio...");

        Files.copy(url.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println(file.toString());

        this.reply(message, "⚫️⚫️⚫️ Searching lyrics...");

        Map<Integer, String> lyricsMap = LyricLib.parseLyric(LyricLib.getLyric(LyricLib.getHash(file)));
        System.out.println(lyricsMap);

        if(lyricsMap.isEmpty()){
            this.reply(message, "❌ There are no lyrics for this music :(");
        }

        String lyrics = String.join("\n\n", lyricsMap.values());

        this.reply(message, lyrics);
    }

    public void reply(Message message, String content){
        try{
            Field messageIdField = Message.class.getDeclaredField("messageID");
            messageIdField.setAccessible(true);
            Integer messageId = (Integer) messageIdField.get(message);

            BotRequest request = new BotRequest(new TextMessage(message.isFromGroupChat() ? message.getGroupChat().getId() : message.getSender().getId(), content));
            request.getContent().addTextBody("reply_to_message_id", Integer.toString(messageId), this.CONTENT_TYPE);

            Sender.bot.post(request);
        }catch(NoSuchFieldException | IllegalAccessException e){
            e.printStackTrace();
        }
    }
}
