package org.khinenw.poweralyric;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.farng.mp3.MP3File;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class LyricLib {

	public static String getHash(File f) throws IOException, NoSuchAlgorithmException {
		MP3File mp3 = new MP3File();
		long start = mp3.getMp3StartByte(f);

		FileInputStream fis = new FileInputStream(f);
		byte[] data = new byte[163840];
		fis.getChannel().position(start);
		fis.read(data, 0, 163840);
		fis.close();

		if (IntStream.range(0, data.length).parallel().allMatch(i -> data[i] == 0))
			return "*INVALID*";

		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(data);
		byte[] md5enc = md5.digest();

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < md5enc.length; i++) {
			sb.append(Integer.toString((md5enc[i] & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();
	}

	public static InputStream getLyric(String hashCode) throws IOException {
		byte[] request = ("<?xml version='1.0' encoding='UTF-8'?> "
				+ "<SOAP-ENV:Envelope xmlns:SOAP-ENV='http://www.w3.org/2003/05/soap-envelope' xmlns:SOAP-ENC='http://www.w3.org/2003/05/soap-encoding' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:xsd='http://www.w3.org/2001/XMLSchema' xmlns:ns2='ALSongWebServer/Service1Soap' xmlns:ns1='ALSongWebServer' xmlns:ns3='ALSongWebServer/Service1Soap12'> "
				+ "<SOAP-ENV:Body> " + "<ns1:GetLyric5> " + "<ns1:stQuery> " + "<ns1:strChecksum>" + hashCode
				+ "</ns1:strChecksum> " + "<ns1:strVersion></ns1:strVersion> "
				+ "<ns1:strMACAddress></ns1:strMACAddress> " + "<ns1:strIPAddress></ns1:strIPAddress> "
				+ "</ns1:stQuery> " + "</ns1:GetLyric5> " + "</SOAP-ENV:Body> " + "</SOAP-ENV:Envelope>").getBytes();

		HttpURLConnection con = (HttpURLConnection) (new URL(
				"http://lyrics.alsong.co.kr/alsongwebservice/service1.asmx").openConnection());
		con.setRequestProperty("Accept-Charset", "utf-8");
		con.setRequestProperty("Content-Length", String.valueOf(request.length));
		con.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
		con.setRequestProperty("User-Agent", "gSOAP");
		con.setRequestProperty("SOAPAction", "AlsongWebServer/GetLyric5");
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		con.setDoInput(true);

		OutputStream os = con.getOutputStream();
		os.write(request);
		os.close();

		return con.getInputStream();
	}

	public static Map<Integer, String> parseLyric(InputStream is)
			throws SAXException, IOException, ParserConfigurationException {
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new InputSource(new InputStreamReader(is, "UTF-8")));

		document.getDocumentElement().normalize();
		Node strLyric = document.getElementsByTagName("strLyric").item(0);

		String[] lyrics = strLyric.getTextContent().split("<br>");

		TreeMap<Integer, String> returnVal = new TreeMap<>();
		for (String i : lyrics) {
			String[] split = i.split("\\]");
			if (split.length < 2)
				continue;

			String[] timeSplit = split[0].split("\\.")[0].split(":");

			if (timeSplit.length < 2)
				continue;

			String lyric = i.replace(split[0] + "]", "");
			int timeStamp = Integer.parseInt(timeSplit[0].replace("[", "")) * 60 + Integer.parseInt(timeSplit[1]);
			if (returnVal.containsKey(timeStamp)) {
				StringBuilder sb = new StringBuilder();
				sb.append(returnVal.get(timeStamp));
				sb.append("\n");
				sb.append(lyric);
				returnVal.put(timeStamp, sb.toString());
			} else {
				returnVal.put(timeStamp, lyric);
			}
		}

		return returnVal;
	}
}
