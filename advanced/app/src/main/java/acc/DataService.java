package acc;//import acc.BytesToDataConverter;
//import acc.DataToBytesConverter;
//import acc.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DataToBytesConverter dataToBytesConverter;

    @Autowired
    private BytesToDataConverter bytesToDataConverter;

    public void saveData(Data data) {

        HashOperations hashOperations = redisTemplate.opsForHash();
        hashOperations.put("data", data.getKey(), dataToBytesConverter.convert(data));
    }

    public Data findDataByKey(String key) {
        HashOperations hashOperations = redisTemplate.opsForHash();
        return bytesToDataConverter.convert((byte[]) hashOperations.get("data", key));
    }

}
