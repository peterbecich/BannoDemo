# Banno Demo

[![Build Status](https://travis-ci.org/peterbecich/BannoDemo.svg?branch=master)](https://travis-ci.org/peterbecich/BannoDemo)

Demonstration with [Twitter firehose data](https://developer.twitter.com/en/docs/tweets/sample-realtime/overview/GET_statuse_sample)


[FS2 0.10.0-M10 Scaladoc](https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M10/fs2-core_2.12-0.10.0-M10-javadoc.jar/!/fs2/index.html)

[FS2 0.10.0-M11 Scaladoc](https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.10.0-M11/fs2-core_2.12-0.10.0-M11-javadoc.jar/!/fs2/index.html)


------------
# Quick start

1. Create a [Twitter App](https://apps.twitter.com/).
1. Create a file `BannoDemo/ops/.env`.  Docker Compose will read this file for its environmental variables.
1. Copy this template and the corresponding credentials from your Twitter App into the file `.env`:

	TWITTER_CONSUMER_TOKEN_KEY=
	TWITTER_CONSUMER_TOKEN_SECRET=
	TWITTER_ACCESS_TOKEN_KEY=
	TWITTER_ACCESS_TOKEN_SECRET=
	
1. Start SBT in `BannoDemo/`.
1. Run `test` in SBT.
1. Run `docker` in SBT to produce the Docker image `peterbecich/bannodemo:latest`.
1. Exit SBT and change directory to `BannoDemo/ops/`
1. Run `docker-compose up` to start the demonstration and log to the terminal, or `docker-compose up -d` to start the demonstration and free the terminal.
1. Visit [http://localhost/stats](http://localhost/stats).
