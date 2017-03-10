FROM registry.spotify.net/spotify/trusty-java:0.22
MAINTAINER dataex <dataex-squad@spotify.net>

COPY hype-run/target/*-capsule.jar /usr/share/hype-run/capsule.jar

ENTRYPOINT ["/bin/bash", "-c", "exec /usr/bin/java -jar /usr/share/hype-run/capsule.jar \"$@\"", "bash"]