package pe.chalk.telegram.alyricbot;

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

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot implements IReceiverService {
    private final String token;
    private CloseableHttpClient client;

    public static void main(String[] args){
        new ALyricBot(args[0]);
    }

    public ALyricBot(String token){
        this.token = token;
        this.client = HttpClients.createDefault();

        BotSettings.setApiToken(this.token);
        Receiver.subscribe(this);
    }

    public void received(Message message){
        if(message.getMessageType() != MessageType.AUDIO_MESSAGE){
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

                HttpPost post = new HttpPost(BotSettings.getApiUrlWithToken() + "getFile");
                post.setEntity(MultipartEntityBuilder.create().addTextBody("file_id", fileId, ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), MIME.UTF8_CHARSET)).build());

                try(CloseableHttpResponse response = this.client.execute(post)){
                    JSONObject fileJson = new JSONObject(new JSONTokener(response.getEntity().getContent()));
                    System.out.println(fileJson.toString());

                    String filePath = String.format("https://api.telegram.org/file/bot%s/%s", this.token, fileJson.getJSONObject("result").getString("file_path"));

                    Files.copy(new URL(filePath).openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(file.toString());
                }

                String lyrics = String.join("\n", LyricLib.parseLyric(LyricLib.getLyric(LyricLib.getHash(file))).values());
                Sender.send(new TextMessage(message.isFromGroupChat() ? message.getGroupChat().getId() : message.getSender().getId(), lyrics));
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
}
