import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import student.TestCase;

/** Shared parser preserves precision and rejects unsafe or malformed response shapes. */
public class JsonTest extends TestCase
{
    public void testDecimalAndEscapes() throws Exception
    {
        Map<?,?> result=(Map<?,?>)Json.parse("{\"price\":0.10,\"name\":\"Rice\\n\\u00e9\",\"ok\":true}");
        assertEquals(new BigDecimal("0.10"),result.get("price"));
        assertEquals("Rice\né",result.get("name"));assertEquals(Boolean.TRUE,result.get("ok"));
    }
    public void testMalformedLimits() throws Exception
    {
        for(String value:new String[]{"{\"a\":1,\"a\":2}","{\"x\":NaN}","[1,]","true false", "[".repeat(34)+"0"+"]".repeat(34)," ".repeat(2_000_001)})
        {try{Json.parse(value);fail();}catch(IOException expected){assertNotNull(expected.getMessage());}}
    }
}
