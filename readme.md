# Dockerization tutorial

This document represents an introduction to docker, swarm, and dockerization of applications.

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

4. 

## Part 2 - Dockerizing Practical Demonstration

This tutorial assumes you've already installed docker to begin with. 

If you haven't used docker swarm before, then `docker node ls` should say that your node isn't part of any active swarms. If you have used it before, you can choose to either leave the swarm with `docker swarm leave` or simply use your current swarm if it happens to be a 1-node swarm.
