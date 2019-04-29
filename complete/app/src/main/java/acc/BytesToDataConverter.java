package acc;

import acc.Data;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

@Component
@ReadingConverter
public class BytesToDataConverter implements Converter<byte[], Data> {

    private final Jackson2JsonRedisSerializer<Data> serializer;

    public BytesToDataConverter() {
        serializer = new Jackson2JsonRedisSerializer<>(Data.class);
        serializer.setObjectMapper(new ObjectMapper());
    }

    @Override
    public Data convert(byte[] value) {
        return serializer.deserialize(value);
    }
}
