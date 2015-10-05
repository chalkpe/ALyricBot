package pe.chalk.telegram.alyricbot;

import de.vivistra.telegrambot.model.Audio;
import de.vivistra.telegrambot.model.message.AudioMessage;
import de.vivistra.telegrambot.model.message.Message;
import de.vivistra.telegrambot.model.message.MessageType;
import de.vivistra.telegrambot.receiver.IReceiverService;
import de.vivistra.telegrambot.receiver.Receiver;
import de.vivistra.telegrambot.settings.BotSettings;
import org.khinenw.poweralyric.LyricLib;

import java.io.File;

/**
 * @author ChalkPE <chalkpe@gmail.com>
 * @since 2015-10-05
 */
public class ALyricBot implements IReceiverService {
    public static void main(String[] args){
        BotSettings.setApiToken(args[0]);

        new ALyricBot();
    }

    public ALyricBot(){
        Receiver.subscribe(this);
    }

    public void received(Message message){
        if(message.getMessageType() != MessageType.AUDIO_MESSAGE){
            return;
        }

        try{
            Audio audio = ((AudioMessage) message).getMessage(); //XXX: No getters
            File file = new File("");

            LyricLib.parseLyric(LyricLib.getLyric(LyricLib.getHash(file)));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
