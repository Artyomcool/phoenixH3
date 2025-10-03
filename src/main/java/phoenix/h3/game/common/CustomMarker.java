package phoenix.h3.game.common;

import phoenix.h3.game.stdlib.StdString;

import java.util.Vector;

public class CustomMarker {

    private static final byte[] CUSTOM_MARKER = "\n<CUSTOM>\n".getBytes();

    public static Vector<Vector<String>> parseForCustomMarkerAndFixup(StdString text) {
        if (text.length == 0) {
            return null;
        }
        int index = Bytes.indexOf(text.data, text.length, CUSTOM_MARKER, CUSTOM_MARKER.length, 0);
        if (index == -1) {
            return null;
        }

        Vector<Vector<String>> result = new Vector<>();
        int start = index + CUSTOM_MARKER.length;
        String custom = new String(text.data, start, text.length - start).trim();
        int lineEnd = -1;
        boolean hasMore = true;
        while (hasMore) {
            int lineStart = lineEnd + 1;
            lineEnd = custom.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                hasMore = false;
                lineEnd = custom.length();
            }

            result.add(parseLine(custom, lineEnd));
        }

        // cut length
        text.trimInPlace(index);

        return result;
    }

    private static Vector<String> parseLine(String custom, int lineEnd) {
        int tokenEnd = -1;
        boolean hasMoreTokens = true;
        Vector<String> tokens = new Vector<>(8);
        while (hasMoreTokens) {
            int tokenStart = tokenEnd + 1;
            tokenEnd = custom.indexOf(':', tokenStart);
            if (tokenEnd == -1 || tokenEnd >= lineEnd) {
                hasMoreTokens = false;
                tokenEnd = lineEnd;
            }
            String token = custom.substring(tokenStart, tokenEnd).trim();
            tokens.add(token);
        }
        return tokens;
    }

}
