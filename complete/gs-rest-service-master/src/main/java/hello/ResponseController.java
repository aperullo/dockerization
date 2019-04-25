package hello;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResponseController {

    private static final String greeting = "Hello";
    private static final String template = "Put %s in %s";
    private final AtomicLong counter = new AtomicLong();

    @RequestMapping("/hello")
    public Response Hello() {
        return new Response(counter.incrementAndGet(),
                            greeting);
    }

    //TODO
    @RequestMapping("/read")
    public Response read() {
        return new Response(counter.incrementAndGet(),
                            "placeholder");
    }

    //TODO find a database img to use
    @RequestMapping("/put")
    public Response put(@RequestParam(value="key") String key, @RequestParam(value="val") String val) {
        //TODO put in db
        return new Response(counter.incrementAndGet(),
                            String.format(template, key, val));
    }

    //TODO find a database img to use
    @RequestMapping("/get")
    public Response get(@RequestParam(value="key") String key) {
        //TODO value
        String val = "placeholder";
        return new Response(counter.incrementAndGet(),
                            val);
    }
}
