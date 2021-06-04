package pe.chalk.telegram.alyricbot;

import be.zvz.alsong.Alsong;
import org.farng.mp3.MP3File;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-08
 */
public class LyricManager {
    private final Path cacheDirectory;

    private final Path tempCacheDirectory;
    private final Path telegramCacheDirectory;
    private final Path urlCacheDirectory;
    private final Path lyricCacheDirectory;

    public LyricManager(Path cacheDirectory) throws IOException {
        this.cacheDirectory = cacheDirectory;
        if (Files.notExists(this.cacheDirectory)) {
            Files.createDirectories(this.cacheDirectory);
        }

        this.tempCacheDirectory = cacheDirectory.resolve("temp");
        if (Files.notExists(this.tempCacheDirectory)) {
            Files.createDirectories(this.tempCacheDirectory);
        }

        this.telegramCacheDirectory = cacheDirectory.resolve("telegram");
        if (Files.notExists(this.telegramCacheDirectory)) {
            Files.createDirectories(this.telegramCacheDirectory);
        }

        this.urlCacheDirectory = cacheDirectory.resolve("url");
        if (Files.notExists(this.urlCacheDirectory)) {
            Files.createDirectories(this.urlCacheDirectory);
        }

        this.lyricCacheDirectory = cacheDirectory.resolve("lyric");
        if (Files.notExists(this.lyricCacheDirectory)) {
            Files.createDirectories(this.lyricCacheDirectory);
        }
    }

    private String getHash(File f) throws IOException, NoSuchAlgorithmException {
        MP3File mp3 = new MP3File();
        long start = mp3.getMp3StartByte(f);
        byte[] data = new byte[163840];
        try (FileInputStream fis = new FileInputStream(f)) {
            fis.getChannel().position(start);
            fis.read(data, 0, 163840);
        }

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(data);
        byte[] md5enc = md5.digest();

        StringBuilder sb = new StringBuilder();
        for (byte b : md5enc) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    private String getHash(Path path) throws IOException, NoSuchAlgorithmException {
        return getHash(path.toFile());
    }

    public String getHash(Message message) throws IOException, NoSuchAlgorithmException, TelegramApiException {
        final String fileId = message.getAudio().getFileId();

        final Path telegramCachePath = this.getCachePath(this.telegramCacheDirectory, fileId);
        if (this.isValidCache(telegramCachePath)) {
            return new String(Files.readAllBytes(telegramCachePath), StandardCharsets.UTF_8);
        }

        final GetFile request = new GetFile();
        request.setFileId(fileId);

        final String url = String.format("https://api.telegram.org/file/bot%s/%s",
                ALyricBot.getInstance().getBotToken(), ALyricBot.getInstance().sendMethod(request).getFilePath());
        ALyricBot.reply(message, "⚫️⚫️⚪️  Downloading your music...");

        final Path tempPath = this.getTempPath();
        try (final InputStream stream = new URL(url).openStream()) {
            Files.copy(stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
        }

        final String hash = this.getHash(tempPath);
        Files.write(telegramCachePath, hash.getBytes(StandardCharsets.UTF_8));

        Files.deleteIfExists(tempPath);
        return hash;
    }

    public String getHash(Message message, String url) throws IOException, NoSuchAlgorithmException {
        Path urlCachePath = this.getCachePath(this.urlCacheDirectory,
                this.getMD5Hash(url.getBytes(StandardCharsets.UTF_8)));
        if (this.isValidCache(urlCachePath)) {
            return new String(Files.readAllBytes(urlCachePath), StandardCharsets.UTF_8);
        }

        ALyricBot.reply(message, "⚫️⚫️⚪️  Downloading your music...");

        Path tempPath = this.getTempPath();
        try (InputStream stream = new URL(url).openStream()) {
            Files.copy(stream, tempPath, StandardCopyOption.REPLACE_EXISTING);
        }

        String hash = this.getHash(tempPath);
        Files.write(urlCachePath, hash.getBytes(StandardCharsets.UTF_8));

        Files.deleteIfExists(tempPath);
        return hash;
    }

    public String getLyrics(String hash, Message message)
            throws IOException {
        Path lyricCachePath = this.getCachePath(this.lyricCacheDirectory, hash);
        if (this.isValidCache(lyricCachePath)) {
            return new String(Files.readAllBytes(lyricCachePath), StandardCharsets.UTF_8);
        }

        ALyricBot.reply(message, "⚫️⚫️⚫️  Searching lyrics...");

        Alsong alsong = new Alsong();
        Map<Long, List<String>> lyricsMap = alsong.getLyricByHash(hash).getLyrics();
        if (lyricsMap.isEmpty()) {
            return null;
        }
        ArrayList<String> lyricsList = new ArrayList<>();
        for (List<String> lyricList : lyricsMap.values()) {
            lyricsList.add(String.join("\n", lyricList));
        }
        String lyrics = String.join("\n\n", lyricsList);
        Files.write(lyricCachePath, lyrics.getBytes(StandardCharsets.UTF_8));

        return lyrics;
    }

    public String getMD5Hash(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        md5Digest.update(bytes);

        StringBuilder builder = new StringBuilder();
        for (byte md5 : md5Digest.digest()) {
            builder.append(Integer.toString((md5 & 0xff) + 0x100, 16).substring(1));
        }

        return builder.toString();
    }

    public Path getTempPath() throws IOException {
        Path tempPath = Files.createTempFile(this.tempCacheDirectory, "TelegramALyricBotAudioCache", ".tmp");
        tempPath.toFile().deleteOnExit();

        return tempPath;
    }

    public Path getCachePath(Path cacheDirectory, String hash) {
        return cacheDirectory.resolve(hash + ".cache");
    }

    public boolean isValidCache(Path cachePath) throws IOException {
        return Files.exists(cachePath) && Files.isRegularFile(cachePath) && TimeUnit.MILLISECONDS
                .toDays(System.currentTimeMillis() - Files.getLastModifiedTime(cachePath).toMillis()) <= 15;
    }

    public String toString() {
        return this.cacheDirectory.toString();
    }
}
