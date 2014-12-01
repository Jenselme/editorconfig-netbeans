package com.welovecoding.netbeans.plugin.editorconfig.io.reader;

import com.welovecoding.netbeans.plugin.editorconfig.io.model.FirstLineInfo;
import com.welovecoding.netbeans.plugin.editorconfig.io.model.SupportedCharset;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.any23.encoding.TikaEncodingDetector;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 * @see
 * <a href="http://www.w3.org/TR/REC-xml/#sec-guessing-no-ext-info">Detection
 * Without External Encoding Information</a>
 */
public class FileInfoReader {

  private static final String[] UNICODE_CHARSETS = new String[]{
    "UTF-16BE",
    "UTF-16LE",
    "UTF-8"
  };

  private static String detectLineEnding(String line) {
    String lineEnding = System.lineSeparator();

    if (line.endsWith("\r\n")) {
      lineEnding = "\r\n";
    } else if (line.endsWith("\n")) {
      lineEnding = "\n";
    } else if (line.endsWith("\r")) {
      lineEnding = "\r";
    }

    return lineEnding;
  }

  public static Charset guessCharset(FileObject fo) {
    Charset charset = StandardCharsets.UTF_8;

    try (InputStream is = fo.getInputStream()) {
      String charsetName = new TikaEncodingDetector().guessEncoding(is);
      boolean isUnicode = Arrays.asList(UNICODE_CHARSETS).contains(charsetName);

      if (!isUnicode) {
        charsetName = "ISO-8859-1";
      }

      charset = Charset.forName(charsetName);
    } catch (IllegalArgumentException | IOException ex) {
      Exceptions.printStackTrace(ex);
    }

    return charset;
  }

  public static FirstLineInfo parseFirstLineInfo(FileObject fo) {
    Charset charset = FileInfoReader.guessCharset(fo);
    SupportedCharset supportedCharset;
    String charsetName = charset.name();
    String firstLine = readFirstLineWithSeparator(fo, charset);
    String lineEnding = detectLineEnding(firstLine);
    boolean marked = false;

    if (charset.equals(StandardCharsets.UTF_8)
            && firstLine.startsWith(SupportedCharset.FILE_MARK)) {
      charsetName = "UTF-8-BOM";
      marked = true;
    } else if (charset.equals(StandardCharsets.UTF_16BE)
            && firstLine.startsWith(SupportedCharset.FILE_MARK)) {
      marked = true;
    } else if (charset.equals(StandardCharsets.UTF_16LE)
            && firstLine.startsWith(SupportedCharset.FILE_MARK)) {
      marked = true;
    }

    supportedCharset = new SupportedCharset(charsetName);

    return new FirstLineInfo(supportedCharset, lineEnding, marked);
  }

  /**
   * Reads the first line of a file with it's termination sequence. A
   * termination sequence can be a line feed ('\n'), a carriage return ('\r'),
   * or a carriage return followed immediately by a linefeed.
   *
   * @param fo
   * @param charset
   *
   * @return First line of a file.
   */
  private static String readFirstLineWithSeparator(FileObject fo, Charset charset) {
    StringBuilder sb = new StringBuilder();
    String firstLine;
    int c;

    try (
            InputStream is = fo.getInputStream();
            InputStreamReader isr = new InputStreamReader(is, charset);
            BufferedReader br = new BufferedReader(isr)) {
      // Read first line
      while ((c = br.read()) != -1) {
        if (c == '\r') {
          // Mac OS
          sb.append('\r');
          // Windows
          if (br.read() == '\n') {
            sb.append('\n');
          }
          break;
        } else if (c == '\n') {
          // Mac OS X
          sb.append('\n');
          break;
        } else {
          sb.append((char) c);
        }
      }

      firstLine = sb.toString();

    } catch (IOException ex) {
      firstLine = "";
    }

    return firstLine;
  }

  public static String trimTrailingWhitespace(Stream<String> lines, String lineEnding) {
    return lines.map((String content) -> {
      return content.replaceAll("\\s+$", "");
    }).collect(Collectors.joining(lineEnding));
  }
}