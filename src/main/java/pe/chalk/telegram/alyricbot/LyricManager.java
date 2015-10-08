package pe.chalk.telegram.alyricbot;

import de.vivistra.telegrambot.model.Audio;
import de.vivistra.telegrambot.model.message.AudioMessage;
import de.vivistra.telegrambot.model.message.Message;
import de.vivistra.telegrambot.settings.BotSettings;
import org.farng.mp3.TagException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.khinenw.poweralyric.LyricLib;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-08
 */
public class LyricManager {
    private Path cacheDirectory;

    private Path tempCacheDirectory;
    private Path telegramCacheDirectory;
    private Path urlCacheDirectory;
    private Path lyricCacheDirectory;

    public LyricManager(Path cacheDirectory) throws IOException {
        this.cacheDirectory = cacheDirectory;
        if(Files.notExists(this.cacheDirectory)){
            Files.createDirectories(this.cacheDirectory);
        }

        this.tempCacheDirectory = cacheDirectory.resolve("temp");
        if(Files.notExists(this.tempCacheDirectory)){
            Files.createDirectories(this.tempCacheDirectory);
        }

        this.telegramCacheDirectory = cacheDirectory.resolve("telegram");
        if(Files.notExists(this.telegramCacheDirectory)){
            Files.createDirectories(this.telegramCacheDirectory);
        }

        this.urlCacheDirectory = cacheDirectory.resolve("url");
        if(Files.notExists(this.urlCacheDirectory)){
            Files.createDirectories(this.urlCacheDirectory);
        }

        this.lyricCacheDirectory = cacheDirectory.resolve("lyric");
        if(Files.notExists(this.lyricCacheDirectory)){
            Files.createDirectories(this.lyricCacheDirectory);
        }
    }

    private String getHash(Path path) throws IOException, TagException, NoSuchAlgorithmException {
        return LyricLib.getHash(path.toFile());
    }

    public String getHash(AudioMessage message) throws Throwable {
        Field fileIdField = Audio.class.getDeclaredField("fileId");
        fileIdField.setAccessible(true);
        String fileId = fileIdField.get(message.getMessage()).toString();

        Path telegramCachePath = this.getCachePath(this.telegramCacheDirectory, fileId);
        if(this.isValidCache(telegramCachePath)){
            return new String(Files.readAllBytes(telegramCachePath), StandardCharsets.UTF_8);
        }

        try(InputStream response = new URL(BotSettings.getApiUrlWithToken() + "getFile?file_id=" + fileId).openStream()){
            String url = String.format("https://api.telegram.org/file/bot%s/%s", ALyricBot.TOKEN, new JSONObject(new JSONTokener(response)).getJSONObject("result").getString("file_path"));

            ALyricBot.reply(message, "⚫️⚫️⚪️  Downloading your music...");

            Path tempPath = this.getTempPath();
            try(InputStream stream = new URL(url).openStream()){
                Files.copy(stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String hash = this.getHash(tempPath);
            Files.write(telegramCachePath, hash.getBytes(StandardCharsets.UTF_8));

            Files.deleteIfExists(tempPath);
            return hash;
        }
    }

    public String getHash(Message message, String url) throws IOException, TagException, NoSuchAlgorithmException {
        Path urlCachePath = this.getCachePath(this.urlCacheDirectory, this.getMD5Hash(url.getBytes(StandardCharsets.UTF_8)));
        if(this.isValidCache(urlCachePath)){
            return new String(Files.readAllBytes(urlCachePath), StandardCharsets.UTF_8);
        }

        ALyricBot.reply(message, "⚫️⚫️⚪️  Downloading your music...");

        Path tempPath = this.getTempPath();
        try(InputStream stream = new URL(url).openStream()){
            Files.copy(stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
        }

        String hash = this.getHash(tempPath);
        Files.write(urlCachePath, hash.getBytes(StandardCharsets.UTF_8));

        Files.deleteIfExists(tempPath);
        return hash;
    }

    public String getLyrics(String hash, Message message) throws IOException, SAXException, ParserConfigurationException {
        Path lyricCachePath = this.getCachePath(this.lyricCacheDirectory, hash);
        if(this.isValidCache(lyricCachePath)){
            return new String(Files.readAllBytes(lyricCachePath), StandardCharsets.UTF_8);
        }

        ALyricBot.reply(message, "⚫️⚫️⚫️  Searching lyrics...");

        Map<Integer, String> lyricsMap = LyricLib.parseLyric(LyricLib.getLyric(hash));
        if(lyricsMap.isEmpty()){
            return null;
        }

        String lyrics = String.join("\n\n", lyricsMap.values());
        Files.write(lyricCachePath, lyrics.getBytes(StandardCharsets.UTF_8));

        return lyrics;
    }

    public String getMD5Hash(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        md5Digest.update(bytes);

        StringBuilder builder = new StringBuilder();
        for(byte md5 : md5Digest.digest()){
            builder.append(Integer.toString((md5 & 0xff) + 0x100, 16).substring(1));
        }

        return builder.toString();
    }

    public Path getTempPath() throws IOException{
        Path tempPath = Files.createTempFile(this.tempCacheDirectory, "TelegramALyricBotAudioCache", ".tmp");
        tempPath.toFile().deleteOnExit();

        return tempPath;
    }

    public Path getCachePath(Path cacheDirectory, String hash){
        return cacheDirectory.resolve(hash + ".cache");
    }

    public boolean isValidCache(Path cachePath) throws IOException {
        return Files.exists(cachePath) && Files.isRegularFile(cachePath) && TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - Files.getLastModifiedTime(cachePath).toMillis()) <= 15;
    }

    public String toString(){
        return this.cacheDirectory.toString();
    }
}
