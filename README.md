# microstart
CLI utility to start processes.

If you are using a microservice architecture, this may come handy as it can various microservices
with just some simple lines. 

This improves developer experience because you'll no longer need to open multiple terminals 🚀.

It is similar to docker compose, but these are the main differences:

- This does not require a docker container to be built. Any command you run from command line can be run here
- It supports microservice chains (see below)

## Concepts

### Microservice chain

Suppose your application has the following dependency graph

Bla bla bla...