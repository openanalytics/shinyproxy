.PHONY: build

SHELL := /bin/bash
CONTAINERNAME_BACKEND=shinyproxy
IMAGENAME_BACKEND=shinyproxy_image

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

build: # Build the docker image
	cp -r Dockerfile ./build # Copy Dockerfilee to ./build
	cp -r target/shinyproxy-1.1.0.jar ./build/shinyproxy-1.1.0.jar # Copy .jar file for execution
	docker build -t $(IMAGENAME_BACKEND) ./build # Build everything in ./build

up: build # Spin up the docker container
	docker-compose -p backend up -d backend || echo 'Already up!'

upwin:  # Bring the Docker container up for bash on ubuntu folk
	export WINDIR="$(subst /mnt/c,//c,$(CURDIR))/" && make up

down: # Stop the docker container, but don't remove it
	docker-compose -p backend stop backend

clean: # Stop the container and remove it
	docker-compose -p backend down

enter: # Enter the running container
	docker exec -it $(CONTAINERNAME_BACKEND) /bin/bash

list_containers: # List all your active containers
	docker container ls -a | grep 'tcp'