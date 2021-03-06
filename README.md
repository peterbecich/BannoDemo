# Banno Demo

[![Build Status](https://travis-ci.org/peterbecich/BannoDemo.svg?branch=master)](https://travis-ci.org/peterbecich/BannoDemo)


[Twitter firehose data](https://developer.twitter.com/en/docs/tweets/sample-realtime/overview/GET_statuse_sample)


[FS2 0.10.0-M10 Scaladoc](https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M10/fs2-core_2.12-0.10.0-M10-javadoc.jar/!/fs2/index.html)

[FS2 0.10.0-M11 Scaladoc](https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M11/fs2-core_2.12-0.10.0-M11-javadoc.jar/!/fs2/index.html)


------------
# Quick Start

Docker Compose is required.  If building the Docker Image from source, be sure to also [clone the sub-module](https://stackoverflow.com/a/4438292/1007926) in this repository, as Docker will copy its contents into the Image when building.

1. Create a [Twitter App](https://apps.twitter.com/)
1. Create a file `BannoDemo/ops/.env`.  Docker Compose will read this file for its environmental variables
1. Copy this template and the corresponding credentials from your Twitter App into the file `.env`:

```
TWITTER_CONSUMER_TOKEN_KEY=
TWITTER_CONSUMER_TOKEN_SECRET=
TWITTER_ACCESS_TOKEN_KEY=
TWITTER_ACCESS_TOKEN_SECRET=
```
4. Proceed with either a pre-built Docker Image, or build the image from source

## Start from Pre-built Docker Image

1. Pull the Docker image from Docker Hub: 
   [peterbecich/bannodemo](https://hub.docker.com/r/peterbecich/bannodemo/) or [peterbecich/bannodemo-arm64](https://hub.docker.com/r/peterbecich/bannodemo-arm64/)
   
1. Proceed to start the Compose application


## (or) Build from Source

SBT is required.

1. Start SBT in `BannoDemo/`
1. Run `test` in SBT
1. Run `docker` in SBT to produce the Docker image `peterbecich/bannodemo:latest`
1. Exit SBT
1. Proceed to start the Compose application

## Start Docker Compose

1. Change directory to `BannoDemo/ops/`
1. Run `docker-compose up` to start the demonstration and log to the terminal, or `docker-compose up -d` to start the demonstration and detach
1. Visit [http://localhost/stats](http://localhost/stats)
1. If detached, run `docker-compose down` to stop the Compose application
