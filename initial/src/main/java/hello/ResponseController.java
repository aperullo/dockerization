package hello;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;


@RestController
public class ResponseController {

    private static final String greeting = "Hello";
    private static final String template = "Put %s in %s";
    private final AtomicLong counter = new AtomicLong();
    private Random rand = new Random();
    private int id = rand.nextInt(9999);
    
    @Autowired
    private DataService dataService;
    
    @Value("${spring.application.name:notFound}")
    private String property;

    @RequestMapping("/hello")
    public Response Hello() {
        return new Response(counter.incrementAndGet(),
                            greeting);
    }

    @RequestMapping("/id")
    public Response getId() {
        return new Response(counter.incrementAndGet(),
                            "This container has ID " + Integer.toString(id));
    }

    @RequestMapping("/read")
    public Response read() {
        return new Response(counter.incrementAndGet(),
                            property);
    }


    @RequestMapping(value = "/put", method = POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity saveData(@RequestBody Data data) {
        dataService.saveData(data);
        return new ResponseEntity(CREATED);
    }

    @RequestMapping(value = "/get", method = GET, produces = APPLICATION_JSON_VALUE)
    public Data findDataByKey(@RequestParam(value="key") String key) {
        return dataService.findDataByKey(key);
    }
}
