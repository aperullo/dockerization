# Dockerization tutorial

This document represents an introduction to docker, swarm, and dockerization of applications.

Table of Contents
=================

  * [Part 1 - Designing an app for Dockerization](#part-1---designing-an-app-for-dockerization)
  * [Part 2 - Dockerizing Practical Demonstration](#part-2---dockerizing-practical-demonstration)
     * [Step 0: Prerequisite, have an app to dockerize](#step-0-prerequisite-have-an-app-to-dockerize)
     * [Step 1: Making a docker image](#step-1-making-a-docker-image)
     * [Step 2: Making a Docker-stack](#step-2-making-a-docker-stack)
        * [Step 2a: Adding a config file](#step-2a-adding-a-config-file)
     * [Step 3: The Second Service and Environment Variables](#step-3-the-second-service-and-environment-variables)
     * [Step 4 (optional): Adding persistence](#step-4-optional-adding-persistence)
  * [Part 3 - Advanced Docker Steps and Best practices](#part-3---advanced-docker-steps-and-best-practices)
     * [Step 1. Gradle Constant Replacement](#step-1-gradle-constant-replacement)
     * [Step 2. Gradle Docker plugin](#step-2-gradle-docker-plugin)
     * [Step 3. Env Var substitution](#step-3-env-var-substitution)

## Part 1 - Designing an app for Dockerization

Designing an application for use in docker swarm means making choices about both configuration and debugging of the app that leverage the nature of docker.

See [view container logs](https://docs.docker.com/v17.09/engine/admin/logging/view_container_logs/) for info on seeing and setting up your app to expose logs to `docker logs` and `docker service logs`

The most important thing to keep in mind is that docker swarm is an orchestrator, meaning that your service will be running, possibly multiple copies of it, managed by the swarm. If the application fails and quits, the container will be destroyed and another copy of the app will take its place. 

1. Start quickly.
    1. The application should start quickly. 
    2. If one copy goes down, docker will start redirecting traffic to the other copies (if present) while it makes another. The additional pressure could cause others to fail too. Minimize start-up to prevent cascades.

2. No on-the-fly configuration and minimal config overall.
    1. As noted above, containers can be shortlived, so requiring manual configuration by a person is not an option.
    2. Applications should start with sane defaults that still allows the service to do something useful.
    3. If your application requires configuration at all, it should be by way of environment variables, config files, or command line args.
    4. Applications in containers should be *stateless* because a container isn't meant to be permanent, they can and do die off. Storing stateful data inside a container will cause issue. 
        1. If your application needs to store data, there are solutions. It can make use of an external database or use volumes to maintain state past the life-expectancy of a container.

3. Have a useful amount of logging available at the default level (like *info*). 
    1. Ideally the app can be debugged just by looking at the log file. Either too little or too much output in the logs will make it harder to diagnose issues by just using `docker service logs` or `docker logs`. 
    2. This is preferable because debugging in docker can be intense or clunky otherwise.
        1. Methods like ssh'ing into the container or using only `docker ps` to find errors are inefficient and imprecise. 


## Part 2 - Dockerizing Practical Demonstration

This tutorial assumes you've already installed docker to begin with. 

If you haven't used docker swarm before, then `docker node ls` should say that your node isn't part of any active swarms. If you have used it before, you can choose to either leave the swarm with `docker swarm leave` or simply use your current swarm if it happens to be a 1-node swarm. To enter a swarm use `docker swarm init`.

### Step 0: Prerequisite, have an app to dockerize

To follow along with the tutorial use the contents of the `/initial` folder as your working directory. 

First thing we need is an application to dockerize. The `/initial` folder includes a simple springboot app with several endpoints.
1. A `/hello` endpoint for testing connectivity
2. A `/read` endpoint that reads a value out of a config file, proving the config works
3. A `/put` endpoint that stores a key-pair in an external database showing it can connect to other containers
4. A `/get` endpoint that reads a value out of the external database based on a key.

First build the application by going to `/initial` and running `./gradlew clean build`.

*Optional*: If you want to test the application. You can run `java -jar build/libs/gs-rest-service-0.1.0.jar` and then hit the endpoints as `curl "http://localhost:8080/hello"`.

### Step 1: Making a docker image
Containers are like disposible virtual machines. Therefore you need an OS image to use that includes your application.

We will need a **Dockerfile** which is basically a set of instructions for how to construct that image. 
Inside of `/initial`, create a new file and name it `Dockerfile`.

We don't want to manually install all the dependencies for our application, so instead we will use a premade image from dockerhub as a base and put our application on top of it. We add 

```
FROM openjdk:8-jre-alpine
```

The next line will copy the jar we built using `./gradlew clean build` into the container from our current folder context. So assuming we will run the `docker build` command from `/initial`:
```
COPY build/libs/gs-rest-service-0.1.0.jar /app.jar
```

The next line is documentation for what ports others can interact with this container at.
```
EXPOSE 8080
```

The final line will tell docker what command to use when starting the image so that our application starts.
```
ENTRYPOINT ["java","-jar","/app.jar"]
```

By the end, `Dockerfile`:
```
FROM openjdk:8-jre-alpine
COPY build/libs/gs-rest-service-0.1.0.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

Now we can build the image from `/initial` using `docker build -t sample-app -f Dockerfile .`
the `-t` tells docker what to call the resulting image and `.` tells it what folder context to use for relative paths. Check if the image exists upon building:
```
> docker image ls
REPOSITORY                     TAG                 IMAGE ID            CREATED             SIZE
sample-app                     latest              24be1a1850e1        10 seconds ago      121MB
```

### Step 2: Making a Docker-stack
Once we have a docker image we can start preparing a stack, which will contain all of the docker-specific configuration. A docker stack is generally made up of multiple services, each one represents all the copies of a specific application with a specific configuration. We will have two services, but will start by creating only one.

Create a file called `docker-stack.yml` and add the following:
```
version: '3.7'
services:
    sample-service:
        image: sample-app:latest
        ports: 
          - published: 8080
            target: 8080
            mode: "host"
```
The ports section allows us to expose a port of the container to a port on the host so we can interact with it from outside the container.
- **published** : represents the port that connections can reach out to through the host.
- **target** : represents the port those connections are routed to inside the container.

We'll run it to make sure it works. The `-c` tells the command to use a file, and the final part of the command is the prefix for all services created in this stack.

```
> docker stack deploy -c docker-stack.yml dep
Creating service dep_sample-service

> docker service ls
ID                  NAME                 MODE                REPLICAS            IMAGE               PORTS
jf6mf00prlof        dep_sample-service   replicated          1/1                 sample-app:latest
```

It may take up a few seconds to go from 0/1 to 1/1. Next try the endpoint to verify it works. This is the only endpoint that will work at this time. After that we are going to remove the stack.
```
> curl http://localhost:8080/hello
{"id":1,"content":"Hello"}

> docker stack rm dep
dep_sample-service

> 
docker service ls
ID                  NAME                MODE                REPLICAS            IMAGE               PORTS
```

#### Step 2a: Adding a config file
The next thing we will do is add a config file to our application to demonstrate that we can pull properties from it.

In `/initial` create a file called `application.properties` and write:
```
spring.application.name=ourdemoapp
```

Then in `docker-stack.yml` we have to add two sections, first on the top level we add a block telling docker to load a config file from our context as a config with name `spring_config`. 
```
configs:
    spring_config:
        file: ./application.properties
```

Then we add a section under the `sample-service:` section telling docker to put that file into the container in the`/config` directory.
```
configs:
  - source: spring_config
    target: /config/application.properties
```

`docker-stack.yml` now looks like:
```
version: '3.7'
services:
    sample-service:
        image: sample-app:latest
        ports: 
          - published: 8080
            target: 8080
            mode: "host"
        configs:
          - source: spring_config
            target: /config/application.properties

configs:
    spring_config:
        file: ./application.properties
```

When we bring our stack up, the app will already be set up to look for the config and load it
```
> docker stack deploy -c docker-stack.yml dep
Creating config dep_spring_config
Creating service dep_sample-service

> curl http://localhost:8080/read
{"id":1,"content":"ourdemoapp"}

> docker stack rm dep
Removing service dep_sample-service
Removing config dep_spring_config
```

### Step 3: The Second Service and Environment Variables

In this stage we will introduce another service into the stack, a database using a premade docker image. 

In `docker-stack.yml` we add a new section under services:

```
sample-db:
    image: "redis:alpine"
```

This isn't enough though, our app still can't communicate with our database, it doesn't know redis exists. We will connect them with a network rather than exposing a port. This way they can talk to each other, but nobody else can talk to our database.

We add a network section to each service, and one to the top level. See the full docker-stack below for sections labelled "networks":
<details><summary>Click to expand</summary>
<p>

```
version: '3.7'
services:
    sample-service:
        image: sample-app:latest
        ports: 
          - published: 8080
            target: 8080
        configs:
          - source: spring_config
            target: /config/application.properties
        networks:
          - db-net

    sample-db:
        image: redis:alpine
        networks:
          - db-net
    
configs:
    spring_config:
        file: ./application.properties

networks:
  db-net:
```

</p>
</details>


When connected by a network, containers contact each other using their service name; in our case *sample-service* will need to know it can reach our database service by using `sample-db`, so that it sends requests to `http://sample-db:6379`. We will need to change a few things to achieve this. 

*NOTE: for the reason, service names should be dash-seperated, not underscore; underscores are not valid url chars.*

First change `application.properties` to be:
```
spring.application.name=ourdemoapp
spring.redis.host=${REDIS_HOST}
```
Then under the *sample-service* section in `docker-stack.yml` we add an environment section:
```
environment:
    REDIS_HOST: "sample-db"
```

You can see we include some environment variables as configuration. We added some env vars for sample-service so it can know where to access the database. The advanced steps later will demonstrate how to not hardcode the values. 

Our final `docker-stack.yml` looks like:
<details><summary>Click to expand</summary>
<p>

```
version: '3.7'
services:
    sample-service:
        image: sample-app:latest
        ports: 
          - published: 8080
            target: 8080
        configs:
          - source: spring_config
            target: /config/application.properties
        networks:
          - db-net
        environment:
          REDIS_HOST: "sample-db"

    sample-db:
        image: redis:alpine
        networks:
          - db-net
    
configs:
    spring_config:
        file: ./application.properties

networks:
  db-net:
```
</p>
</details>


We start the stack and try out the new functions:
```
> docker stack deploy -c docker-stack.yml dep
Creating network dep_db-net
Creating config dep_spring_config
Creating service dep_sample-service
Creating service dep_sample-db

> curl -X POST "http://localhost:8080/put" -H 'content-type: application/json' -d '{ "key": "345" }'

> curl -X GET "http://localhost:8080/get?key=345"
{"key":"345"}

> docker stack rm dep
Removing service dep_sample-db
Removing service dep_sample-service
Removing config dep_spring_config
Removing network dep_db-net
```

### Step 4 (optional): Adding persistence 
As stated previously, containers are generally short-lived. With our current docker-stack, if the redis container goes down for any reason, it will lose all of its data. We will be giving it a place to store that data that survives after the container is gone, using a named volume. 

Volumes are space docker allocates to containers to let them write files. They are usually anonymous, which means that only one container will get to use it, giving it the same lifespan as that container. By naming a volume and binding it to a service, docker can assign it to a new container if the old one dies.

We add a volumes section to both the top level and the *sample-db* sections of our stack:
```
sample-db:
    image: "redis:alpine"
    command: ["redis-server", "--appendonly", "yes"]
    networks:
      - db-net
    volumes:
      - redis-data:/data
```

```
volumes:
    redis-data:
```
We also needed to add some flags to the redis container to enable persistence using the `command:` section.

**docker-stack.yml**

<details><summary>Click to expand</summary>
<p>

```
version: '3.7'
services:
    sample-service:
        image: sample-app:latest
        ports: 
          - published: 8080
            target: 8080
        configs:
          - source: spring_config
            target: /config/application.properties
        networks:
          - db-net
        environment:
          REDIS_HOST: "sample-db"

    sample-db:
        image: redis:alpine
        command: ["redis-server", "--appendonly", "yes"]
        networks:
          - db-net
        volumes:
          - redis-data:/data
    
configs:
    spring_config:
        file: ./application.properties

networks:
    db-net:

volumes:
    redis-data:
```
</p>
</details>

Now we will:
1. Bring the stack up
2. Put some data in redis
3. Kill the container
4. Wait for the container to respawn
5. See that the data is still there

```
> docker stack deploy -c docker-stack.yml dep
Creating network dep_db-net
Creating config dep_spring_config
Creating service dep_sample-service
Creating service dep_sample-db

> docker service ls
docker service ls
ID                  NAME                 MODE                REPLICAS            IMAGE               PORTS
we8jonix8eq2        dep_sample-db        replicated          1/1                 redis:alpine
ahx9ummoi3od        dep_sample-service   replicated          1/1                 sample-app:latest   *:8080->8080/tcp

> curl -X POST "http://localhost:8080/put" -H 'content-type: application/json' -d '{ "key": "345" }'

> curl -X GET "http://localhost:8080/get?key=345"
{"key":"345"}

> docker kill dep_sample-db.1.j3fb9yhpg64gmqi93zw3z5j7c
dep_sample-db.1.j3fb9yhpg64gmqi93zw3z5j7c 
```
Your container name will be different, you can find it with `docker container ls` Once `docker service ls` shows 1/1 again, a new container is up.

```
> docker service ls
ID                  NAME                 MODE                REPLICAS            IMAGE               PORTS
we8jonix8eq2        dep_sample-db        replicated          0/1                 redis:alpine
ahx9ummoi3od        dep_sample-service   replicated          1/1                 sample-app:latest   *:8080->8080/tcp

> docker service ls
ID                  NAME                 MODE                REPLICAS            IMAGE               PORTS
we8jonix8eq2        dep_sample-db        replicated          1/1                 redis:alpine
ahx9ummoi3od        dep_sample-service   replicated          1/1                 sample-app:latest   *:8080->8080/tcp
```
Check the data is still there.

```
> curl -X GET "http://localhost:8080/get?key=345"
{"key":"345"}
```

WARNING: `docker stack rm dep` will delete the persistent volume. 
To delete the volume manually `docker volume rm dep_redis-data`.

## Part 3 - Advanced Docker Steps and Best practices

Everything up to this section is enough to get started with dev work. This section details some best practices for production, as well as time-savers. These are standard things that the platform team does when building services.

To enable some of these changes a new structure is needed for the project. For this section use the contents of the `/advanced` folder. 

Broadly the changes are:
1. An *app* folder which contains the raw java application
2. A *deployment* folder which contains the properties and stack file organized into subfolders, with a place for .env
3. A *buildSrc* folder for gradle constants
4. Seperate `build.gradle` files for each project

### Step 1. Gradle Constant Replacement

For some applications there may be repeated strings that can change at build time, rather than hard-coding these values, we can use Gradle constant replacement. Gradle constants are surrounded by `@` like `@imageName@`. For this example, the image names and versions in `/advanced/deployment/stacks/docker-stack.yml` have been swapped out for constants.

```
...
sample-service:
    image: @sampleAppImage@:@sampleAppVersion@
...
sample-db:
    image: @redisImage@:@redisVersion@
...
```

There are tasks set up which filter the files and replace constants, they can be seen in `/advanced/deployment/build.gradle`

```
def replacements = [
                    sampleAppImage: 'sample-app',
                    sampleAppVersion: 'latest',
                    redisImage: 'redis',
                    redisVersion: 'alpine'
            ]

task filter {
    dependsOn 'filterStacks'
    dependsOn 'filterConfigs'
    dependsOn 'filterEnvironments'
}

task filterStacks(type: Copy) {
    from 'stacks'
    include '**/*'
    into buildDir

    project.logger.info("$buildDir")
    
    filter(ReplaceTokens, tokens: replacements)
}

task filterConfigs(type: Copy) {
    from 'configs'
    include '**/*'
    into "$buildDir/configs"

    filter(ReplaceTokens, tokens: replacements)
}

task filterEnvironments(type: Copy) {
    from 'environments'
    include '**/*'
    into "$buildDir/environments"

    filter(ReplaceTokens, tokens: replacements)
}
```

The *replacements* dictionary shows the keys and the values to be swapped. Containing them in the `build.gradle` file is not ideal and they should be in their own constants file.

The constants we will replace these with are in `/buildSrc/src/main/groovy/ImageConstants.groovy`.
```
class ImageConstants {

    static final String sampleAppImage = "sample-app"
    static final String sampleAppVersion = "latest"
    static final String redisImage = "redis"
    static final String redisVersion = "alpine"
}
```
To enable these constants, edit `/advanced/deployment/build.gradle`, adding the following imports, and swapping out the *replacements* dictionary.

```
import static ImageConstants.sampleAppImage
import static ImageConstants.sampleAppVersion
import static ImageConstants.redisImage
import static ImageConstants.redisVersion
...

def replacements = [
                sampleAppImage: sampleAppImage,
                sampleAppVersion: sampleAppVersion,
                redisImage: redisImage,
                redisVersion: redisVersion
        ]
```
Full `build.gradle`:

<details><summary>Click to expand</summary>
<p>

```
import org.apache.tools.ant.filters.ReplaceTokens

import static ImageConstants.sampleAppImage
import static ImageConstants.sampleAppVersion
import static ImageConstants.redisImage
import static ImageConstants.redisVersion

def replacements = [
                    sampleAppImage: sampleAppImage,
                    sampleAppVersion: sampleAppVersion,
                    redisImage: redisImage,
                    redisVersion: redisVersion
            ]

task filter {
    dependsOn 'filterStacks'
    dependsOn 'filterConfigs'
    dependsOn 'filterEnvironments'
}

task filterStacks(type: Copy) {
    from 'stacks'
    include '**/*'
    into buildDir

    project.logger.info("$buildDir")
    
    filter(ReplaceTokens, tokens: replacements)
}

task filterConfigs(type: Copy) {
    from 'configs'
    include '**/*'
    into "$buildDir/configs"

    filter(ReplaceTokens, tokens: replacements)
}

task filterEnvironments(type: Copy) {
    from 'environments'
    include '**/*'
    into "$buildDir/environments"

    filter(ReplaceTokens, tokens: replacements)
}
```
</p>
</details>

You can verify it worked by building (`./gradlew build`) and navigating to `advanced/deployment/build/docker-stack.yml`; all instances of `@constant@` will have been replaced with the value from the imageConstants file. You can also run the stack and verify the application still works.

### Step 2. Gradle Docker plugin
Running both `./gradlew build` and `docker build` is inefficient, we can stream line it by having gradle build the jar file, then also run our dockerfile. 

To do this we need to add the docker plugin to our `build.gradle` and then point the plugin at our dockerfile.

Open `advanced/app/build.gradle` and add the following sections

Add the following sections, 
```
plugins {
    ...
    id 'com.palantir.docker' version '0.13.0'
}

apply plugin: 'com.palantir.docker'

docker {
    name "sample-app"
    dockerfile file('Dockerfile')
    copySpec.from("build/libs").into("libs")
}

build.dependsOn('docker')

```

We first specify the docker plugin to use and apply it. Our *docker* task says to run the dockerfile, copy some files to bring for the folder context, then name the result `sample-app`. 

`advanced/app/build.gradle` now looks like:


<details><summary>Click to expand</summary>
<p>

```
plugins {
    id 'java'
    id 'org.springframework.boot' version '2.1.4.RELEASE'
    id 'com.palantir.docker' version '0.13.0'
}

version '1.0-SNAPSHOT'

sourceCompatibility = 1.8

repositories {
    mavenCentral()
}

dependencies {
    compile("org.springframework.boot:spring-boot-starter-web")
    compile("org.springframework.boot:spring-boot-starter-data-redis")
    compile("org.springframework.boot:spring-boot-starter-actuator")
}


allprojects {
    gradle.projectsEvaluated {
        tasks.withType(JavaCompile) {
            options.compilerArgs << "-Xlint:unchecked" << "-Xlint:deprecation"
        }
    }
}

apply plugin: 'org.springframework.boot'
apply plugin: 'io.spring.dependency-management'
apply plugin: 'com.palantir.docker'

docker {
    name "sample-app"
    dockerfile file('Dockerfile')
    copySpec.from("build/libs").into("libs")
}

build.dependsOn('docker')
```
</p>
</details>

If you already have built `sample-app` you can delete it; verify gradle now build the image running `./gradlew build`, and checking for the image's presence.

```
> docker image ls
REPOSITORY                        TAG                 IMAGE ID            CREATED             SIZE
sample-app                        latest              4522dc74cefb        About an hour ago   131MB

> docker image rm sample-app 
Untagged: sample-app:latest

> ./gradlew clean build
...

> docker image ls
REPOSITORY                        TAG                 IMAGE ID            CREATED             SIZE
sample-app                        latest              eb1210707573        3 seconds ago   131MB
```

### Step 3. Env Var substitution

The last section covered build-time settings, if configuration needs to be made at deploy time, we can use environment variables. First we will add an evironment variable to our docker-stack.yml.

In `/advanced/deployment/stacks/docker-stack.yml`, under *sample-service* we will change a hardcoded value to an env var.
```
environment:
   REDIS_HOST: "sample-db"
```
To
```
environment:
      REDIS_HOST: "${REDIS_HOST:-sample-db}"
```
The syntax `${ENV:-default}` means "use the environment variable or this `default` if it doesn't exist".

Next in `/advanced/deployment/environments` create a file called `dev.env` and write the following:
```
export REDIS_HOST=sample-db
```

From `/advanced`, run `./gradlew clean build` to make the changes part of the `/build`. Then we can load the contents of the .env
```
> source deployment/build/environments/dev.env
```

We will deploy by running our `docker-stack.yml` through docker-compose before giving the output to docker-stack. This is because docker-stack does not handle environment variable substitution by itself.

```
> docker stack deploy -c <(docker-compose -f deployment/build/docker-stack.yml config) dep
Creating network dep_db-net
Creating service dep_sample-db
Creating service dep_sample-service
```

In our case, we are using the environment variable to tell the app where the redis service is, but there are many other possible uses.

You can run the stack and verify the application still works by using curl on the endpoints `/put` and `/get`, see Part 2 - Step 3.






