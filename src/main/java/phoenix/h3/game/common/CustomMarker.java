package phoenix.h3.game.common;

import phoenix.h3.game.stdlib.StdString;

import java.io.ByteArrayOutputStream;
import java.util.Vector;

public class CustomMarker {

    private static final byte[] CUSTOM_MARKER = "\n<CUSTOM>\n".getBytes();

    public static Value parseForCustomMarkerAndFixup(StdString text) {
        if (text.length == 0) {
            return null;
        }
        int index = Bytes.indexOf(text.data, text.length, CUSTOM_MARKER, CUSTOM_MARKER.length, 0);
        if (index == -1) {
            return null;
        }

        int start = index + CUSTOM_MARKER.length;

        Value value = new TokenStream(text.data, start, text.length).nextObjectValue(true);

        // cut length
        text.trimInPlace(index);
        return value;
    }

    public static class Value {
        final String[] names;
        final Object[] values;

        public Value(String[] names, Object[] values) {
            this.names = names;
            this.values = values;
        }

        public Value val(String name) {
            for (int i = 0, namesLength = names.length; i < namesLength; i++) {
                if (name.equals(names[i])) {
                    return (Value) values[i];
                }
            }
            return null;
        }

        public int integer(String name, int def) {
            for (int i = 0, namesLength = names.length; i < namesLength; i++) {
                if (name.equals(names[i])) {
                    return ((Number) values[i]).intValue();
                }
            }
            return def;
        }

        public String ascii(String name) {
            for (int i = 0, namesLength = names.length; i < namesLength; i++) {
                if (name.equals(names[i])) {
                    return new String((byte[]) values[i]);
                }
            }
            return null;
        }

        public byte[] bytes(String name) {
            for (int i = 0, namesLength = names.length; i < namesLength; i++) {
                if (name.equals(names[i])) {
                    return (byte[]) values[i];
                }
            }
            return null;
        }
    }

    private static class TokenStream {
        final byte[] data;
        final int start;
        final int end;
        int pos;

        public TokenStream(byte[] data, int start, int end) {
            this.data = data;
            this.start = start;
            this.end = end;
            this.pos = start;
        }

        public void skipSpaces() {
            while (pos < end && data[pos] <= ' ') {
                pos++;
            }
        }

        public String nextName() {
            skipSpaces();
            int start = pos;
            while (true) {
                if (!(pos < end)) break;
                int d = data[pos];
                if ((d >= 'A' && d <= 'Z') || (d >= 'a' && d <= 'z') || (d >= '0' && d <= '9') || d == '_') {
                    pos++;
                } else {
                    break;
                }
            }
            return new String(data, start, pos - start);
        }

        public int nextChar() {
            skipSpaces();
            return data[pos++] & 0xff;
        }

        public Object nextAnyValue() {
            int c = nextChar();
            skipSpaces();
            if (c == '=') {
                c = nextChar();
                skipSpaces();
            }

            switch (c) {
                case '{':
                    return nextObjectValue(false);
                case '"':
                    return nextStringValue();
                default:
                    pos--;
                    return nextNumberValue();
            }
        }

        private Value nextObjectValue(boolean tillEnd) {
            Vector<String> names = new Vector<>();
            Vector<Object> values = new Vector<>();
            while (true) {
                String name = nextName();
                if (name.length() == 0) {
                    if (tillEnd) {
                        if (end == pos) {
                            break;
                        }
                        throw new IllegalArgumentException("Expected end of data, got " + (char) data[pos()] + " at pos " + pos() + " for string " + str());
                    }
                    if (nextChar() != '}') {
                        throw new IllegalArgumentException("Expected '}', got " + (char) data[pos()] + " at pos " + pos() + " for string " + str());
                    }
                    break;
                }
                names.add(name);
                values.add(nextAnyValue());
            }

            return new Value(
                    Vectors.toArray(names, new String[names.size()]),
                    Vectors.toArray(values, new Object[values.size()])
            );
        }

        private int pos() {
            return pos - 1 - start;
        }

        private String str() {
            return new String(data, start, end - start);
        }

        private Object nextStringValue() {
            int start = pos;
            while (pos < end) {
                int d = data[pos++];
                if (d == '\\') {
                    return nextStringValueSlow(start);
                }
                if (d == '"') {
                    return Bytes.copy(data, start, pos - start - 1);
                }
            }
            throw new IllegalArgumentException("Expected string data, got end of file for string " + str());
        }

        private int nextSpecialChar() {
            if (pos == end) {
                throw new IllegalArgumentException("Expected special char, got end of file for string " + str());
            }
            int c = data[pos++] & 0xff;
            switch (c) {
                case '\\':
                    return '\\';
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 't':
                    return '\t';
                case '"':
                    return '"';
            }
            throw new IllegalArgumentException("Expected special char, got " + (char) c + " at pos " + pos() + " for string " + str());
        }

        private byte[] nextStringValueSlow(int start) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(pos - start + 16);
            out.write(data, start, (pos - start - 1));
            out.write(nextSpecialChar());

            while (pos < end) {
                int d = data[pos++];
                if (d == '"') {
                    return out.toByteArray();
                }
                if (d == '\\') {
                    d = nextSpecialChar();
                }
                out.write(d);
            }
            throw new IllegalArgumentException("Expected string data, got end of file for string " + str());
        }

        private Object nextNumberValue() {
            if (pos == end) {
                throw new IllegalArgumentException("Expected number, got end of file for string " + str());
            }
            int start = pos;
            while (pos < end) {
                int n = data[pos++];
                if (n <= ' ') {
                    return Double.parseDouble(new String(data, start, pos - start - 1));
                }
                if (n >= '0' && n <= '9' || n == '.' || n == '+' || n == '-') {
                    continue;
                }
                throw new IllegalArgumentException("Expected number, got " + (char) n + " at pos " + pos() + " for string " + str());
            }
            return Double.parseDouble(new String(data, start, pos - start));
        }

    }

}
