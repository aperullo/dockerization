package hello;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class DataToBytesConverter implements Converter<Data, byte[]> {

    private final Jackson2JsonRedisSerializer<Data> serializer;

    public DataToBytesConverter() {
        serializer = new Jackson2JsonRedisSerializer<>(Data.class);
        serializer.setObjectMapper(new ObjectMapper());
    }

    @Override
    public byte[] convert(Data value) {
        return serializer.serialize(value);
    }
}
