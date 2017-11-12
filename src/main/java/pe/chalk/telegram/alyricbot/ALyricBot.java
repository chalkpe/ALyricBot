package pe.chalk.telegram.alyricbot;

import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.logging.BotLogger;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot extends TelegramLongPollingBot {
	private static ALyricBot instance;

	private static String TOKEN;
	private static LyricManager manager;

	private static Map<Message, Message> previous;

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Usage: java -jar ALyricBot.jar <token>");
			return;
		}

		TelegramBotsApi api = new TelegramBotsApi();
		api.registerBot(new ALyricBot(args[0]));
	}

	public ALyricBot(String token) throws IOException {
		ALyricBot.TOKEN = token;

		ALyricBot.instance = this;
		ALyricBot.manager = new LyricManager(Paths.get("cache"));
		ALyricBot.previous = new HashMap<>();

		BotLogger.setLevel(Level.ALL);
	}

	public static ALyricBot getInstance() {
		return ALyricBot.instance;
	}

	@Override
	public void onUpdateReceived(Update update) {
		if (!update.hasMessage())
			return;
		final Message message = update.getMessage();

		if (Objects.nonNull(message.getAudio()))
			this.search(message, () -> ALyricBot.manager.getHash(message));
		else if (message.hasText()) {
			final List<String> commands = new ArrayList<>(Arrays.asList(message.getText().split(" ")));

			String command = commands.remove(0);
			if (!command.startsWith("/"))
				return;

			if (command.contains("@")) {
				final String[] mainCommand = command.split("@");
				if (mainCommand.length > 1 && !mainCommand[1].equalsIgnoreCase(this.getBotUsername()))
					return;
				command = mainCommand[0];
			}

			if (!command.equalsIgnoreCase("/lyric"))
				return;
			if (commands.isEmpty()) {
				ALyricBot.reply(message,
						"\uD83C\uDF10 https://github.com/ChalkPE/ALyricBot\n\nUsage: /lyric <URL OF MUSIC FILE>");
				return;
			}

			if (!commands.get(0).startsWith("http://") && !commands.get(0).startsWith("https://"))
				commands.set(0, "http://" + commands.get(0));
			this.search(message, () -> ALyricBot.manager.getHash(message, String.join(" ", commands)));
		}
	}

	@Override
	public String getBotUsername() {
		return "ALyricBot";
	}

	@Override
	public String getBotToken() {
		return ALyricBot.TOKEN;
	}

	@FunctionalInterface
	public interface Factory<T> {
		T get() throws Throwable;
	}

	public void search(final Message message, final Factory<String> hashFactory) {
		new Thread(() -> {
			try {
				/* #1 - DOWNLOAD AUDIO FILE FROM STREAM */
				ALyricBot.reply(message, "⚫️⚪️⚪️  Getting an information...");
				String hash = hashFactory.get();
				if (hash.equals("*INVALID*"))
					throw new IllegalArgumentException("Invalid music file!");

				/* #2 - SEARCH FROM ALSONG SERVER */
				String lyrics = ALyricBot.manager.getLyrics(hash, message);

				/* #3 - REPLY RESULT AND DELETE FILE */
				ALyricBot.reply(message, Objects.isNull(lyrics) ? "❌ There are no lyrics for this music :(" : lyrics);
			} catch (Throwable e) {
				ALyricBot.reply(message, e);
			}
		}).start();
	}

	public static void reply(Message message, Throwable e) {
		String errorMessage = e.getMessage();
		if (Objects.isNull(errorMessage))
			errorMessage = "";

		ALyricBot.reply(message, "⁉️ ERROR: " + e.getClass().getSimpleName() + ": "
				+ errorMessage.replaceAll("https?://\\S*", "[DATA EXPUNGED]"));
		e.printStackTrace();
	}

	public static void reply(Message message, String content) {
		try {
			ALyricBot.reply(message, message.getChatId(), content);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public static void reply(Message message, long recipient, String content) {
		ALyricBot.reply(message, recipient, content, false);
	}

	public static void reply(Message message, long recipient, String content, boolean overflow) {
		if (Objects.isNull(content) || content.isEmpty())
			return;

		try {
			String nextContent = null;
			if (content.length() >= 4096) {
				nextContent = content.substring(4096);
				content = content.substring(0, 4096);
			}

			if (!overflow && ALyricBot.previous.containsKey(message)) {
				EditMessageText request = new EditMessageText();

				request.setChatId(String.valueOf(recipient));
				request.setMessageId(ALyricBot.previous.get(message).getMessageId());
				request.setText(content);
				request.disableWebPagePreview();

				ALyricBot.getInstance().editMessageText(request);
			} else {
				SendMessage request = new SendMessage();
				request.setChatId(String.valueOf(recipient));
				request.setText(content);

				request.setReplyToMessageId(message.getMessageId());
				request.disableWebPagePreview();

				ALyricBot.previous.put(message, ALyricBot.getInstance().sendMessage(request));
			}

			ALyricBot.reply(message, recipient, nextContent, true);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
}