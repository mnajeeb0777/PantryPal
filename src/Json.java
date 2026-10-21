import java.io.IOException;
import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared bounded JSON reader; numbers retain decimal precision. */
final class Json
{
    private Json() { }

    static Object parse(String text) throws IOException
    {
        if (text == null || text.length() > 2_000_000)
        {
            throw new IOException("JSON response is missing or too large.");
        }
        CharBuffer input = CharBuffer.wrap(text);
        Object value = jsonValue(input, 0);
        jsonWhitespace(input);
        if (input.hasRemaining()) { throw invalidJson(input); }
        return value;
    }

    private static Object jsonValue(CharBuffer input, int depth) throws IOException
    {
        jsonWhitespace(input);
        if (depth > 32 || !input.hasRemaining()) { throw invalidJson(input); }
        char next = input.charAt(0);
        if (next == '"') { return jsonString(input); }
        if (next == '{')
        {
            input.get();
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            if (jsonTake(input, '}')) { return object; }
            do
            {
                jsonWhitespace(input);
                String key = jsonString(input);
                if (!jsonTake(input, ':') || object.containsKey(key))
                {
                    throw invalidJson(input);
                }
                object.put(key, jsonValue(input, depth + 1));
                if (jsonTake(input, '}')) { return object; }
            }
            while (jsonTake(input, ','));
            throw invalidJson(input);
        }
        if (next == '[')
        {
            input.get();
            List<Object> values = new ArrayList<Object>();
            if (jsonTake(input, ']')) { return values; }
            do
            {
                values.add(jsonValue(input, depth + 1));
                if (jsonTake(input, ']')) { return values; }
            }
            while (jsonTake(input, ','));
            throw invalidJson(input);
        }
        if (next == 'n') { jsonLiteral(input, "null"); return null; }
        if (next == 't') { jsonLiteral(input, "true"); return Boolean.TRUE; }
        if (next == 'f') { jsonLiteral(input, "false"); return Boolean.FALSE; }
        StringBuilder digits = new StringBuilder();
        while (input.hasRemaining() && "0123456789-+.eE".indexOf(input.charAt(0)) >= 0)
        {
            digits.append(input.get());
        }
        String number = digits.toString();
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
        {
            throw invalidJson(input);
        }
        try { return new BigDecimal(number); }
        catch (NumberFormatException exception) { throw invalidJson(input); }
    }

    private static String jsonString(CharBuffer input) throws IOException
    {
        if (!input.hasRemaining() || input.get() != '"') { throw invalidJson(input); }
        StringBuilder result = new StringBuilder();
        while (input.hasRemaining())
        {
            char character = input.get();
            if (character == '"') { return result.toString(); }
            if (character < 32) { throw invalidJson(input); }
            if (character != '\\') { result.append(character); continue; }
            if (!input.hasRemaining()) { throw invalidJson(input); }
            char escape = input.get();
            switch (escape)
            {
                case '"': case '\\': case '/': result.append(escape); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (input.remaining() < 4) { throw invalidJson(input); }
                    char[] hex = new char[4];
                    input.get(hex);
                    String code = new String(hex);
                    if (!code.matches("[0-9a-fA-F]{4}")) { throw invalidJson(input); }
                    result.append((char)Integer.parseInt(code, 16));
                    break;
                default: throw invalidJson(input);
            }
        }
        throw invalidJson(input);
    }

    private static void jsonLiteral(CharBuffer input, String literal) throws IOException
    {
        for (int i = 0; i < literal.length(); i++)
        {
            if (!input.hasRemaining() || input.get() != literal.charAt(i))
            {
                throw invalidJson(input);
            }
        }
    }

    private static boolean jsonTake(CharBuffer input, char character)
    {
        jsonWhitespace(input);
        if (input.hasRemaining() && input.charAt(0) == character)
        {
            input.get();
            return true;
        }
        return false;
    }

    private static void jsonWhitespace(CharBuffer input)
    {
        while (input.hasRemaining() && " \t\r\n".indexOf(input.charAt(0)) >= 0)
        {
            input.get();
        }
    }

    private static IOException invalidJson(CharBuffer input)
    {
        return new IOException("Invalid JSON at character " + input.position() + ".");
    }
}
